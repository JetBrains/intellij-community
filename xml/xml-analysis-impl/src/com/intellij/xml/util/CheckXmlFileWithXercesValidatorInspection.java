// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xml.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.xml.impl.ExternalDocumentValidator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 * @see ExternalDocumentValidator
 */
public class CheckXmlFileWithXercesValidatorInspection extends XmlSuppressableInspectionTool implements UnfairLocalInspectionTool {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public @NotNull @NonNls String getShortName() {
    return ExternalDocumentValidator.INSPECTION_SHORT_NAME;
  }
}