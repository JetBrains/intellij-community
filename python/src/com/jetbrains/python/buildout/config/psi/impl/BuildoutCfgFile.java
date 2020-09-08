// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.buildout.config.BuildoutCfgFileType;
import com.jetbrains.python.buildout.config.BuildoutCfgLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class BuildoutCfgFile extends PsiFileBase {

  @NonNls private static final String BUILDOUT_SECTION = "buildout";
  @NonNls private static final String PARTS_OPTION = "parts";

  public BuildoutCfgFile(FileViewProvider viewProvider) {
    super(viewProvider, BuildoutCfgLanguage.INSTANCE);
  }

  @Override
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
    BuildoutCfgSection buildoutSection = findSectionByName(BUILDOUT_SECTION);
    if (buildoutSection == null) {
      return Collections.emptyList();
    }
    final BuildoutCfgOption option = buildoutSection.findOptionByName(PARTS_OPTION);
    if (option == null) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    for (String value : option.getValues()) {
      result.addAll(StringUtil.split(value, " "));
    }
    return result;
  }
}
