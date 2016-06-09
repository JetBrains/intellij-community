package de.plushnikov.intellij.plugin.configsystem;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

public abstract class AbstractLombokConfigSystemTestCase extends AbstractLombokParsingTestCase {
  public void doTest() throws IOException {
    final String fullFileName = getTestName(true).replace('$', '/') + ".java";
    final String subPath = fullFileName.substring(0, fullFileName.lastIndexOf('/'));
    final String fileName = fullFileName.substring(fullFileName.lastIndexOf('/') + 1);

    myFixture.copyFileToProject(getBasePath() + "/" + subPath + "/lombok.config", "lombok.config");

    doTest(fullFileName, subPath + "/after/" + fileName);
  }
}
