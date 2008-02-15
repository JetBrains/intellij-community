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
    return new CodeStyleSettings.IndentOptions();
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
