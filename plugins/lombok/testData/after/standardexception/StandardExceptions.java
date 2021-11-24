class EmptyException extends Exception {
  @java.lang.SuppressWarnings("all")
  public EmptyException() {
    this(null, null);
  }

  @java.lang.SuppressWarnings("all")
  public EmptyException(final java.lang.String message) {
    this(message, null);
  }

  @java.lang.SuppressWarnings("all")
  public EmptyException(final java.lang.Throwable cause) {
    this(cause != null ? cause.getMessage() : null, cause);
  }

  @java.lang.SuppressWarnings("all")
  public EmptyException(final java.lang.String message, final java.lang.Throwable cause) {
    super(message);
    if (cause != null) super.initCause(cause);
  }
}

class NoArgsException extends Exception {
  public NoArgsException() {
  }

  @java.lang.SuppressWarnings("all")
  protected NoArgsException(final java.lang.String message) {
    this(message, null);
  }

  @java.lang.SuppressWarnings("all")
  protected NoArgsException(final java.lang.Throwable cause) {
    this(cause != null ? cause.getMessage() : null, cause);
  }

  @java.lang.SuppressWarnings("all")
  protected NoArgsException(final java.lang.String message, final java.lang.Throwable cause) {
    super(message);
    if (cause != null) super.initCause(cause);
  }
}
