/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ReferenceEditorWithBrowseButton;

/**
 * @author peter
 */
public class PsiTypeControl extends EditorTextFieldControl<PsiTypePanel> {

  public PsiTypeControl(final DomWrapper<String> domWrapper) {
    super(domWrapper);
  }

  public PsiTypeControl(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  protected EditorTextField getEditorTextField(final PsiTypePanel component) {
    return ((ReferenceEditorWithBrowseButton)component.getComponent(0)).getEditorTextField();
  }

  protected PsiTypePanel createMainComponent(PsiTypePanel boundedComponent, final Project project) {
    if (boundedComponent == null) {
      boundedComponent = new PsiTypePanel();
    }
    return PsiClassControl2.initReferenceEditorWithBrowseButton(boundedComponent,
                                                                new ReferenceEditorWithBrowseButton(null, project, new Factory<Document>() {
                                                                  public Document create() {
                                                                    return ReferenceEditorWithBrowseButton.createTypeDocument("", PsiManager.getInstance(project));
                                                                  }
                                                                }), this);
  }


}
