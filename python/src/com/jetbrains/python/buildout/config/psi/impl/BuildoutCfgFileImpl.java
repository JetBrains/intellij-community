package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.buildout.config.BuildoutCfgFileType;
import com.jetbrains.python.buildout.config.BuildoutCfgLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author traff
 */
public class BuildoutCfgFileImpl extends PsiFileBase {
  public BuildoutCfgFileImpl(FileViewProvider viewProvider) {
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

  public Collection<BuildoutCfgSectionImpl> getSections() {
    return PsiTreeUtil.collectElementsOfType(this, BuildoutCfgSectionImpl.class);
  }

  @Nullable
  public BuildoutCfgSectionImpl findSectionByName(String name) {
    final Collection<BuildoutCfgSectionImpl> sections = getSections();
    for (BuildoutCfgSectionImpl section : sections) {
      if (name.equals(section.getHeaderName())) {
        return section;
      }
    }
    return null;
  }

  public List<String> getParts() {
    BuildoutCfgSectionImpl buildoutSection = findSectionByName("buildout");
    if (buildoutSection == null) {
      return Collections.emptyList();
    }
    final BuildoutCfgOptionImpl option = buildoutSection.findOptionByName("parts");
    if (option == null) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<String>();
    for (String value : option.getValues()) {
      result.addAll(StringUtil.split(value, " "));
    }
    return result;
  }
}
