/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.structureView.impl.StructureNodeRenderer;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.theoryinpractice.testng.util.TestNGUtil;

import java.awt.BorderLayout;
import java.util.Comparator;
import javax.swing.*;

public class MethodList extends JPanel
{
    private final SortedListModel<PsiMethod> model;
    private final JList list;
    private final PsiClass psiClass;

    private static final Comparator<PsiMethod> comparator = new Comparator<PsiMethod>() {

        public int compare(PsiMethod method1, PsiMethod method2)
        {
            return method1.getName().compareToIgnoreCase(method2.getName());
        }
    };

    public static class TestMethodFilter implements Condition<PsiMethod>
    {
        public boolean value(PsiMethod method)
        {
            return TestNGUtil.hasTest(method);
        }
    }
    
    public MethodList(PsiClass psiClass)
    {
        super(new BorderLayout());
        model = new SortedListModel<PsiMethod>(comparator);
        list = new JBList(model);
        this.psiClass = psiClass;
        evaluate(psiClass.getAllMethods(), new TestMethodFilter());
        add(ScrollPaneFactory.createScrollPane(list));
        list.setCellRenderer(new ColoredListCellRenderer() {

            @Override
            protected void customizeCellRenderer(JList jlist, Object obj, int i, boolean flag, boolean flag1)
            {
                PsiMethod psimethod = (PsiMethod)obj;
                append(PsiFormatUtil.formatMethod(psimethod, PsiSubstitutor.EMPTY, 1, 0), StructureNodeRenderer.applyDeprecation(psimethod, SimpleTextAttributes.REGULAR_ATTRIBUTES));
                PsiClass psiclass1 = psimethod.getContainingClass();
                if(!MethodList.this.psiClass.equals(psiclass1)) {
                    append(" (" + psiclass1.getQualifiedName() + ')', StructureNodeRenderer.applyDeprecation(psiclass1, SimpleTextAttributes.GRAY_ATTRIBUTES));
                }
            }
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ListScrollingUtil.ensureSelectionExists(list);
    }

    private void evaluate(PsiMethod methods[], Condition<PsiMethod> condition)
    {
        for(PsiMethod psimethod : methods) {
            if(condition.value(psimethod)) model.add(psimethod);
        }

    }

    public PsiMethod getSelected()
    {
        return (PsiMethod)list.getSelectedValue();
    }

    public static PsiMethod showDialog(PsiClass psiClass, JComponent component)
    {
        MethodList list = new MethodList(psiClass);
        DialogBuilder builder = new DialogBuilder(component);
        builder.setCenterPanel(list);
        builder.setPreferedFocusComponent(list.list);
        builder.setTitle("Choose Test Method");
        return builder.show() != 0 ? null : list.getSelected();
    }
}
