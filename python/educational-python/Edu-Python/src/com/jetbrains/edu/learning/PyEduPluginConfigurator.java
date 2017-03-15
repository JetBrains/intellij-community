package com.jetbrains.edu.learning;

import org.jetbrains.annotations.NotNull;

public class PyEduPluginConfigurator implements EduPluginConfigurator {
  public static final String PYTHON_3 = "3.x";
  public static final String PYTHON_2 = "2.x";

  @NotNull
  @Override
  public String getTestFileName() {
    return "tests.py";
  }
}
