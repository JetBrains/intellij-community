/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.clientProperties;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
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
    myEditorTextField1 = new EditorTextField("", myProject, StdFileTypes.JAVA);
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    PsiPackage defaultPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage("");
    final PsiCodeFragment fragment = factory.createReferenceCodeFragment("", defaultPackage, true, true);
    myEditorTextField1.setDocument(PsiDocumentManager.getInstance(manager.getProject()).getDocument(fragment));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditorTextField1;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  public String getClassName() {
    return myEditorTextField1.getDocument().getText();
  }
}
