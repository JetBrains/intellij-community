package de.plushnikov.intellij.plugin.inspection;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.util.PathUtil;
import com.siyeh.ig.LightInspectionTestCase;
import de.plushnikov.TestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.testFramework.LightPlatformTestCase.getModule;

public abstract class LombokInspectionTest extends LightInspectionTestCase {
  static final String TEST_DATA_INSPECTION_DIRECTORY = "testData/inspection";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final String lombokLibPath = PathUtil.toSystemIndependentName(new File(TEST_DATA_INSPECTION_DIRECTORY, "lib").getAbsolutePath());
    VfsRootAccess.allowRootAccess(lombokLibPath);

    TestUtil.addLibrary(myFixture, getModule(), "Lombok", lombokLibPath, "lombok.jar");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public Sdk getSdk() {
        return JavaSdk.getInstance().createJdk("java 1.7", "lib/mockJDK-1.7", false);
      }

      @Override
      public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_7);
      }
    };
  }
}
