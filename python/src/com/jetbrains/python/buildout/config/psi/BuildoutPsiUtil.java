/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.buildout.config.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgFile;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgOption;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public class BuildoutPsiUtil {
  private static final String RECIPE = "recipe";
  private static final String DJANGO_RECIPE = "djangorecipe";

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

