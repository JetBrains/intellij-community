package com.intellij.application.options;

import com.intellij.psi.codeStyle.FileTypeIndentOptionsProvider;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * @author yole
 */
public class XmlIndentOptionsProvider implements FileTypeIndentOptionsProvider {
  public CodeStyleSettings.IndentOptions createIndentOptions() {
    final CodeStyleSettings.IndentOptions options = new CodeStyleSettings.IndentOptions();
    // HACK [yole]
    if (System.getProperty("idea.platform.prefix").equals("Ruby")) {
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
