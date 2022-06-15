// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.breakpoints.dialog;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.core.util.DescriptorMemberChooserObject;
import org.jetbrains.kotlin.psi.KtProperty;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.List;

public abstract class AddFieldBreakpointDialog extends DialogWrapper {
    private final Project myProject;
    private JPanel myPanel;
    private TextFieldWithBrowseButton myFieldChooser;
    private TextFieldWithBrowseButton myClassChooser;

    public AddFieldBreakpointDialog(Project project) {
        super(project, true);
        myProject = project;
        setTitle(JavaDebuggerBundle.message("add.field.breakpoint.dialog.title"));
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        myClassChooser.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void textChanged(@NotNull DocumentEvent event) {
                updateUI();
            }
        });

        myClassChooser.addActionListener(e -> {
            PsiClass currentClass = getSelectedClass();
            TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createAllProjectScopeChooser(
                    JavaDebuggerBundle.message("add.field.breakpoint.dialog.classchooser.title"));
            if (currentClass != null) {
                PsiFile containingFile = currentClass.getContainingFile();
                if (containingFile != null) {
                    PsiDirectory containingDirectory = containingFile.getContainingDirectory();
                    if (containingDirectory != null) {
                        chooser.selectDirectory(containingDirectory);
                    }
                }
            }
            chooser.showDialog();
            PsiClass selectedClass = chooser.getSelected();
            if (selectedClass != null) {
                myClassChooser.setText(selectedClass.getQualifiedName());
            }
        });

        myFieldChooser.addActionListener(e -> {
            PsiClass selectedClass = getSelectedClass();
            if (selectedClass != null) {
                DescriptorMemberChooserObject[] properties = FieldBreakpointDialogUtilKt.collectProperties(selectedClass);
                MemberChooser<DescriptorMemberChooserObject> chooser = new MemberChooser<>(properties, false, false, myProject);
                chooser.setTitle(JavaDebuggerBundle.message("add.field.breakpoint.dialog.field.chooser.title", properties.length));
                chooser.setCopyJavadocVisible(false);
                TransactionGuard.getInstance().submitTransactionAndWait(chooser::show);
                List<DescriptorMemberChooserObject> selectedElements = chooser.getSelectedElements();
                if (selectedElements != null && selectedElements.size() == 1) {
                    KtProperty field = (KtProperty) selectedElements.get(0).getElement();
                    myFieldChooser.setText(field.getName());
                }
            }
        });
        myFieldChooser.setEnabled(false);
        return myPanel;
    }

    private void updateUI() {
        PsiClass selectedClass = getSelectedClass();
        myFieldChooser.setEnabled(selectedClass != null);
    }

    private PsiClass getSelectedClass() {
        PsiManager psiManager = PsiManager.getInstance(myProject);
        String classQName = myClassChooser.getText();
        if (StringUtil.isEmpty(classQName)) {
            return null;
        }
        return JavaPsiFacade.getInstance(psiManager.getProject()).findClass(classQName, GlobalSearchScope.allScope(myProject));
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myClassChooser.getTextField();
    }

    public String getClassName() {
        return myClassChooser.getText();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "#com.intellij.debugger.ui.breakpoints.BreakpointsConfigurationDialogFactory.BreakpointsConfigurationDialog.AddFieldBreakpointDialog";
    }

    public String getFieldName() {
        return myFieldChooser.getText();
    }

    protected abstract boolean validateData();

    @Override
    protected void doOKAction() {
        if (validateData()) {
            super.doOKAction();
        }
    }
}
