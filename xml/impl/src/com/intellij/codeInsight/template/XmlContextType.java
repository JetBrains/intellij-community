package com.intellij.codeInsight.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * @author yole
 */
public class XmlContextType extends FileTypeBasedContextType {
  public XmlContextType() {
    super("XML", CodeInsightBundle.message("dialog.edit.template.checkbox.xml"), StdFileTypes.XML);
  }

}
