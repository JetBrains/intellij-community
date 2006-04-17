/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.ui.UIBundle;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.*;
import java.util.List;

/**
 * @author peter
 */
public class PsiClassControl2 extends BaseControl<PsiClassPanel, String> {
  private final boolean myCommitOnEveryChange;
  private final DocumentListener myListener = new DocumentAdapter() {
    public void documentChanged(DocumentEvent e) {
      commit();
    }
  };

  public PsiClassControl2(final DomWrapper<String> domWrapper) {
    this(domWrapper, false);
  }

  public PsiClassControl2(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper);
    myCommitOnEveryChange = commitOnEveryChange;
  }

  protected static Document getDocument(final PsiClassPanel component) {
    return getEditor(component).getEditorTextField().getDocument();
  }

  private static ReferenceEditorWithBrowseButton getEditor(final PsiClassPanel component) {
    return (ReferenceEditorWithBrowseButton)component.getComponent(0);
  }

  protected void doReset() {
    if (myCommitOnEveryChange) {
      final EditorTextField textField = getEditor(getComponent()).getEditorTextField();
      textField.getDocument().removeDocumentListener(myListener);
      super.doReset();
      textField.getDocument().addDocumentListener(myListener);
    } else {
      super.doReset();
    }
  }

  protected JComponent getComponentToListenFocusLost(final PsiClassPanel component) {
    return getEditor(component).getEditorTextField();
  }

  public static PsiClass showClassChooserDialog(DomElement element) {
    return PsiClassControl2.showClassChooserDialog(element.getManager().getProject(), element.getResolveScope());
  }

  public static PsiClass showClassChooserDialog(final Project project, final GlobalSearchScope resolveScope) {
    TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
      .createInheritanceClassChooser(UIBundle.message("choose.class"), resolveScope, null, true, true, new Condition<PsiClass>() {
        public boolean value(final PsiClass object) {
          return true;
        }
      });
    chooser.showDialog();
    return chooser.getSelectedClass();
  }

  protected JComponent getHighlightedComponent(final PsiClassPanel component) {
    return new JTextField();
  }

  protected PsiClassPanel createMainComponent(PsiClassPanel boundedComponent) {
    final Project project = getProject();
    if (boundedComponent == null) {
      boundedComponent = new PsiClassPanel();
    }
    final ReferenceEditorWithBrowseButton editor = new ReferenceEditorWithBrowseButton(null, "", PsiManager.getInstance(project), true);
    editor.getEditorTextField().setSupplementary(true);
    final GlobalSearchScope resolveScope = getDomWrapper().getResolveScope();
    editor.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final PsiClass psiClass = showClassChooserDialog(project, resolveScope);
        if (psiClass != null) {
          setValue(getComponent(), psiClass.getQualifiedName());
        }
      }
    });
    boundedComponent.add(editor);
    final PsiCodeFragment file = (PsiCodeFragment)PsiDocumentManager.getInstance(project).getPsiFile(editor.getEditorTextField().getDocument());
    file.addAnnotator(new Annotator() {
      public void annotate(PsiElement psiElement, AnnotationHolder holder) {
        if (isCommitted()) {
          for (final DomElementProblemDescriptor problem : DomElementAnnotationsManager.getInstance().getProblems(getDomElement(), true)) {
            holder.createErrorAnnotation(psiElement.getContainingFile(), problem.getDescriptionTemplate());
          }
        }
      }
    });
    return boundedComponent;
  }

  protected String getValue(final PsiClassPanel component) {
    return getEditor(component).getText();
  }

  protected void setValue(final PsiClassPanel component, final String value) {
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        getDocument(component).replaceString(0, getValue(component).length(), value == null ? "" : value);
      }
    }.execute();
  }

  protected void updateComponent() {
    final EditorTextField textField = getTextField();

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          final List<DomElementProblemDescriptor> errorProblems = DomElementAnnotationsManager.getInstance().getProblems(getDomElement(), true);
          final List<DomElementProblemDescriptor> warningProblems =
               DomElementAnnotationsManager.getInstance().getProblems(getDomElement(), true, true, HighlightSeverity.WARNING);

          Color background = getDefaultBackground();
          if (errorProblems.size() > 0 && textField.getText().trim().length() == 0) {
            background = getErrorBackground();
          }
          else if (warningProblems.size() > 0) {
            background = getWarningBackground();
          }
          textField.setBackground(background);

          //todo!!! tooltip text isn't showed
          errorProblems.addAll(warningProblems);
          textField.setToolTipText(TooltipUtils.getTooltipText(errorProblems));

          final Editor editor = textField.getEditor();
          if (editor != null && isCommitted()) {
          DaemonCodeAnalyzer.getInstance(getProject()).updateVisibleHighlighters(editor);
          }
        }
      });

  }

  private EditorTextField getTextField() {
    return getEditor(getComponent()).getEditorTextField();
  }

  public boolean canNavigate(final DomElement element) {
    return getDomElement().equals(element);
  }

  public void navigate(final DomElement element) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        getTextField().requestFocus();
        getTextField().selectAll();
      }
    });
  }
}
