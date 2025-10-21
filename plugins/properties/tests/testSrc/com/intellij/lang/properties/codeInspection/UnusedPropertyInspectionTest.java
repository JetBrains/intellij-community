package com.intellij.lang.properties.codeInspection;

import com.intellij.lang.properties.codeInspection.unused.UnusedPropertyInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;

import java.io.IOException;

public class UnusedPropertyInspectionTest extends CodeInsightFixtureTestCase<ModuleFixtureBuilder<?>> {

  public void testUnused() {
    myFixture.configureByFile("root_project.properties");
    myFixture.checkHighlighting();
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("properties") + "/tests/testData/propertiesFile/unused";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    configureAdditionalModule();

    myFixture.enableInspections(UnusedPropertyInspection.class);
  }

  private void configureAdditionalModule() throws IOException {
    final String path = ProjectKt.getStateStore(getProject()).getProjectBasePath() + "/module";
    final ModuleManager moduleManager = ModuleManager.getInstance(getProject());

    final Module module = WriteAction.compute(() -> moduleManager.newModule(path, JavaModuleType.getModuleType().getId()));

    ModuleRootModificationUtil.addDependency(module, myModule);
    configureSources(module);
  }

  private void configureSources(Module module) throws IOException {
    final SourceFolder src = PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().findOrCreateDir("src"));
    @Language("JAVA") final String javaClass = """
      public class Main {
        static {
          System.getProperty("used");
        }
      }""";
    VirtualFile file = VfsTestUtil.createFile(src.getFile(), "org/main/Main.java", javaClass);
    myFixture.allowTreeAccessForFile(file);
  }
}