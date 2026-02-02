// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.clientProperties;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;


public class ClassNameInputDialog extends DialogWrapper {
  private EditorTextField myEditorTextField1;
  private JPanel myRootPanel;
  private final Project myProject;

  public ClassNameInputDialog(Project project, Component parent) {
    super(parent, false);
    myProject = project;
    init();
    setTitle(UIDesignerBundle.message("client.properties.title"));
  }

  private void createUIComponents() {
    myEditorTextField1 = new EditorTextField("", myProject, JavaFileType.INSTANCE);
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
    PsiPackage defaultPackage = JavaPsiFacade.getInstance(myProject).findPackage("");
    final PsiCodeFragment fragment = factory.createReferenceCodeFragment("", defaultPackage, true, true);
    myEditorTextField1.setDocument(PsiDocumentManager.getInstance(myProject).getDocument(fragment));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditorTextField1;
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myRootPanel;
  }

  public String getClassName() {
    return myEditorTextField1.getDocument().getText();
  }
}
