import lombok.SneakyThrows;


public class SneakThrowsDoesntCatchCaughtExceptionNested {

  @lombok.SneakyThrows
  public void m() {
    String name;
    try {
      try {
        name = "";
        throwsMyException();
        throwsSomeException();
        throwsAnotherException();
      } catch (SneakThrowsDoesntCatchCaughtExceptionNested.SomeException e) {
      }
    } catch (SneakThrowsDoesntCatchCaughtExceptionNested.AnotherException e) {
      System.out.println(<error descr="Variable 'name' might not have been initialized">name</error>);
    }
  }

  void throwsAnotherException() throws AnotherException {
  }

  void throwsMyException() throws MyException {
  }

  void throwsSomeException() throws SomeException {
  }

  static class MyException extends Exception {
  }

  static class SomeException extends Exception {
  }

  static class AnotherException extends Exception {
  }

  static class Exception extends Throwable {
  }
}
