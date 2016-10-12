package com.jetbrains.edu.learning;

import com.jetbrains.edu.learning.core.EduNames;
import org.jetbrains.annotations.NotNull;

public class PyStudyLanguageManager implements StudyLanguageManager {
  public static final String PYTHON_3 = "3.x";
  public static final String PYTHON_2 = "2.x";

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
