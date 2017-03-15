package com.jetbrains.edu.learning;

import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

public interface EduPluginConfigurator {
  LanguageExtension<EduPluginConfigurator> INSTANCE = new LanguageExtension<>("Edu.pluginConfigurator");

  @NotNull
  String getTestFileName();
}
