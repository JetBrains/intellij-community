/*
 * @author: Eugene Zhuravlev
 * Date: Jul 10, 2003
 * Time: 4:51:25 PM
 */
package com.intellij.compiler.make;

public class CacheCorruptedException extends Exception{
  private static final String DEFAULT_MESSAGE = "Compiler dependency information on disk is corrupted. Rebuild required.";
  public CacheCorruptedException(String message) {
    super((message == null || message.length() == 0)? DEFAULT_MESSAGE : message);
  }

  public CacheCorruptedException(Throwable cause) {
    super(DEFAULT_MESSAGE, cause);
  }

  public CacheCorruptedException(String message, Throwable cause) {
    super((message == null || message.length() == 0)? DEFAULT_MESSAGE : message, cause);
  }

  public String getMessage() {
    return super.getMessage();
  }
}
