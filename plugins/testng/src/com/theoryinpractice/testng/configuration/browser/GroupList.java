/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.theoryinpractice.testng.configuration.browser;

import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.theoryinpractice.testng.util.TestNGUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
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
      String[] array = ArrayUtil.toStringArray(groups);
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
