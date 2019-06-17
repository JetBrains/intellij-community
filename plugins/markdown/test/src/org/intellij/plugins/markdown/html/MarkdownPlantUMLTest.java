// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.html;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.intellij.plugins.markdown.settings.MarkdownSettingsConfigurable;
import org.intellij.plugins.markdown.ui.preview.MarkdownCodeFencePluginCache;
import org.intellij.plugins.markdown.ui.preview.MarkdownUtil;

import java.io.IOException;

public class MarkdownPlantUMLTest extends BasePlatformTestCase {
  private static final String PLANTUML_TARGET_DIR = "plantuml";
  protected TempDirTestFixture myFixture;

  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/html/plantuml";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    myFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }

  public void testPlantUML1() {
    doTest();
  }

  public void testPlantUML2() {
    doTest();
  }

  public void testPuml() {
    doTest();
  }

  void doTest() {
    myFixture.copyAll(getTestDataPath(), PLANTUML_TARGET_DIR);
    MarkdownSettingsConfigurable.PLANTUML_JAR_TEST.set(myFixture.getFile(PLANTUML_TARGET_DIR + "/plantuml.jar"));

    VirtualFile mdVFile = myFixture.getFile(PLANTUML_TARGET_DIR + "/" + getTestName(true) + ".md");
    try {
      assertTrue(MarkdownUtil.INSTANCE.generateMarkdownHtml(mdVFile, VfsUtilCore.loadText(mdVFile), getProject()).contains(
        MarkdownUtil.INSTANCE.md5(mdVFile.getPath(), MarkdownCodeFencePluginCache.MARKDOWN_FILE_PATH_KEY)));
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}