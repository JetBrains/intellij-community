// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.template;

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
