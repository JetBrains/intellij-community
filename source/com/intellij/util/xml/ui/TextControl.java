/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PsiCodeFragmentImpl;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.ui.EditorTextField;

import javax.swing.*;

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
    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    final TextPanel boundedComponent1 = boundedComponent;
    final EditorTextField editorTextField = new EditorTextField(document, project, StdFileTypes.PLAIN_TEXT) {
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        if (boundedComponent1 instanceof MultiLineTextPanel) {
          MultiLineTextPanel panel = (MultiLineTextPanel) boundedComponent1;
          editor.setOneLineMode(false);
          editor.getComponent().setPreferredSize(new JTextArea(panel.getRowCount(), 50).getPreferredSize());
        }
        return editor;
      }
    };
    boundedComponent.add(editorTextField);
    return boundedComponent;
  }


}
