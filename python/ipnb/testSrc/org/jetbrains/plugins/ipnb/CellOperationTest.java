package org.jetbrains.plugins.ipnb;

import com.intellij.testFramework.LightVirtualFile;
import junit.framework.TestCase;
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
    TestCase.assertTrue(cell.getCellOutputs().isEmpty());
    TestCase.assertNull(cell.getPromptNumber());
    TestCase.assertTrue(cell.getMetadata().isEmpty());
  }
  
  public void testRemoveCell() throws IOException {
    final String fileName = "testData/emptyFile.ipynb";
    final String fileText = IpnbTestCase.getFileText(fileName);

    final IpnbFile ipnbFile = IpnbParser.parseIpnbFile(fileText, new LightVirtualFile());
    ipnbFile.addCell(IpnbCodeCell.createEmptyCodeCell(), ipnbFile.getCells().size());
    ipnbFile.removeCell(ipnbFile.getCells().size() - 1);
    
    TestCase.assertEquals(fileText, IpnbTestCase.getFileText(fileName));
  }
}
