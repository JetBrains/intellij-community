package com.jetbrains.edu.learning;

import org.jetbrains.annotations.NotNull;

public class PyStudyLanguageManager implements StudyLanguageManager {

  @NotNull
  @Override
  public String getTestFileName() {
    return "tests.py";
  }

  @NotNull
  @Override
  public String getTestHelperFileName() {
    return "test_helper.py";
  }

  @NotNull
  @Override
  public String getUserTester() {
    return "user_tester.py";
  }
}
