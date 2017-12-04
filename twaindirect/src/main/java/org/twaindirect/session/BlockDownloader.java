package org.twaindirect.session;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

/**
 * This class is responsible for downloading blocks and delivering them to the client application.
 *
 * REST responses from the scanner include a session, which indicates which blocks are ready to
 * be retrieved. Session calls BlockDownloader's enqueueBlocks method with these block numbers
 * and BlockDownloader does the rest:
 *
 *  - Downloading the blocks.
 *  - Joining blocks together to form an image, if required.
 *  - Calling the SessionListener to notify the client that files are ready.
 *
 * BlockDownloader is associated with a particular session. The session must remain valid
 * until the downloads are complete.
 */

public class BlockDownloader {
    private static final Logger logger = Logger.getLogger(BlockDownloader.class.getName());

    // Reference to the owning session
    private Session session;

    // The client application's listener
    private final SessionListener sessionListener;

    /**
     * Status of all the blocks we're aware of
     */
    private Map<Integer, BlockState> blockState = new HashMap<Integer, BlockState>();

    // Block numbers <= this value have been downloaded, assembled, and delivered
    // to the application.
    int highestBlockCompleted = 1;

    /**
     * Number of simultaneous downloads allowed
     */
    private int windowSize = 3;

    /**
     * Temporary path for downloaded images before being delivered.
     */
    private File tempDir;

    /**
     * Number of downloads currently in progress
     */
    private int activeDownloadCount = 0;

    /**
     * Blocks that we've downloaded but not yet delivered
     */
    Map<Integer, ImageBlockInfo> downloadedBlocks = new HashMap<>();

    /**
     * Each block is in one of these states.
     */
    enum BlockState {
        // Ready to download
        readyToDownload,
        // Currently downloading
        downloading,
        // Downloaded, but waitingForMoreParts for more parts
        waitingForMoreParts,
        // Delivered to the client, and deleted
        completed
    }

    public BlockDownloader(Session session, File tempDir, SessionListener sessionListener) {
        this.session = session;
        this.tempDir = tempDir;
        this.sessionListener = sessionListener;
    }

    /**
     * The scanner has indicated it has these blocks available - add them to the blockStatus
     * map if we're not already tracking them.
     * @param blockNumbers
     * @return
     */
    public void enqueueBlocks(List<Integer> blockNumbers) {
        synchronized(this) {
            for (int blockNum : blockNumbers) {
                if (!blockState.containsKey(blockNum)) {
                    blockState.put(blockNum, BlockState.readyToDownload);
                }
            }

            // Try to queue up some downloads
            for (int i=0; i<windowSize; i++) {
                startDownloadThread();
            }
        }
    }

    /**
     * Kick off a download thread, if there aren't already >windowSize in progress.
     */
    private void startDownloadThread() {
        synchronized(this) {
            if (activeDownloadCount >= windowSize) {
                // Can't start another download right now
                return;
            }

            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    downloadThreadEntry();
                }
            });

            thread.start();
        }
    }

    /**
     * Start download the next block, which means fire up the download thread if it's
     * not already running.  If it is, then the block will get downloaded in turn.
     */
    private void downloadThreadEntry() {
        int blockReady = -1;

        synchronized(this) {
            if (session.getStopping()) {
                return;
            }

            if (activeDownloadCount >= windowSize) {
                return;
            }

            // Find the lowest block number that's not already downloading
            Object[] blocks = blockState.keySet().toArray();
            if (blocks.length == 0) {
                // Nothing to download
                return;
            }
            Arrays.sort(blocks);

            // Go through the blocks in sorted order looking for one that's readyToDownload
            for (int bidx=0; bidx<blocks.length; bidx++) {
                int blockNum = (int)blocks[bidx];
                if (blockState.get(blockNum) == BlockState.readyToDownload) {
                    blockReady = blockNum;
                    break;
                }
            }

            if (blockReady == -1) {
                // No blocks are ready
                return;
            }

            blockState.put(blockReady, BlockState.downloading);

            activeDownloadCount = activeDownloadCount + 1;
        }

        logger.info(String.format("Starting download of block %s", blockReady));

        final int blockNum = blockReady;

        try {
            // Synchronously download this block - we're on a background thread
            JSONObject params = new JSONObject();
            params.put("sessionId", session.getSessionId());
            params.put("imageBlockNum", blockReady);
            params.put("withMetadata", "true");

            HttpBlockRequest request = session.createBlockRequest(params);

            request.listener = new AsyncResult<InputStream>() {
                @Override
                public void onResult(InputStream inputStream) {
                    try {
                        synchronized(this) {
                            activeDownloadCount = activeDownloadCount - 1;
                        }

                        InputStreamDataSource dataSource = new InputStreamDataSource(inputStream, "multipart/mixed");
                        MimeMultipart multipart = new MimeMultipart(dataSource);

                        int count = multipart.getCount();

                        JSONObject metadata = null;
                        MimeBodyPart contentPart = null;

                        for (int part = 0; part < count; part++) {
                            BodyPart bodyPart = multipart.getBodyPart(part);

                            if (bodyPart.getContentType().startsWith("application/json")) {
                                Object partObj = bodyPart.getContent();
                                if (partObj instanceof InputStream) {
                                    JSONObject response = StreamUtils.inputStreamToJSONObject((InputStream)partObj);
                                    JSONObject results = response.getJSONObject("results");
                                    metadata = results.getJSONObject("metadata");
                                }
                            }

                            if (bodyPart.getContentType().startsWith("application/pdf")) {
                                contentPart = (MimeBodyPart)bodyPart;
                            }
                        }

                        if (contentPart == null) {
                            logger.severe("requestImageBlock did not deliver an application/pdf part");
                            return;
                        }

                        // Save the content part, using an intermediate filename geneated from the metadata
                        JSONObject address = metadata.getJSONObject("address");

                        ImageBlockInfo imageBlockInfo = new ImageBlockInfo();
                        imageBlockInfo.metadata = metadata;
                        imageBlockInfo.blockNum = blockNum;
                        imageBlockInfo.sheetNumber = address.getInt("sheetNumber");
                        imageBlockInfo.imageNumber = address.getInt("imageNumber");
                        imageBlockInfo.imagePart = address.getInt("imagePart");
                        imageBlockInfo.moreParts = ImageBlockInfo.MoreParts.valueOf(address.getString("moreParts"));

                        // Save the content part to disk
                        String partName = imageBlockInfo.partFileName();
                        File tempFile = new File(tempDir, partName);

                        // The BodyPart.saveFile method includes the crlf at the end of the part (which
                        // extends past Content-Length).  To work around this, truncate the file
                        // to the value of the Content-Length header.
                        String[] contentLengthHeader = contentPart.getHeader("Content-Length");
                        int bodyLength = Integer.parseInt(contentLengthHeader[0]);
                        contentPart.saveFile(tempFile);
                        FileChannel chan = new FileOutputStream(tempFile, true).getChannel();
                        chan.truncate(bodyLength);
                        chan.close();

                        // Add the ImageBlockInfo to our map of lists of parts.
                        String imageName = null;
                        synchronized(this) {
                            blockState.put(blockNum, BlockState.waitingForMoreParts);
                            imageName = imageBlockInfo.eventualFileName();
                            downloadedBlocks.put(blockNum, imageBlockInfo);
                        }

                        logger.fine(String.format("Finished downloading block %d", blockNum));

                        deliverCompletedParts();

                        // On to the next part
                        startDownloadThread();

                        session.releaseBlock(blockNum, blockNum);
                    } catch (MessagingException e) {
                        // The MIME body was unusable
                        logger.severe(e.toString());
                    } catch (IOException e) {
                        logger.severe(e.toString());
                    } catch (JSONException e) {
                        logger.severe(e.toString());
                    }
                }

                @Override
                public void onError(Exception e) {
                    // We failed getting this piece
                    activeDownloadCount = activeDownloadCount - 1;
                    sessionListener.onConnectionError(session, e);
                }
            };

            request.run();
        } catch (Exception e) {
            logger.severe(e.toString());
        }
    }

    /**
     * If we have all the parts for the next image to deliver, deliver it to the application.
     */
    private void deliverCompletedParts() {
        int partsToAssemble = 0;

        synchronized(this) {
            // See if we have an unbroken sequence of blocks ending in a block that has
            // moreParts = lastPart
            int nextBlock = 0;

            for (int blockNum = highestBlockCompleted;; blockNum++) {
                ImageBlockInfo ibi = downloadedBlocks.get(blockNum);
                if (ibi == null) {
                    // No block with this index
                    return;
                }

                partsToAssemble++;

                if (ibi.moreParts != ImageBlockInfo.MoreParts.morePartsPending) {
                    // This is a last part
                    nextBlock = blockNum + 1;
                    break;
                }
            }

            // Assemble parts from highestBlockDownloaded to nextBlock
            ImageBlockInfo firstBlockInfo = downloadedBlocks.get(highestBlockCompleted);
            File outFile = new File(tempDir, firstBlockInfo.eventualFileName());

            logger.log(Level.FINE, String.format("Assembling parts from %s to %s into %s", highestBlockCompleted, nextBlock, outFile.getAbsolutePath()));

            File firstBlockFile = new File(tempDir, firstBlockInfo.partFileName());
            if (partsToAssemble > 1) {
                // Append all subsequent blocks to the first one
                try {
                    OutputStream out = new FileOutputStream(firstBlockFile, true);

                    byte[] buf = new byte[65536];

                    for (int idx = highestBlockCompleted+ 1; idx < nextBlock; idx++) {
                        ImageBlockInfo block = downloadedBlocks.get(idx);
                        File inFile = new File(tempDir, block.partFileName());
                        InputStream in = new FileInputStream(inFile);
                        int b = 0;
                        while ((b = in.read(buf)) >= 0) {
                            out.write(buf, 0, b);
                        }

                        in.close();
                        inFile.delete();
                    }
                    out.close();
                } catch (FileNotFoundException e) {
                    logger.severe(e.toString());
                } catch (IOException e) {
                    logger.severe(e.toString());
                }
            }

            JSONObject metadata = firstBlockInfo.metadata;

            if (sessionListener != null) {
                File finalFile = new File(tempDir, firstBlockInfo.eventualFileName());
                firstBlockFile.renameTo(finalFile);
                sessionListener.onImageReceived(session, finalFile, metadata);
                finalFile.delete();
            }

            highestBlockCompleted = nextBlock;
        }
    }
}
