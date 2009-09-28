package com.theoryinpractice.testng;

import com.intellij.testIntegration.JavaTestFrameworkDescriptor;
import com.intellij.util.PathUtil;
import com.intellij.psi.PsiClass;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nullable;

public class TestNGFrameworkDescriptor extends JavaTestFrameworkDescriptor {
  public String getName() {
    return "TestNG";
  }

  protected String getMarkerClassFQName() {
    return "org.testng.annotations.Test";
  }

  public String getLibraryPath() {
    try {
      return PathUtil.getJarPathForClass(Class.forName("org.testng.annotations.Test"));
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public String getDefaultSuperClass() {
    return null;
  }

  public boolean isTestClass(PsiClass clazz) {
    return TestNGUtil.isTestNGClass(clazz);
  }

  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG SetUp Method.java");
  }

  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG TearDown Method.java");
  }

  public FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG Test Method.java");
  }
}
