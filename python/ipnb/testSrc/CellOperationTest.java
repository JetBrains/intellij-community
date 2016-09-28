import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.IpnbParser;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;

import java.io.IOException;


public class CellOperationTest extends IpnbTestCase {
  
  public void testAddCell() throws IOException {
    final String fileName = "testData/emptyFile.ipynb";
    final String fileText = IpnbTestCase.getFileText(fileName);

    final IpnbFile ipnbFile = IpnbParser.parseIpnbFile(fileText, new LightVirtualFile());
    ipnbFile.addCell(IpnbCodeCell.createEmptyCodeCell(), ipnbFile.getCells().size());
    final IpnbCodeCell cell = (IpnbCodeCell)ipnbFile.getCells().get(ipnbFile.getCells().size() - 1);
    assertTrue(cell.getCellOutputs().isEmpty());
    assertNull(cell.getPromptNumber());
    assertTrue(cell.getMetadata().isEmpty());
  }
  
  public void testRemoveCell() throws IOException {
    final String fileName = "testData/emptyFile.ipynb";
    final String fileText = IpnbTestCase.getFileText(fileName);

    final IpnbFile ipnbFile = IpnbParser.parseIpnbFile(fileText, new LightVirtualFile());
    ipnbFile.addCell(IpnbCodeCell.createEmptyCodeCell(), ipnbFile.getCells().size());
    ipnbFile.removeCell(ipnbFile.getCells().size() - 1);
    
    assertEquals(fileText, IpnbTestCase.getFileText(fileName));
  }
}
