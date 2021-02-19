/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.inspections.quickfix.AddEncodingQuickFix;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User : catherine
 */
public class PyMandatoryEncodingInspection extends PyInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFile(@NotNull PyFile node) {
      if (!(myAllPythons || LanguageLevel.forElement(node).isPython2())) return;

      final String charsetString = PythonFileType.getCharsetFromEncodingDeclaration(node);
      if (charsetString == null) {
        TextRange tr = new TextRange(0, 0);
        ProblemsHolder holder = getHolder();
        if (holder != null) {
          holder.registerProblem(node, tr, PyPsiBundle.message("INSP.mandatory.encoding.no.encoding.specified.for.file"),
                                 new AddEncodingQuickFix(myDefaultEncoding, myEncodingFormatIndex));
        }
      }
    }
  }

  public @NlsSafe String myDefaultEncoding = "utf-8";
  public int myEncodingFormatIndex = 0;
  public boolean myAllPythons = false;

  @Override
  public JComponent createOptionsPanel() {
    final PythonUiService uiService = PythonUiService.getInstance();
    final JPanel main = uiService.createMultipleCheckboxOptionsPanel(this);

    main.add(onlyPython2Box());
    uiService.addRowToOptionsPanel(main, new JLabel(PyPsiBundle.message("INSP.mandatory.encoding.label.select.default.encoding")), defaultEncodingBox());
    uiService.addRowToOptionsPanel(main, new JLabel(PyPsiBundle.message("INSP.mandatory.encoding.label.encoding.comment.format")), encodingFormatBox());

    return main;
  }

  @NotNull
  private JPanel onlyPython2Box() {
    final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JCheckBox checkBox =
      PythonUiService.getInstance().createInspectionCheckBox(PyPsiBundle.message("INSP.mandatory.encoding.checkbox.enable.in.python.3"), this, "myAllPythons");
    if (checkBox != null) {
      panel.add(checkBox);
    }
    return panel;
  }

  @NotNull
  private JComboBox<String> defaultEncodingBox() {
    final JComboBox<String> box = PythonUiService.getInstance().createComboBox(PyEncodingUtil.POSSIBLE_ENCODINGS);

    box.setSelectedItem(myDefaultEncoding);
    box.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        myDefaultEncoding = (String)cb.getSelectedItem();
      }
    });

    return box;
  }

  @NotNull
  private JComboBox<String> encodingFormatBox() {
    final JComboBox<String> box = PythonUiService.getInstance().createComboBox(PyEncodingUtil.ENCODING_FORMAT);

    box.setSelectedIndex(myEncodingFormatIndex);
    box.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        myEncodingFormatIndex = cb.getSelectedIndex();
      }
    });

    return box;
  }
}
