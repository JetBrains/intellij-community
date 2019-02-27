package de.plushnikov.intellij.plugin;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import junit.framework.ComparisonFailure;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public abstract class AbstractLombokLightCodeInsightTestCase extends LightCodeInsightFixtureTestCase {
  private static final String LOMBOK_SRC_PATH = "./generated/src/lombok";

  @Override
  protected String getTestDataPath() {
    return ".";
  }

  @Override
  protected String getBasePath() {
    return "testData";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public Sdk getSdk() {
        return JavaSdk.getInstance().createJdk("java 1.8", "lib/mockJDK-1.8", false);
      }

      @Override
      public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_8);
      }
    };
  }

  @Override
  public void setUp() throws Exception {
    VfsRootAccess.allowRootAccess(new File(getTestDataPath(), getBasePath()).getCanonicalPath(),
      new File(LOMBOK_SRC_PATH).getCanonicalPath());

    super.setUp();
    loadFilesFrom(LOMBOK_SRC_PATH);
  }

  private void loadFilesFrom(final String srcPath) {
    List<File> filesByMask = FileUtil.findFilesByMask(Pattern.compile(".*\\.java"), new File(srcPath));
    for (File javaFile : filesByMask) {
      String filePath = PathUtil.toSystemIndependentName(javaFile.getPath());
      myFixture.copyFileToProject(filePath, filePath.substring(srcPath.lastIndexOf("/") + 1));
    }
  }

  protected PsiFile loadToPsiFile(String fileName) {
    final String sourceFilePath = getBasePath() + "/" + fileName;
    VirtualFile virtualFile = myFixture.copyFileToProject(sourceFilePath, fileName);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    return myFixture.getFile();
  }

  protected void checkResultByFile(String expectedFile) throws IOException {
    try {
      myFixture.checkResultByFile(expectedFile, true);
    } catch (ComparisonFailure ex) {
      String actualFileText = myFixture.getFile().getText();
      actualFileText = actualFileText.replace("java.lang.", "");

      final String path = getTestDataPath() + "/" + expectedFile;
      String expectedFileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(path)));

      assertEquals(expectedFileText.replaceAll("\\s+", ""), actualFileText.replaceAll("\\s+", ""));
    }
  }
}
