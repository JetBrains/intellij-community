// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config;

import com.intellij.lang.Language;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;

public final class BuildoutCfgLanguage extends Language {
  public static final BuildoutCfgLanguage INSTANCE = new BuildoutCfgLanguage();

  private BuildoutCfgLanguage() {
    super("BuildoutCfg");
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("buildout.config.language");
  }
}
