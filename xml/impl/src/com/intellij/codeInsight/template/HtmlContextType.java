package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.FileType;

/**
 * @author yole
 */
public class HtmlContextType extends AbstractContextType {
  public String getName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.html");
  }

  @Override
  public boolean isInContext(final FileType fileType) {
    return fileType == StdFileTypes.HTML || fileType == StdFileTypes.XHTML;
  }

  protected LanguageFileType getExpectedFileType() {
    return StdFileTypes.HTML;
  }

  protected TemplateContext.ContextElement getContextElement(final TemplateContext context) {
    return context.HTML;
  }

}