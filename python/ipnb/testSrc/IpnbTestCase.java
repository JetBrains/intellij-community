import com.intellij.openapi.application.PathManager;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public abstract class IpnbTestCase extends TestCase {
  
  static String getFileText(@NotNull final String fileName) throws IOException {
    String fullPath = PathManager.getHomePath() + "/community/python/ipnb/" + fileName;
    final BufferedReader br = new BufferedReader(new FileReader(fullPath));
    try {
      final StringBuilder sb = new StringBuilder();
      String line = br.readLine();

      while (line != null) {
        sb.append(line);
        sb.append("\n");
        line = br.readLine();
      }
      return sb.toString();
    }
    finally {
      br.close();
    }
  }
}
