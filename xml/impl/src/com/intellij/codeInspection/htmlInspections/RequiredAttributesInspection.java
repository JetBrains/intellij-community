// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.expandableString;
import static com.intellij.codeInspection.options.OptPane.pane;

public class RequiredAttributesInspection extends RequiredAttributesInspectionBase {
  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      expandableString("myAdditionalRequiredHtmlAttributes", 
                               XmlBundle.message("inspection.javadoc.html.not.required.label.text"), ",")
    );
  }
}
