import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightVirtualFile;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.IpnbParser;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class JsonParserTest extends TestCase {
  public void testFile() throws IOException {
    final String fileName = "testData/SymPy.ipynb";
    final String fileText = getFileText(fileName);

    final IpnbFile ipnbFile = IpnbParser.parseIpnbFile(fileText, new LightVirtualFile());
    assertNotNull(ipnbFile);
    assertEquals(31, ipnbFile.getCells().size());
  }

  public void testMarkdownCells() throws IOException {
    final String fileName = "testData/SymPy.ipynb";
    final String fileText = getFileText(fileName);
    final IpnbFile ipnbFile = IpnbParser.parseIpnbFile(fileText, new LightVirtualFile());
    assertNotNull(ipnbFile);
    final List<IpnbCell> cells = ipnbFile.getCells();
    Iterables.removeIf(cells, new Predicate<IpnbCell>() {
      @Override
      public boolean apply(IpnbCell cell) {
        return !(cell instanceof IpnbMarkdownCell);
      }
    });
    assertEquals(7, cells.size());
  }

  public void testMarkdownCell() throws IOException {
    final String fileName = "testData/markdown.ipynb";
    final String fileText = getFileText(fileName);
    final IpnbFile ipnbFile = IpnbParser.parseIpnbFile(fileText, new LightVirtualFile());
    assertNotNull(ipnbFile);
    final List<IpnbCell> cells = ipnbFile.getCells();
    assertEquals(1, cells.size());
    final IpnbCell cell = cells.get(0);
    assertTrue(cell instanceof IpnbMarkdownCell);
    final List<String> source = ((IpnbMarkdownCell)cell).getSource();
    final String joined = StringUtil.join(source, "");
    assertEquals("<img src=\"images/ipython_logo.png\">", joined);
  }

  public void testCodeCell() throws IOException {
    final String fileName = "testData/code.ipynb";
    final String fileText = getFileText(fileName);
    final IpnbFile ipnbFile = IpnbParser.parseIpnbFile(fileText, new LightVirtualFile());
    assertNotNull(ipnbFile);
    final List<IpnbCell> cells = ipnbFile.getCells();
    assertEquals(1, cells.size());
    final IpnbCell cell = cells.get(0);
    assertTrue(cell instanceof IpnbCodeCell);
    final List<IpnbOutputCell> outputs = ((IpnbCodeCell)cell).getCellOutputs();
    assertEquals(0, outputs.size());
    final List<String> source = ((IpnbCodeCell)cell).getSource();
    final String joined = StringUtil.join(source, "");
    assertEquals("e = x + 2*y", joined);
    final String language = ((IpnbCodeCell)cell).getLanguage();
    assertEquals("python", language);
    final Integer number = ((IpnbCodeCell)cell).getPromptNumber();
    assertEquals(new Integer(4), number);
  }

  public void testOutputs() throws IOException {
    final String fileName = "testData/outputs.ipynb";
    final String fileText = getFileText(fileName);
    final IpnbFile ipnbFile = IpnbParser.parseIpnbFile(fileText, new LightVirtualFile());
    assertNotNull(ipnbFile);
    final List<IpnbCell> cells = ipnbFile.getCells();
    assertEquals(1, cells.size());
    final IpnbCell cell = cells.get(0);
    assertTrue(cell instanceof IpnbCodeCell);
    final List<IpnbOutputCell> outputs = ((IpnbCodeCell)cell).getCellOutputs();
    assertEquals(1, outputs.size());
    final IpnbOutputCell output = outputs.get(0);
    final List<String> text = output.getText();
    assertNotNull(text);
    final String joined = StringUtil.join(text, "");
    assertEquals("\"Add(Symbol('x'), Mul(Integer(2), Symbol('y')))\"", joined);
  }

  private static String getFileText(@NotNull final String fileName) throws IOException {
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
