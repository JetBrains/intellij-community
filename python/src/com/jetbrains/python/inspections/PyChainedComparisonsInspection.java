/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.util.ui.CheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * User: catherine
 *
 * Inspection to detect chained comparisons which can be simplified
 * For instance, a < b and b < c  -->  a < b < c
 */
public class PyChainedComparisonsInspection extends PyPsiChainedComparisonsInspection {

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.add(new CheckBox(ourIgnoreConstantOptionText, this, "ignoreConstantInTheMiddle"),
                  BorderLayout.PAGE_START);

    return rootPanel;
  }
}
