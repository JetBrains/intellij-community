package com.theoryinpractice.testng;

import com.intellij.codeInsight.intention.impl.createTest.CreateTestBaseProvider;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;

public class CreateTestNGTestProvider extends CreateTestBaseProvider {
  public String getName() {
    return "TestNG";
  }

  protected String getMarkerClassFQName() {
    return getTestAnnotation();
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

  @Nullable
  public String getSetUpAnnotation() {
    return "org.testng.annotations.BeforeTest";
  }

  @Nullable
  public String getTearDownAnnotation() {
    return "org.testng.annotations.AfterTest";
  }

  @Nullable
  public String getTestAnnotation() {
    return "org.testng.annotations.Test";
  }
}
