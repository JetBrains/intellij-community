package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.openapi.util.Key;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dummy inspection for configuring the PEP8 checker. The checking itself is performed by
 * Pep8ExternalAnnotator.
 *
 * @author yole
 */
public class PyPep8Inspection extends PyInspection {
  public List<String> ignoredErrors = new ArrayList<String>();
  public static final String INSPECTION_SHORT_NAME = "PyPep8Inspection";
  public static final Key<PyPep8Inspection> KEY = Key.create(INSPECTION_SHORT_NAME);

  @Override
  public JComponent createOptionsPanel() {
    ListEditForm form = new ListEditForm("Ignore errors", ignoredErrors);
    return form.getContentPanel();
  }
}
