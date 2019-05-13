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
package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public class BuildoutCfgSection extends BuildoutCfgPsiElement {
  public BuildoutCfgSection(@NotNull final ASTNode node) {
    super(node);
  }

  @Nullable
  public String getHeaderName() {
    BuildoutCfgSectionHeader header = PsiTreeUtil.findChildOfType(this, BuildoutCfgSectionHeader.class);
    return header != null ? header.getName() : null;
  }


  public List<BuildoutCfgOption> getOptions() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BuildoutCfgOption.class);
  }

  @Nullable
  public BuildoutCfgOption findOptionByName(String name) {
    for (BuildoutCfgOption option : getOptions()) {
      if (name.equals(option.getKey())) {
        return option;
      }
    }
    return null;
  }

  @Nullable
  public String getOptionValue(String name) {
    final BuildoutCfgOption option = findOptionByName(name);
    if (option != null) {
      return StringUtil.join(option.getValues(), " ");
    }
    return null;
  }

  @Override
  public String toString() {
    return "BuildoutCfgSection:" + getNode().getElementType().toString();
  }
}
