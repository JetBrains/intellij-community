package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider;

/**
 * @author yole
 */
public class XmlDefaultLiveTemplatesProvider implements DefaultLiveTemplatesProvider {
  public String[] getDefaultLiveTemplateFiles() {
    return new String[] { "/liveTemplates/html_xml" };
  }
}
