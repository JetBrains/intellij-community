/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 18.06.2002
 * Time: 13:19:44
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.ui;

import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;

public class MemberSelectionPanel extends JPanel {
  private final MemberSelectionTable myTable;

  public MemberSelectionPanel(String title, List<MemberInfo> memberInfo, String abstractColumnHeader) {
    super();
    Border titledBorder = IdeBorderFactory.createTitledBorder(title);
    Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
    Border border = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
    setBorder(border);
    setLayout(new BorderLayout());

    myTable = new MemberSelectionTable(memberInfo, abstractColumnHeader);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);


    add(scrollPane, BorderLayout.CENTER);
  }

  public MemberSelectionTable getTable() {
    return myTable;
  }


}
