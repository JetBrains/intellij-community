package com.intellij.tasks.impl;

import com.intellij.tasks.TaskBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class RequestFailedException extends RuntimeException {
  @NotNull
  public static RequestFailedException forStatusCode(int code) {
    return new RequestFailedException(TaskBundle.messageForStatusCode(code));
  }

  @NotNull
  public static RequestFailedException forStatusCode(int code, @NotNull String message) {
    return new RequestFailedException(TaskBundle.message("failure.http.error", code, message));
  }

  @NotNull
  public static RequestFailedException forServerMessage(@NotNull String message) {
    return new RequestFailedException(TaskBundle.message("failure.server.message", message));
  }

  public RequestFailedException(String message) {
    super(message);
  }

  public RequestFailedException(String message, Throwable cause) {
    super(message, cause);
  }

  public RequestFailedException(Throwable cause) {
    super(cause);
  }
}
