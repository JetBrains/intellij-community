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

import com.google.common.collect.Lists;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.buildout.config.BuildoutCfgFileType;
import com.jetbrains.python.buildout.config.BuildoutCfgLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author traff
 */
public class BuildoutCfgFile extends PsiFileBase {
  public BuildoutCfgFile(FileViewProvider viewProvider) {
    super(viewProvider, BuildoutCfgLanguage.INSTANCE);
  }

  @NotNull
  public FileType getFileType() {
    return BuildoutCfgFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "buildout.cfg file";
  }

  public Collection<BuildoutCfgSection> getSections() {
    return PsiTreeUtil.collectElementsOfType(this, BuildoutCfgSection.class);
  }

  @Nullable
  public BuildoutCfgSection findSectionByName(String name) {
    final Collection<BuildoutCfgSection> sections = getSections();
    for (BuildoutCfgSection section : sections) {
      if (name.equals(section.getHeaderName())) {
        return section;
      }
    }
    return null;
  }

  public List<String> getParts() {
    BuildoutCfgSection buildoutSection = findSectionByName("buildout");
    if (buildoutSection == null) {
      return Collections.emptyList();
    }
    final BuildoutCfgOption option = buildoutSection.findOptionByName("parts");
    if (option == null) {
      return Collections.emptyList();
    }
    List<String> result = Lists.newArrayList();
    for (String value : option.getValues()) {
      result.addAll(StringUtil.split(value, " "));
    }
    return result;
  }
}
