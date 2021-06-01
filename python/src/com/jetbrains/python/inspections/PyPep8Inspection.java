// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection;
import com.intellij.codeInspection.ui.ListEditForm;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dummy inspection for configuring the PEP8 checker. The checking itself is performed by
 * Pep8ExternalAnnotator.
 */
public class PyPep8Inspection extends PyInspection implements ExternalAnnotatorBatchInspection {
  public List<String> ignoredErrors = new ArrayList<>();
  public static final String INSPECTION_SHORT_NAME = "PyPep8Inspection";

  @Override
  public JComponent createOptionsPanel() {
    ListEditForm form = new ListEditForm(PyBundle.message("INSP.settings.pep8.ignore.errors"), PyBundle.message("INSP.settings.pep8.ignore.errors.label"), ignoredErrors);
    return form.getContentPanel();
  }

  @NotNull
  @Override
  public String getShortName() {
    return INSPECTION_SHORT_NAME;
  }
}
