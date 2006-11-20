package com.theoryinpractice.testng;

import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.psi.*;
import com.intellij.ui.*;
import com.theoryinpractice.testng.util.TestNGUtil;

import java.awt.BorderLayout;
import java.util.Comparator;
import java.util.Set;
import java.util.Arrays;
import javax.swing.*;

public class GroupList extends JPanel
{
    private final JList list;

    public GroupList(PsiClass[] classes)
    {
        super(new BorderLayout());
        SortedListModel<String> model = new SortedListModel<String>(new Comparator<String>()
        {
            public int compare(String s1, String s2) {
                return s1.compareTo(s2);
            }
        });
        list = new JList(model);
        Set<String> groups = TestNGUtil.getAnnotationValues("groups", classes);
        String[] array = groups.toArray(new String[groups.size()]);
        Arrays.sort(array);
        model.addAll(array);
        add(ScrollPaneFactory.createScrollPane(list));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ListScrollingUtil.ensureSelectionExists(list);
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
        builder.setPreferedFocusComponent(groupList.list);
        builder.setTitle("Choose Test Group");
        return builder.show() != 0 ? null : groupList.getSelected();
    }
}
