package com.intellij.codeInsight.template;

import com.intellij.psi.PsiFile;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;

/**
 * @author yole
 */
public class XmlContextType implements TemplateContextType {
  public String getName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.xml");
  }

  public boolean isInContext(final PsiFile file, final int offset) {
    return file.getFileType() == StdFileTypes.XML;
  }

  public boolean isEnabled(final TemplateContext context) {
    return context.XML;
  }

  public void setEnabled(final TemplateContext context, final boolean value) {
    context.XML = value;
  }

  public SyntaxHighlighter createHighlighter() {
    return  SyntaxHighlighter.PROVIDER.create(StdFileTypes.XML, null, null);
  }
}
