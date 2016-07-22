package com.jetbrains.edu.learning;

import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

public interface StudyLanguageManager {
  LanguageExtension<StudyLanguageManager> INSTANCE = new LanguageExtension<>("Edu.StudyLanguageManager");

  @NotNull
  String getTestFileName();

  @NotNull
  String getTestHelperFileName();

  @NotNull
  String getUserTester();
}
