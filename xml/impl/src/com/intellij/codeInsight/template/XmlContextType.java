package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * @author yole
 */
public class XmlContextType extends AbstractContextType {
  public String getName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.xml");
  }

  protected LanguageFileType getExpectedFileType() {
    return StdFileTypes.XML;
  }

  protected TemplateContext.ContextElement getContextElement(final TemplateContext context) {
    return context.XML;
  }
}
