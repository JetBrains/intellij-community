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
