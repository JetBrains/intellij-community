// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.liveTemplates;

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public class PyDefaultLiveTemplatesProvider implements DefaultLiveTemplatesProvider {
  private static final @NonNls String[] DEFAULT_TEMPLATES = new String[]{
    "/liveTemplates/Python"
  };

  @Override
  public String[] getDefaultLiveTemplateFiles() {
    return DEFAULT_TEMPLATES;
  }

  @Override
  public String[] getHiddenLiveTemplateFiles() {
    return null;
  }
}
