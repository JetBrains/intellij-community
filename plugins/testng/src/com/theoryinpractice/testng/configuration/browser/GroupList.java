// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration.browser;

import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtilRt;
import com.theoryinpractice.testng.util.TestNGUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Set;

public class GroupList extends JPanel
{
    private final JList list;

    public GroupList(PsiClass[] classes)
    {
        super(new BorderLayout());
        SortedListModel<String> model = new SortedListModel<>((s1, s2) -> s1.compareTo(s2));
        list = new JBList(model);
        Set<String> groups = TestNGUtil.getAnnotationValues("groups", classes);
      String[] array = ArrayUtilRt.toStringArray(groups);
        Arrays.sort(array);
        model.addAll(array);
        add(ScrollPaneFactory.createScrollPane(list));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ScrollingUtil.ensureSelectionExists(list);
    }

    public String getSelected()
    {
        return (String)list.getSelectedValue();
    }

    public static String showDialog(PsiClass[] classes, JComponent component)
    {
        GroupList groupList = new GroupList(classes);
        DialogBuilder builder = new DialogBuilder(component);
        builder.setCenterPanel(groupList);
      builder.setPreferredFocusComponent(groupList.list);
      builder.setTitle("Choose Test Group");
        return builder.show() != 0 ? null : groupList.getSelected();
    }
}
