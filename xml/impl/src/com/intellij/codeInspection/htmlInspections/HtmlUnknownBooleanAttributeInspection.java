// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.*;

public class HtmlUnknownBooleanAttributeInspection extends HtmlUnknownBooleanAttributeInspectionBase {
  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("myCustomValuesEnabled", XmlAnalysisBundle.message("html.inspections.unknown.tag.boolean.attribute.checkbox.title"),
               stringList("myValues", ""))
    );
  }
}
