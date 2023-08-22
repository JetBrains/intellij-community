// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration.browser;

import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtilRt;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.util.TestNGUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

public class GroupList extends JPanel {
  private final JList<String> list;

  public GroupList(PsiClass[] classes) {
    super(new BorderLayout());
    SortedListModel<String> model = new SortedListModel<>(Comparator.naturalOrder());
    list = new JBList<>(model);
    Set<String> groups = TestNGUtil.getAnnotationValues("groups", classes);
    String[] array = ArrayUtilRt.toStringArray(groups);
    Arrays.sort(array);
    model.addAll(array);
    add(ScrollPaneFactory.createScrollPane(list));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    ScrollingUtil.ensureSelectionExists(list);
  }

  public String getSelected() {
    return list.getSelectedValue();
  }

  public static String showDialog(PsiClass[] classes, JComponent component) {
    GroupList groupList = new GroupList(classes);
    DialogBuilder builder = new DialogBuilder(component);
    builder.setCenterPanel(groupList);
    builder.setPreferredFocusComponent(groupList.list);
    builder.setTitle(TestngBundle.message("testng.choose.test.group"));
    return builder.show() != 0 ? null : groupList.getSelected();
  }
}
