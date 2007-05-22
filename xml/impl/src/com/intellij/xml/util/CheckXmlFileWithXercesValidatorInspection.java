/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.xml.XmlBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 */
public class CheckXmlFileWithXercesValidatorInspection extends UnfairLocalInspectionTool {
  public static final @NonNls String SHORT_NAME = "CheckXmlFileWithXercesValidator";

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.XML_INSPECTIONS;
  }

  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("xml.inspections.check.file.with.xerces");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }
}