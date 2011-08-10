package com.jetbrains.python.formatter;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.FileTypeIndentOptionsProvider;
import com.jetbrains.python.PythonFileType;

/**
 * @author yole
 */
public class PyIndentOptionsProvider implements FileTypeIndentOptionsProvider {
  public CommonCodeStyleSettings.IndentOptions createIndentOptions() {
    final CommonCodeStyleSettings.IndentOptions indentOptions = new CommonCodeStyleSettings.IndentOptions();
    indentOptions.INDENT_SIZE = 4;
    return indentOptions;
  }

  public FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  public IndentOptionsEditor createOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }

  public String getPreviewText() {
    return "def foo():\n"+
           "    print 'bar'";
  }

  public void prepareForReformat(final PsiFile psiFile) {
  }
}
