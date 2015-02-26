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
package com.jetbrains.python.refactoring.classes.ui;

import com.google.common.base.Preconditions;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Panel that handles table with list of class members with selection checkboxes.
 *
 * @author Dennis.Ushakov
 */
public class PyMemberSelectionPanel extends JPanel {
  private static final List<PyMemberInfo<PyElement>> EMPTY_MEMBER_INFO = Collections.emptyList();
  private final PyMemberSelectionTable myTable;
  private boolean myInitialized;


  /**
   * Creates empty panel to be filled later by {@link #init(com.intellij.refactoring.classMembers.MemberInfoModel, java.util.Collection)}
   *
   * @param title
   */
  public PyMemberSelectionPanel(@NotNull String title, boolean supportAbstract) {
    this(title, EMPTY_MEMBER_INFO, null, supportAbstract);
  }

  /**
   * Creates panel and fills its table (see {@link #init(com.intellij.refactoring.classMembers.MemberInfoModel, java.util.Collection)} ) with members info
   *
   * @param title      Title for panel
   * @param memberInfo list of members
   * @param model      model
   */
  public PyMemberSelectionPanel(String title,
                                List<PyMemberInfo<PyElement>> memberInfo,
                                final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> model,
                                final boolean supportAbstract) {
    Border titledBorder = IdeBorderFactory.createTitledBorder(title, false);
    Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
    Border border = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
    setBorder(border);
    setLayout(new BorderLayout());

    myTable = new PyMemberSelectionTable(memberInfo, model, supportAbstract);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);


    add(scrollPane, BorderLayout.CENTER);
  }


  /**
   * Inits panel.
   *
   * @param memberInfoModel model to display memebers in table
   * @param members         members to display
   */
  public void init(@NotNull final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel,
                   @NotNull final Collection<PyMemberInfo<PyElement>> members) {
    Preconditions.checkState(!myInitialized, "Already myInitialized");
    myTable.setMemberInfos(members);
    myTable.setMemberInfoModel(memberInfoModel);
    myTable.addMemberInfoChangeListener(memberInfoModel);
    myInitialized = true;
  }

  /**
   * @return list of members, selected by user
   */
  @NotNull
  public Collection<PyMemberInfo<PyElement>> getSelectedMemberInfos() {
    Preconditions.checkState(myInitialized, "Call #init first");
    return myTable.getSelectedMemberInfos();
  }

  /**
   * Redraws table. Call it when some new data is available.
   */
  public void redraw() {
    myTable.redraw();
  }
}
