// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.breakpoints.dialog;

import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.asJava.classes.KtLightClass;
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade;
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinPsiElementMemberChooserObject;
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable;
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtProperty;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.ArrayList;

public abstract class AddFieldBreakpointDialog extends DialogWrapper {
    private final Project myProject;
    private JPanel myPanel;
    private TextFieldWithBrowseButton myFieldChooser;
    private TextFieldWithBrowseButton myClassChooser;

    public AddFieldBreakpointDialog(Project project) {
        super(project, true);
        myProject = project;
        setTitle(KotlinDebuggerCoreBundle.message("property.watchpoint.add.dialog.title"));
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
                    KotlinDebuggerCoreBundle.message("property.watchpoint.add.dialog.choose.owner.class.title"));
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
                showFieldChooser(selectedClass);
            }
        });
        myFieldChooser.setEnabled(false);
        return myPanel;
    }

    private void showFieldChooser(@NotNull PsiClass selectedClass) {
        Project project = selectedClass.getProject();

        ReadAction.nonBlocking(() -> collectPropertyMembers(selectedClass))
                .expireWith(KotlinPluginDisposable.getInstance(project))
                .finishOnUiThread(ModalityState.defaultModalityState(), (properties) -> {
                    if (properties != null) {
                        var chooser = new MemberChooser<>(properties, false, false, project);
                        chooser.setTitle(KotlinDebuggerCoreBundle.message("property.watchpoint.add.dialog.chooser.title", properties.length));
                        chooser.setCopyJavadocVisible(false);
                        chooser.show();
                        var selectedElements = chooser.getSelectedElements();
                        if (selectedElements != null && selectedElements.size() == 1) {
                            var field = (KtProperty) selectedElements.get(0).getElement();
                            myFieldChooser.setText(field.getName());
                        }
                    }
                }).submit(AppExecutorUtil.getAppExecutorService());
    }

    private static KotlinPsiElementMemberChooserObject[] collectPropertyMembers(PsiClass container) {
        var result = new ArrayList<KotlinPsiElementMemberChooserObject>();

        if (container instanceof KtLightClassForFacade) {
            var facadeClass = (KtLightClassForFacade) container;
            for (var file : facadeClass.getFiles()) {
                for (var declaration : file.getDeclarations()) {
                    ProgressManager.checkCanceled();
                    if (declaration instanceof KtProperty) {
                        result.add(KotlinPsiElementMemberChooserObject.getKotlinMemberChooserObject(declaration));
                    }
                }
            }
        } else if (container instanceof KtLightClass) {
            KtClassOrObject kotlinOrigin = ((KtLightClass) container).getKotlinOrigin();
            if (kotlinOrigin != null) {
                for (KtDeclaration declaration : kotlinOrigin.getDeclarations()) {
                    ProgressManager.checkCanceled();
                    if (declaration instanceof KtProperty) {
                        result.add(KotlinPsiElementMemberChooserObject.getKotlinMemberChooserObject(declaration));
                    }
                }
            }
        }

        return result.toArray(new KotlinPsiElementMemberChooserObject[0]);
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
