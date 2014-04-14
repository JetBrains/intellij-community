import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.IpnbParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class JsonParserTest extends TestCase {
  public void testFile() throws IOException {
    final String fileName = "testData/SymPy.ipynb";
    final String fileText = getFileText(fileName);
    final IpnbFile ipnbFile = IpnbParser.parseIpnbFile(fileText);
    assertNotNull(ipnbFile);
    assertEquals(31, ipnbFile.getCells().size());
  }

  private String getFileText(@NotNull final String fileName) throws IOException {
    final BufferedReader br = new BufferedReader(new FileReader(fileName));
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
