import lombok.SneakyThrows;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class SneakyThrowsTryWithResources {

  private static class SomeException extends Exception {

  }

  private Connection getConnection() throws IOException, SQLException, SomeException {
    return null;
  }

  @SneakyThrows
  public void methodOneNotCatched() {
    try (Connection connection = getConnection()) {

      // no errors
    } catch (IOException | SQLException e) {

    }
  }

  @SneakyThrows
  public void methodAllCatched() {
    try (Connection connection = getConnection()) {

      // no errors
    } catch (IOException | SQLException | SomeException e) {

    }
  }

  public void methodWithUnhandledException() {
    try (Connection connection = <error descr="Unhandled exception: SneakyThrowsTryWithResources.SomeException">getConnection</error>()) {

    } catch (IOException | SQLException e) {

    }
  }
}