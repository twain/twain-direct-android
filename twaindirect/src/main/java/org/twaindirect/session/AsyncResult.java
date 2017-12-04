package org.twaindirect.session;

/***
 * Listener for the result of asynchronous operation, which will return an object on success.
 * @param <T> Type of object expected.
 */

public interface AsyncResult<T> {
    void onResult(T result);
    void onError(Exception e);
}
