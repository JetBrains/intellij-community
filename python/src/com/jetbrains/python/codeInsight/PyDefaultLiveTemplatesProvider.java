package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public class PyDefaultLiveTemplatesProvider implements DefaultLiveTemplatesProvider {
  private static final @NonNls String[] DEFAULT_TEMPLATES = new String[]{
    "/liveTemplates/Django"
  };

  public String[] getDefaultLiveTemplateFiles() {
    return DEFAULT_TEMPLATES;
  }
}
