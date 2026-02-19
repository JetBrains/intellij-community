// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.classes.ui;

import com.google.common.base.Preconditions;
import com.intellij.openapi.util.NlsContexts.BorderTitle;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import java.awt.BorderLayout;
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
   * Creates empty panel to be filled later by {@link #init(MemberInfoModel, Collection)}
   *
   */
  public PyMemberSelectionPanel(@NotNull @BorderTitle String title, boolean supportAbstract) {
    this(title, EMPTY_MEMBER_INFO, null, supportAbstract);
  }

  /**
   * Creates panel and fills its table (see {@link #init(MemberInfoModel, Collection)} ) with members info
   *
   * @param title      Title for panel
   * @param memberInfo list of members
   * @param model      model
   */
  public PyMemberSelectionPanel(@BorderTitle String title,
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
   * @param memberInfoModel model to display members in table
   * @param members         members to display
   */
  public void init(final @NotNull MemberInfoModel<PyElement, PyMemberInfo<PyElement>> memberInfoModel,
                   final @NotNull Collection<PyMemberInfo<PyElement>> members) {
    Preconditions.checkState(!myInitialized, "Already myInitialized");
    myTable.setMemberInfos(members);
    myTable.setMemberInfoModel(memberInfoModel);
    myTable.addMemberInfoChangeListener(memberInfoModel);
    myInitialized = true;
  }

  /**
   * @return list of members, selected by user
   */
  public @NotNull Collection<PyMemberInfo<PyElement>> getSelectedMemberInfos() {
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
