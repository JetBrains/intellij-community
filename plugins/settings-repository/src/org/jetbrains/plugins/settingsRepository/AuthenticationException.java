package org.jetbrains.plugins.settingsRepository;

import org.jetbrains.annotations.NotNull;

public final class AuthenticationException extends Exception {
  public AuthenticationException(@NotNull String message, @NotNull Throwable cause) {
    super(message, cause);
  }
}