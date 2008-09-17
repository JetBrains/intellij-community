/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.application.options.editor;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.BeanConfigurable;

public class XmlCodeFoldingOptionsProvider extends BeanConfigurable<XmlFoldingSettings> implements CodeFoldingOptionsProvider {
  public XmlCodeFoldingOptionsProvider() {
    super(XmlFoldingSettings.getInstance());
    checkBox("COLLAPSE_XML_TAGS", ApplicationBundle.message("checkbox.collapse.xml.tags"));
  }
}