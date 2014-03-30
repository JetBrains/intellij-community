/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.xml.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.codeInspection.XmlInspectionGroupNames;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.impl.ExternalDocumentValidator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 * @see com.intellij.xml.impl.ExternalDocumentValidator
 */
public class CheckXmlFileWithXercesValidatorInspection extends XmlSuppressableInspectionTool implements UnfairLocalInspectionTool {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.XML_INSPECTIONS;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("xml.inspections.check.file.with.xerces");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return ExternalDocumentValidator.INSPECTION_SHORT_NAME;
  }
}