package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public class HtmlContextType implements TemplateContextType {
  public String getName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.html");
  }

  public boolean isInContext(final PsiFile file, final int offset) {
    return file.getFileType() == StdFileTypes.HTML;
  }

  public boolean isEnabled(final TemplateContext context) {
    return context.HTML;
  }

  public void setEnabled(final TemplateContext context, final boolean value) {
    context.HTML = value;
  }

  public SyntaxHighlighter createHighlighter() {
    return  SyntaxHighlighter.PROVIDER.create(StdFileTypes.HTML, null, null);
  }
}