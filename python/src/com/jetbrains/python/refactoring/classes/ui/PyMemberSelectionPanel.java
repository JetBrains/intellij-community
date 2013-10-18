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
