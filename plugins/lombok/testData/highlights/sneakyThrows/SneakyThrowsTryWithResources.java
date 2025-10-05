import lombok.SneakyThrows;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class SneakyThrowsTryWithResources {

  private static class SomeException extends Exception {

  }

  private static class CustomException extends Exception {

  }

  private Reader getReader() throws IOException, CustomException, SomeException {
    return new StringReader("");
  }

  @SneakyThrows
  public void methodOneNotCatched() {
    try (Reader reader = getReader()) {

      // no errors
    } catch (IOException | CustomException e) {

    }
  }

  @SneakyThrows
  public void methodAllCatched() {
    try (Reader reader = getReader()) {

      // no errors
    } catch (IOException | CustomException | SomeException e) {

    }
  }

  public void methodWithUnhandledException() {
    try (Reader reader = <error descr="Unhandled exception: SneakyThrowsTryWithResources.SomeException">getReader</error>()) {

    } catch (IOException | CustomException e) {

    }
  }
}