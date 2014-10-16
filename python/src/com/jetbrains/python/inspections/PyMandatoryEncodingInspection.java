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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.inspections.quickfix.AddEncodingQuickFix;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFile(PyFile node) {
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

  @Override
  public JComponent createOptionsPanel() {
    final JComboBox defaultEncoding = new JComboBox(PyEncodingUtil.POSSIBLE_ENCODINGS);
    defaultEncoding.setSelectedItem(myDefaultEncoding);

    defaultEncoding.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        myDefaultEncoding = (String)cb.getSelectedItem();
      }
    });

    final JComboBox encodingFormat = new JComboBox(PyEncodingUtil.ENCODING_FORMAT);

    encodingFormat.setSelectedIndex(myEncodingFormatIndex);
    encodingFormat.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        myEncodingFormatIndex = cb.getSelectedIndex();
      }
    });

    return PyEncodingUtil.createEncodingOptionsPanel(defaultEncoding, encodingFormat);
  }
}
