package com.jetbrains.tmp.learning;

import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

public interface StudyLanguageManager {
  LanguageExtension<StudyLanguageManager> INSTANCE = new LanguageExtension<StudyLanguageManager>("SCore.StudyLanguageManager");

  @NotNull
  String getTestFileName();

  @NotNull
  String getTestHelperFileName();

  @NotNull
  String getUserTester();
}
