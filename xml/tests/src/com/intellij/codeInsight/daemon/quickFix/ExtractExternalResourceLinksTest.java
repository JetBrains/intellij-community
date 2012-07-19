package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ExtractExternalResourceLinksTest extends CodeInsightTestCase {

  protected static String getBasePath() {
    return "/quickFix/fetchExternalResources";
  }

  public void testExtractionOfEmbeddedFiles() throws Exception {
    doExtractionOfEmbeddedFiles(
      "1.dtd",
      new String[] {"xhtml-lat1.ent", "xhtml-symbol.ent", "xhtml-special.ent"}
    );

    doExtractionOfEmbeddedFiles(
      "1.xsd",
      new String[] {"j2ee_1_4.xsd", "jsp_2_0.xsd"}
    );

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
      new String[] {"j2ee_1_4.xsd", "jsp_2_0.xsd"}
    );

    doExtractionOfEmbeddedFiles(
      "4.xml",
      new String[] {
        "http://www.springframework.org/schema/beans/spring-beans.xsd",
        "http://www.springframework.org/schema/aop/spring-aop.xsd",
        "http://www.springframework.org/schema/tx/spring-tx.xsd"
      }
    );

    doExtractionOfEmbeddedFiles(
      "5.xml",
      new String[] {
        "http://geronimo.apache.org/xml/ns/geronimo-naming-1.1.xsd",
        "http://geronimo.apache.org/xml/ns/geronimo-security-1.1.xsd",
        "http://geronimo.apache.org/xml/ns/geronimo-module-1.1.xsd"
      }
    );

    doExtractionOfEmbeddedFiles(
      "6.xml",
      new String[] {
        "http://jboss.com/products/seam/components-1.2.xsd"
      }
    );
  }

  private void doExtractionOfEmbeddedFiles(String shortFileName,String[] expectedFileNames) throws Exception {
    doExtractionOfEmbeddedFiles(new String[] {shortFileName}, new String[][] {expectedFileNames} );
  }

  private void doExtractionOfEmbeddedFiles(String[] shortFileName,String[][] expectedFileNames) throws Exception {
    final List<VirtualFile> files = new ArrayList<VirtualFile>(shortFileName.length);
    for(String s:shortFileName) {
      files.add(getVirtualFile( getBasePath() + "/"+ s ));
    }

    int fileIndex = 0;

    for(String[] expectedFileNameArray:expectedFileNames) {
      List<String> strings = FetchExtResourceAction.extractEmbeddedFileReferences(
        files.get(fileIndex), fileIndex != 0? files.get(0): null, getPsiManager()
      );

      assertEquals(expectedFileNameArray.length,strings.size());
      int index = 0;

      for (final String string : strings) {
        assertEquals(string, expectedFileNameArray[index]);
        ++index;
      }

      fileIndex++;
    }
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }
}
