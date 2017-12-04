package org.twaindirect.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataSource;

/**
 * Provide an InputStream from a DataSource
 */
class InputStreamDataSource implements DataSource {

    private InputStream inputStream;
    private String contentType;
    private String name;

    public InputStreamDataSource(InputStream inputStream, String contentType) {
        this.inputStream = inputStream;
        this.contentType = contentType;
    }

    public InputStreamDataSource(InputStream is, String contentType, String name) {
        this.inputStream = inputStream;
        this.contentType = contentType;
        this.name = name;
    }

    public String getContentType() {
        return contentType;
    }

    public InputStream getInputStream() throws IOException {
        return inputStream;
    }

    public String getName() {
        return name;
    }

    public OutputStream getOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

}