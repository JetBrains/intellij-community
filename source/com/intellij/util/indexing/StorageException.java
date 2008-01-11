package com.intellij.util.indexing;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */
public class StorageException extends Exception{
  public StorageException(final String message) {
    super(message);
  }

  public StorageException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public StorageException(final Throwable cause) {
    super(cause);
  }
}
