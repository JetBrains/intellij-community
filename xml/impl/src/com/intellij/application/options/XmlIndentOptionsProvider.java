package com.intellij.application.options;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.FileTypeIndentOptionsProvider;

/**
 * @author yole
 */
public class XmlIndentOptionsProvider implements FileTypeIndentOptionsProvider {
  public CodeStyleSettings.IndentOptions createIndentOptions() {
    final CodeStyleSettings.IndentOptions options = new CodeStyleSettings.IndentOptions();
    // HACK [yole]
    if ("Ruby".equals(System.getProperty("idea.platform.prefix"))) {
      options.INDENT_SIZE = 2;
    }
    return options;
  }

  public FileType getFileType() {
    return StdFileTypes.XML;
  }

  public IndentOptionsEditor createOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }

  public String getPreviewText() {
    return CodeStyleAbstractPanel.readFromFile(getClass(), "preview.xml.template");
  }

  public void prepareForReformat(final PsiFile psiFile) {
  }
}
