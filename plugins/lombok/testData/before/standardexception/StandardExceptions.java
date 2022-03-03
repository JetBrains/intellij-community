import lombok.AccessLevel;
import lombok.experimental.StandardException;

@StandardException
class EmptyException extends Exception {
}

@StandardException(access = AccessLevel.PROTECTED)
class NoArgsException extends Exception {
  public NoArgsException() {
  }
}
