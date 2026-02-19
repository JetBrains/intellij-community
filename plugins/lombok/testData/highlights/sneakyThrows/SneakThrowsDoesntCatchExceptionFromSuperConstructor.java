import lombok.SneakyThrows;

import java.io.FileInputStream;
import java.io.IOException;

public class SneakThrowsDoesntCatchExceptionFromSuperConstructor extends SomeSuperClass {

  <warning descr="Calls to sibling / super constructors are always excluded from @SneakyThrows; @SneakyThrows has been ignored because there is no other code in this constructor.">@SneakyThrows</warning>
  public SneakThrowsDoesntCatchExceptionFromSuperConstructor() {
    <error descr="Unhandled exception: java.io.IOException">super</error>("somePath");
  }

  <warning descr="Calls to sibling / super constructors are always excluded from @SneakyThrows; @SneakyThrows has been ignored because there is no other code in this constructor.">@SneakyThrows</warning>
  public SneakThrowsDoesntCatchExceptionFromSuperConstructor(int i, float f) {
    super(<error descr="Unhandled exception: java.io.IOException">throwException</error>());
  }

  @SneakyThrows
  public SneakThrowsDoesntCatchExceptionFromSuperConstructor(int i, float f, String s)  {
    super(<error descr="Unhandled exception: java.io.IOException">throwException</error>());
    if (1 == 1) {
      throw new Exception("12331323");
    }
  }
  private static int throwException() throws IOException {
    throw new IOException("Boom");
  }
}

class SomeSuperClass {
  private FileInputStream myStream;

  public SomeSuperClass(int i) {

  }

  public SomeSuperClass(String path) throws IOException {
    myStream = new FileInputStream(path);
  }
}