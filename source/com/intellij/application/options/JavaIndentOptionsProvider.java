package com.intellij.application.options;

import com.intellij.psi.codeStyle.FileTypeIndentOptionsProvider;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * @author yole
 */
public class JavaIndentOptionsProvider implements FileTypeIndentOptionsProvider {
  public CodeStyleSettings.IndentOptions createIndentOptions() {
    return new CodeStyleSettings.IndentOptions();
  }

  public FileType getFileType() {
    return StdFileTypes.JAVA;
  }

  public IndentOptionsEditor createOptionsEditor() {
    return new JavaIndentOptionsEditor();
  }
}
