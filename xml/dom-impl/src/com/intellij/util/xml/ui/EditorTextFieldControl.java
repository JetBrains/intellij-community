// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.ui;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.EditorTextField;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.highlighting.DomElementsProblemsHolder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class EditorTextFieldControl<T extends JComponent> extends BaseModifiableControl<T, String> {
  private static final JTextField J_TEXT_FIELD = new JTextField() {
    @Override
    public void addNotify() {
      throw new UnsupportedOperationException("Shouldn't be shown");
    }

    @Override
    public void setVisible(boolean aFlag) {
      throw new UnsupportedOperationException("Shouldn't be shown");
    }
  };
  private final boolean myCommitOnEveryChange;
  private final DocumentListener myListener = new DocumentListener() {
    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      setModified();
      if (myCommitOnEveryChange) {
        commit();
      }
    }
  };

  protected EditorTextFieldControl(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper);
    myCommitOnEveryChange = commitOnEveryChange;
  }


  protected EditorTextFieldControl(final DomWrapper<String> domWrapper) {
    this(domWrapper, false);
  }

  protected abstract EditorTextField getEditorTextField(@NotNull T component);

  @Override
  protected void doReset() {
    final EditorTextField textField = getEditorTextField(getComponent());
    textField.getDocument().removeDocumentListener(myListener);
    super.doReset();
    textField.getDocument().addDocumentListener(myListener);
  }

  @Override
  protected JComponent getComponentToListenFocusLost(final T component) {
    return getEditorTextField(getComponent());
  }

  @Override
  protected JComponent getHighlightedComponent(final T component) {
    return J_TEXT_FIELD;
  }

  @Override
  protected T createMainComponent(T boundedComponent) {
    final Project project = getProject();
    boundedComponent = createMainComponent(boundedComponent, project);

    final EditorTextField editorTextField = getEditorTextField(boundedComponent);
    editorTextField.setSupplementary(true);
    editorTextField.getDocument().addDocumentListener(myListener);
    return boundedComponent;
  }

  protected abstract T createMainComponent(T boundedComponent, Project project);

  @Override
  @NotNull
  protected String getValue() {
    return getEditorTextField(getComponent()).getText();
  }

  @Override
  protected void setValue(final String value) {
    CommandProcessor.getInstance().runUndoTransparentAction(() -> WriteAction.run(() -> {
      final T component = getComponent();
      final Document document = getEditorTextField(component).getDocument();
      document.replaceString(0, document.getTextLength(), value == null ? "" : value);
    }));
  }

  @Override
  protected void updateComponent() {
    final DomElement domElement = getDomElement();
    if (domElement == null || !domElement.isValid()) return;

    final EditorTextField textField = getEditorTextField(getComponent());
    final Project project = getProject();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!project.isOpen()) return;
      if (!getDomWrapper().isValid()) return;

      final DomElement domElement1 = getDomElement();
      if (domElement1 == null || !domElement1.isValid()) return;

      final DomElementAnnotationsManager manager = DomElementAnnotationsManager.getInstance(project);
      final DomElementsProblemsHolder holder = manager.getCachedProblemHolder(domElement1);
      final List<DomElementProblemDescriptor> errorProblems = holder.getProblems(domElement1);
      final List<DomElementProblemDescriptor> warningProblems =
        new ArrayList<>(holder.getProblems(domElement1, true, HighlightSeverity.WARNING));
      warningProblems.removeAll(errorProblems);

      Color background = getDefaultBackground();
      if (errorProblems.size() > 0 && textField.getText().trim().length() == 0) {
        background = getErrorBackground();
      }
      else if (warningProblems.size() > 0) {
        background = getWarningBackground();
      }

      final Editor editor = textField.getEditor();
      if (editor != null) {
        final MarkupModel markupModel = editor.getMarkupModel();
        markupModel.removeAllHighlighters();
        if (!errorProblems.isEmpty() && editor.getDocument().getLineCount() > 0) {
          markupModel.addLineHighlighter(CodeInsightColors.ERRORS_ATTRIBUTES, 0, 0);
          editor.getContentComponent().setToolTipText(errorProblems.get(0).getDescriptionTemplate());
        }
      }

      textField.setBackground(background);
    });

  }

  @Override
  public boolean canNavigate(final DomElement element) {
    return getDomElement().equals(element);
  }

  @Override
  public void navigate(final DomElement element) {
    final EditorTextField field = getEditorTextField(getComponent());
    SwingUtilities.invokeLater(() -> {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(field, true));
      field.selectAll();
    });
  }
}
