import lombok.SneakyThrows;

import java.io.FileInputStream;
import java.io.IOException;

public class SneakThrowsDoesntCatchExceptionFromThisConstructor {

  private FileInputStream myStream;

  <warning descr="Calls to sibling / super constructors are always excluded from @SneakyThrows; @SneakyThrows has been ignored because there is no other code in this constructor.">@SneakyThrows</warning>
  public SneakThrowsDoesntCatchExceptionFromThisConstructor() {
    <error descr="Unhandled exception: java.io.IOException">this</error>("somePath");
  }

  public SneakThrowsDoesntCatchExceptionFromThisConstructor(String path) throws IOException {
    myStream = new FileInputStream(path);
  }

  <warning descr="Calls to sibling / super constructors are always excluded from @SneakyThrows; @SneakyThrows has been ignored because there is no other code in this constructor.">@SneakyThrows</warning>
  public SneakThrowsDoesntCatchExceptionFromThisConstructor(int i, float f) {
    this(<error descr="Unhandled exception: java.io.IOException">throwException</error>());
  }

  public SneakThrowsDoesntCatchExceptionFromThisConstructor(int i) {

  }

  @SneakyThrows
  public SneakThrowsDoesntCatchExceptionFromThisConstructor(int i, float f, String s) {
    this(<error descr="Unhandled exception: java.io.IOException">throwException</error>());
    if (1 == 1) {
      throw new Exception("12331323");
    }
  }

  private static int throwException() throws IOException {
    throw new IOException("Boom");
  }
}
