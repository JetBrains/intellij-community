package com.intellij.bash.template;

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

public class BashTemplateProvider implements DefaultLiveTemplatesProvider {
  @Override
  public String[] getDefaultLiveTemplateFiles() {
    return new String[]{"liveTemplates/Bash"};
  }

  @Nullable
  @Override
  public String[] getHiddenLiveTemplateFiles() {
    return new String[]{"liveTemplates/BashHidden"};
  }
}
