package inspection.autoCloseableResource;

import lombok.Cleanup;

import java.io.IOException;
import java.io.InputStream;

public class AutoCloseableCleanup {

  public static void main(String[] args) throws IOException {
    withoutCleanUp();
    withCleanUp();
  }

  private static void withCleanUp() throws IOException {
    @Cleanup InputStream profile = Thread.currentThread().getContextClassLoader().getResourceAsStream("/someFile");
    System.out.println(profile.read());
  }

  private static void withoutCleanUp() throws IOException {
    InputStream profile = Thread.currentThread().getContextClassLoader().<warning descr="'InputStream' used without 'try'-with-resources statement">getResourceAsStream</warning>("/someFile");
    System.out.println(profile.read());
  }
}
