package com.jetbrains.edu.learning;

import com.jetbrains.edu.EduNames;
import org.jetbrains.annotations.NotNull;

public class PyStudyLanguageManager implements StudyLanguageManager {

  @NotNull
  @Override
  public String getTestFileName() {
    return EduNames.TESTS_FILE;
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
