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
package com.jetbrains.python.refactoring.classes.ui;

import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyMemberSelectionPanel extends JPanel {
  private final PyMemberSelectionTable myTable;

  public PyMemberSelectionPanel(String title, List<PyMemberInfo> memberInfo, final MemberInfoModel<PyElement, PyMemberInfo> model) {
    super();
    Border titledBorder = IdeBorderFactory.createTitledBorder(title, false);
    Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
    Border border = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
    setBorder(border);
    setLayout(new BorderLayout());

    myTable = new PyMemberSelectionTable(memberInfo, model);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);


    add(scrollPane, BorderLayout.CENTER);
  }

  public PyMemberSelectionTable getTable() {
    return myTable;
  }
}
