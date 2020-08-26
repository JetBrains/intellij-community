// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgFile;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgOption;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgSection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BuildoutPsiUtil {
  @NonNls private static final String RECIPE = "recipe";
  @NonNls private static final String DJANGO_RECIPE = "djangorecipe";

  @Nullable
  public static BuildoutCfgSection getDjangoSection(@NotNull BuildoutCfgFile configFile) {
    List<String> parts = configFile.getParts();
    for (String part : parts) {
      final BuildoutCfgSection section = configFile.findSectionByName(part);
      if (section != null && DJANGO_RECIPE.equals(section.getOptionValue(RECIPE))) {
        return section;
      }
    }
    return null;
  }

  public static boolean isInBuildoutSection(@NotNull PsiElement element) {
    BuildoutCfgSection section = PsiTreeUtil.getParentOfType(element, BuildoutCfgSection.class);
    return section != null && "buildout".equals(section.getHeaderName());
  }

  public static boolean isAssignedTo(@NotNull PsiElement element, @NotNull String name) {
    BuildoutCfgOption option = PsiTreeUtil.getParentOfType(element, BuildoutCfgOption.class);
    return (option != null && name.equals(option.getKey()));
  }


}

