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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.ui.CheckBox;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.inspections.quickfix.AddEncodingQuickFix;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.Nls;
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
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.mandatory.encoding");
  }

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
    public void visitPyFile(PyFile node) {
      if (!(myAllPythons || LanguageLevel.forElement(node).isPython2())) return;

      final String charsetString = PythonFileType.getCharsetFromEncodingDeclaration(node);
      if (charsetString == null) {
        TextRange tr = new TextRange(0,0);
        ProblemsHolder holder = getHolder();
        if (holder != null)
          holder.registerProblem(node, tr, "No encoding specified for file", new AddEncodingQuickFix(myDefaultEncoding,
                                                                                                     myEncodingFormatIndex));
      }
    }
  }

  public String myDefaultEncoding = "utf-8";
  public int myEncodingFormatIndex = 0;
  public boolean myAllPythons = false;

  @Override
  public JComponent createOptionsPanel() {
    final JPanel main = new JPanel(new GridBagLayout());

    main.add(onlyPython2Box(), fixedIn(0));
    main.add(defaultEncodingLabel(), fixedIn(1));
    main.add(defaultEncodingBox(), resizableIn(1));
    main.add(encodingFormatLabel(), fixedIn(2));
    main.add(encodingFormatBox(), resizableIn(2));

    final JPanel result = new JPanel(new BorderLayout());
    result.add(main, BorderLayout.NORTH);
    return result;
  }

  @NotNull
  private static GridBagConstraints fixedIn(int y) {
    final GridBagConstraints c = new GridBagConstraints();

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE; // do not resize
    c.weightx = 0; // do not give extra horizontal space
    c.gridx = 0;
    c.gridy = y;

    return c;
  }

  @NotNull
  private static GridBagConstraints resizableIn(int y) {
    final GridBagConstraints c = new GridBagConstraints();

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL; // resize horizontally
    c.weightx = 1; // give extra horizontal space
    c.gridx = 1;
    c.gridy = y;

    return c;
  }

  @NotNull
  private JPanel onlyPython2Box() {
    final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    panel.add(new CheckBox("Enable in Python 3+", this, "myAllPythons"));
    return panel;
  }

  @NotNull
  private static JPanel defaultEncodingLabel() {
    final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    panel.add(new JLabel("Select default encoding: "));
    return panel;
  }

  @NotNull
  private JComboBox<String> defaultEncodingBox() {
    final JComboBox<String> box = new ComboBox<>(PyEncodingUtil.POSSIBLE_ENCODINGS);

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
  private static JPanel encodingFormatLabel() {
    final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    panel.add(new JLabel("Encoding comment format:"));
    return panel;
  }

  @NotNull
  private JComboBox<String> encodingFormatBox() {
    final JComboBox<String> box = new ComboBox<>(PyEncodingUtil.ENCODING_FORMAT, 250);

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
