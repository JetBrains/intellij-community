// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.html;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.intellij.plugins.markdown.extensions.MarkdownCodeFencePluginGeneratingProvider;
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles;
import org.intellij.plugins.markdown.extensions.common.plantuml.PlantUMLCodeGeneratingProvider;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFencePluginCache;
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil;

import java.io.IOException;
import java.util.Collections;

public class MarkdownPlantUMLTest extends BasePlatformTestCase {
  protected TempDirTestFixture myFixture;

  private PlantUMLCodeGeneratingProvider extension = null;

  private static final String BASE_PATH = "/html/plantuml";
  private static final String JAR_DOWNLOAD_SUBDIR = "/download";
  private static final String DATA_SUBDIR = "/data";

  @Override
  protected String getTestDataPath() {
    return MarkdownTestingUtil.TEST_DATA_PATH + BASE_PATH;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    myFixture.setUp();

    extension = MarkdownCodeFencePluginGeneratingProvider.getAll().stream()
      .filter(PlantUMLCodeGeneratingProvider.class::isInstance)
      .map(PlantUMLCodeGeneratingProvider.class::cast)
      .findFirst().orElse(null);
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
    MarkdownExtensionWithExternalFiles.setBASE_DIRECTORY(myFixture.getTempDirPath());
    assertFalse(extension.isAvailable());
    myFixture.copyAll(
      getTestDataPath() + JAR_DOWNLOAD_SUBDIR,
      MarkdownExtensionWithExternalFiles.getDownloadCacheDirectoryName()
    );
    assertTrue(extension.isAvailable());
    myFixture.copyAll(getTestDataPath() + DATA_SUBDIR, "plantuml");

    MarkdownApplicationSettings.getInstance().setExtensionsEnabledState(Collections.singletonMap("PlantUMLLanguageExtension", true));
    assertTrue(extension.isEnabled());

    VirtualFile mdVFile = myFixture.getFile("plantuml/" + getTestName(true) + ".md");
    try {
      assertTrue(MarkdownUtil.INSTANCE.generateMarkdownHtml(mdVFile, VfsUtilCore.loadText(mdVFile), getProject()).contains(
        MarkdownUtil.INSTANCE.md5(mdVFile.getPath(), MarkdownCodeFencePluginCache.MARKDOWN_FILE_PATH_KEY)));
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
