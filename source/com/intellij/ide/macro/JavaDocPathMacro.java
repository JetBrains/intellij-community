package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.javadoc.JavadocConfiguration;
import com.intellij.javadoc.JavadocGenerationManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;

import java.io.File;

public final class JavaDocPathMacro extends Macro {
  public String getName() {
    return "JavaDocPath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.javadoc.output.directory");
  }

  public String expand(DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }
    JavadocGenerationManager manager = project.getComponent(JavadocGenerationManager.class);
    if (manager == null) {
      return null;
    }
    final JavadocConfiguration configuration = manager.getConfiguration();
    return configuration.OUTPUT_DIRECTORY == null ? null : configuration.OUTPUT_DIRECTORY.replace('/', File.separatorChar);
  }
}
