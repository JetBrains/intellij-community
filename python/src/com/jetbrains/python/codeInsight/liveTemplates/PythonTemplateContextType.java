package com.jetbrains.python.codeInsight.liveTemplates;

import com.intellij.codeInsight.template.FileTypeBasedContextType;
import com.jetbrains.python.PythonFileType;

/**
 * @author yole
 */
public class PythonTemplateContextType extends FileTypeBasedContextType {
  public PythonTemplateContextType() {
    super("Python", "Python", PythonFileType.INSTANCE);
  }
}
