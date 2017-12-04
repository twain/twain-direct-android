package org.twaindirect.session;

/***
 * Response from an asynchronous operation indicating either success or failure.
 * For a result with an associated value, see @AsyncResult
 */
public interface AsyncResponse {
    void onSuccess();
    void onError(Exception e);
}
