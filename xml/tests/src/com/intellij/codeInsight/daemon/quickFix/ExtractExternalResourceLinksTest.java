package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExtractExternalResourceLinksTest extends LightCodeInsightTestCase {

  protected static String getBasePath() {
    return "/quickFix/fetchExternalResources";
  }

  public void testExtractionOfEmbeddedFiles() throws Exception {
    doExtractionOfEmbeddedFiles(
      "1.dtd",
      "xhtml-lat1.ent", "xhtml-symbol.ent", "xhtml-special.ent");

    doExtractionOfEmbeddedFiles(
      "1.xsd",
      "j2ee_1_4.xsd", "jsp_2_0.xsd");

    doExtractionOfEmbeddedFiles(
      new String[] { "2.dtd", "2.mod" },
      new String[][] {
        new String[] {"dbnotnx.mod", "dbcentx.mod", "dbpoolx.mod", "dbhierx.mod", "dbgenent.mod"},
        new String[] {"htmltblx.mod", "calstblx.dtd"}
      }
    );

    doExtractionOfEmbeddedFiles(
      new String[] { "3.dtd"},
      new String[][] {
        new String[] {"onix-international.elt"}
      }
    );

    doExtractionOfEmbeddedFiles(
      "3.xsd",
      "j2ee_1_4.xsd", "jsp_2_0.xsd");

    doExtractionOfEmbeddedFiles(
      "4.xml",
      "http://www.springframework.org/schema/beans/spring-beans.xsd",
      "http://www.springframework.org/schema/aop/spring-aop.xsd",
      "http://www.springframework.org/schema/tx/spring-tx.xsd");

  }

  public void testSeamImport() throws Exception {
    doExtractionOfEmbeddedFiles(
      "6.xml",
      "http://jboss.com/products/seam/components-1.2.xsd");

  }

  public void testBPMN() throws Exception {
    doExtractionOfEmbeddedFiles("BPMN20.xsd",
                                "BPMNDI.xsd", "Semantic.xsd");

  }

  public void testGeronimo() throws Exception {
    doExtractionOfEmbeddedFiles("web-1.1",
                                "http://geronimo.apache.org/xml/ns/geronimo-naming-1.1.xsd",
                                "http://geronimo.apache.org/xml/ns/geronimo-security-1.1.xsd",
                                "http://geronimo.apache.org/xml/ns/geronimo-module-1.1.xsd");
  }

  private void doExtractionOfEmbeddedFiles(String shortFileName,String... expectedFileNames) throws Exception {
    doExtractionOfEmbeddedFiles(new String[] {shortFileName}, new String[][] {expectedFileNames} );
  }

  private void doExtractionOfEmbeddedFiles(String[] shortFileName,String[][] expectedFileNames) throws Exception {
    final List<VirtualFile> files = new ArrayList<VirtualFile>(shortFileName.length);
    for(String s: shortFileName) {
      if (FileUtilRt.getExtension(s).length() < 3) {
        s += ".xsd";
      }
      files.add(getVirtualFile( getBasePath() + "/"+ s ));
    }

    int fileIndex = 0;

    for (int i = 0; i < expectedFileNames.length; i++) {
      String[] expectedFileNameArray = expectedFileNames[i];
      Set<String> strings = FetchExtResourceAction.extractEmbeddedFileReferences(
        files.get(fileIndex), fileIndex != 0 ? files.get(0) : null, getPsiManager(),
        shortFileName[i]);

      assertEquals(expectedFileNameArray.length, strings.size());
      int index = 0;

      for (final String string : strings) {
        assertEquals(expectedFileNameArray[index], string);
        ++index;
      }

      fileIndex++;
    }
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }
}
