/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PsiCodeFragmentImpl;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.ui.EditorTextField;

/**
 * @author peter
 */
public class TextControl extends EditorTextFieldControl<TextPanel> {

  public TextControl(final DomWrapper<String> domWrapper) {
    super(domWrapper);
  }

  public TextControl(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  protected EditorTextField getEditorTextField(final TextPanel component) {
    return (EditorTextField)component.getComponent(0);
  }

  protected TextPanel createMainComponent(TextPanel boundedComponent, final Project project) {
    if (boundedComponent == null) {
      boundedComponent = new TextPanel();
    }
    final PsiCodeFragmentImpl psiFile = new PsiCodeFragmentImpl(project, ElementType.PLAIN_TEXT, true, "a.txt", "");
    final EditorTextField editorTextField = new EditorTextField(PsiDocumentManager.getInstance(project).getDocument(psiFile), project, StdFileTypes.PLAIN_TEXT);
    boundedComponent.add(editorTextField);
    return boundedComponent;
  }


}
