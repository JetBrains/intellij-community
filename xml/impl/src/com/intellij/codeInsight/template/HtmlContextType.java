package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class HtmlContextType extends FileTypeBasedContextType {
  public HtmlContextType() {
    super("HTML", CodeInsightBundle.message("dialog.edit.template.checkbox.html"), StdFileTypes.HTML);
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return file.getLanguage() instanceof HTMLLanguage;
  }

  @Override
  public boolean isInContext(@NotNull final FileType fileType) {
    return fileType == StdFileTypes.HTML || fileType == StdFileTypes.XHTML;
  }

}