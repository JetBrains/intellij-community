package com.jetbrains.performancePlugin.lang;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class IJPerfFileType extends LanguageFileType {

  public static final IJPerfFileType INSTANCE = new IJPerfFileType();
  public static final String DEFAULT_EXTENSION = "ijperf";

  private IJPerfFileType() {super(IJPerfLanguage.INSTANCE);}

  @NotNull
  @Override
  public String getName() {
    return "IntegrationPerformanceTest";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return PerformanceTestingBundle.message("filetype.ijperformance.test.display.name");
  }

  @NotNull
  @Override
  public String getDescription() {
    return PerformanceTestingBundle.message("filetype.ijperformance.test.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Text;
  }
}
