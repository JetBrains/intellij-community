import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.IpnbParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TestJsonParserTest extends TestCase {
    public void testFile() throws IOException {
        final String fileName = "testData/SymPy.ipynb";
        final BufferedReader br = new BufferedReader(new FileReader(fileName));
        try {
            final String fileText = getFileText(br);
            final IpnbFile ipnbFile = IpnbParser.parseIpnbFile(fileText);
            assertNotNull(ipnbFile);
            assertEquals(ipnbFile.getWorksheets()[0].getCells().length, 31);
        } finally {
            br.close();
        }
    }

    private String getFileText(@NotNull final BufferedReader br) throws IOException {
        final StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            sb.append(System.lineSeparator());
            line = br.readLine();
        }
        return sb.toString();
    }
}
