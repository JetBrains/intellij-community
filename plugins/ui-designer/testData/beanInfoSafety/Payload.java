import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Stands in for the arbitrary code an attacker would run. Appends to the file named by the
 * {@code uiDesigner.test.payloadMarker} system property so the test can observe it from another
 * classloader.
 */
public final class Payload {
  public static void record(String what) {
    String marker = System.getProperty("uiDesigner.test.payloadMarker");
    if (marker == null) {
      return;
    }
    try {
      Files.write(Path.of(marker), (what + "\n").getBytes("UTF-8"),
                  StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    catch (IOException ignored) {
    }
  }
}
