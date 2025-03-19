// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.Language;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Inherit in the module you are going to use it
@ApiStatus.Internal
public abstract class PythonIdeLanguageCustomization extends IdeLanguageCustomization {
  protected PythonIdeLanguageCustomization() {}
  @Override
  public @NotNull List<Language> getPrimaryIdeLanguages() {
    return ContainerUtil.createMaybeSingletonList(findPythonLanguageByID());
  }

  public static boolean isMainlyPythonIde() {
    Language python = findPythonLanguageByID();
    if (python == null) return false;
    return IdeLanguageCustomization.getInstance().getPrimaryIdeLanguages().contains(python);
  }

  private static @Nullable Language findPythonLanguageByID() {
    return Language.findLanguageByID("Python");
  }
}
