package org.twaindirect.session;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Information about an image block we've received, that represents part of an image.
 * Once we have all the parts, we can assemble and deliver the image.
 */
class ImageBlockInfo {
    // See the TWAIN Direct Metadata spec for a definition of moreParts
    enum MoreParts {
        lastPartInFile,
        lastPartInFileMorePartsPending,
        morePartsPending
    }

    public int sheetNumber;
    public int imageNumber;
    public int imagePart;
    public int blockNum;
    public MoreParts moreParts;

    public JSONObject metadata;

    public String partFileName() {
        return String.format(Locale.US, "%d-%d-%d-%d.part", sheetNumber, imageNumber, imagePart, blockNum);
    }

    public String eventualFileName() {
        return String.format(Locale.US, "%d-%d-%d.pdf", sheetNumber, imageNumber, imagePart);
    }
}
