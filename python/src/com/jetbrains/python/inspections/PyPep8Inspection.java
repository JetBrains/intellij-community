// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection;
import com.intellij.codeInspection.options.OptPane;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * Dummy inspection for configuring the PEP8 checker. The checking itself is performed by
 * Pep8ExternalAnnotator.
 */
public final class PyPep8Inspection extends PyInspection implements ExternalAnnotatorBatchInspection {
  public List<String> ignoredErrors = new ArrayList<>();
  public static final String INSPECTION_SHORT_NAME = "PyPep8Inspection";

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(OptPane.stringList("ignoredErrors", PyBundle.message("INSP.settings.pep8.ignore.errors.label")));
  }

  @NotNull
  @Override
  public String getShortName() {
    return INSPECTION_SHORT_NAME;
  }
}
