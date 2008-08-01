package org.jetbrains.idea.maven.indices;

public class MavenIndexException extends Exception {
  public MavenIndexException(Throwable cause) {
    super(cause);
  }

  public MavenIndexException(String message, Throwable cause) {
    super(message, cause);
  }
}
