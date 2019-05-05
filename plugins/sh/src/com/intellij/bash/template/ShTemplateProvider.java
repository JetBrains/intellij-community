package com.intellij.bash.template;

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider;
import org.jetbrains.annotations.Nullable;

public class ShTemplateProvider implements DefaultLiveTemplatesProvider {
  @Override
  public String[] getDefaultLiveTemplateFiles() {
    return new String[]{"liveTemplates/ShellScript", "liveTemplates/ShellScriptArray"};
  }

  @Nullable
  @Override
  public String[] getHiddenLiveTemplateFiles() {
    return new String[]{"liveTemplates/ShellScriptHidden"};
  }
}
