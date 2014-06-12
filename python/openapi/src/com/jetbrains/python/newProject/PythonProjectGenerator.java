package com.jetbrains.python.newProject;

import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public abstract class PythonProjectGenerator {
  @Nullable
  public JComponent getSettingsPanel(File baseDir) throws ProcessCanceledException {
    return null;
  }

  public Object getProjectSettings() {
    return new PyNewProjectSettings();
  }
}
