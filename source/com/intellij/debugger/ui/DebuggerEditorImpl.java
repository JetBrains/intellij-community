package com.intellij.debugger.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 12, 2004
 * Time: 2:46:02 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class DebuggerEditorImpl extends CompletitionEditor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerEditorImpl");

  public static final char SEPARATOR = 13;

  private final Project myProject;
  private PsiElement myContext;

  private final String myRecentsId;

  private List<DocumentListener> myDocumentListeners = new ArrayList<DocumentListener>();
  private Document myCurrentDocument;

  public DebuggerEditorImpl(Project project, PsiElement context, String recentsId) {
    myProject = project;
    myContext = context;
    myRecentsId = recentsId;
  }

  protected TextWithImportsImpl createItem(Document document, Project project) {
    if (document != null) {
      PsiDocumentManager.getInstance(project).commitDocument(document);
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      return createText(psiFile.getText(), ((PsiCodeFragment)psiFile).importsToString());
    }

    return createText("");
  }

  protected TextWithImportsImpl createText(String text) {
    return createText(text, "");
  }

  protected abstract TextWithImportsImpl createText(String text, String importsString);

  public abstract JComponent getPreferredFocusedComponent();

  public void setContext(PsiElement context) {
    TextWithImports text = getText();
    myContext = context;
    setText(text);
  }

  public PsiElement getContext() {
    return myContext;
  }

  protected Project getProject() {
    return myProject;
  }

  public void requestFocus() {
    getPreferredFocusedComponent().requestFocus();
  }

  protected Document createDocument(TextWithImportsImpl item) {
    LOG.assertTrue(myContext == null || myContext.isValid());

    if(item == null) {
      item = createText("");
    }
    PsiCodeFragment codeFragment = item.createCodeFragment(getContext(), getProject());

    if(myCurrentDocument != null) {
      for (Iterator<DocumentListener> iterator = myDocumentListeners.iterator(); iterator.hasNext();) {
        DocumentListener documentListener = iterator.next();
        myCurrentDocument.removeDocumentListener(documentListener);
      }
    }
    myCurrentDocument = PsiDocumentManager.getInstance(getProject()).getDocument(codeFragment);

    for (Iterator<DocumentListener> iterator = myDocumentListeners.iterator(); iterator.hasNext();) {
      DocumentListener documentListener = iterator.next();
      myCurrentDocument.addDocumentListener(documentListener);
    }

    return myCurrentDocument;
  }

  public String getRecentsId() {
    return myRecentsId;
  }

  public void addRecent(TextWithImportsImpl text) {
    if(getRecentsId() != null && text != null && !"".equals(text.getText())){
      DebuggerRecents.getInstance(getProject()).addRecent(getRecentsId(), text);
    }
  }

  public void addDocumentListener(DocumentListener listener) {
    myDocumentListeners.add(listener);
    if(myCurrentDocument != null) {
      myCurrentDocument.addDocumentListener(listener);
    }
  }

  public void removeDocumentListener(DocumentListener listener) {
    myDocumentListeners.remove(listener);
    if(myCurrentDocument != null) {
      myCurrentDocument.removeDocumentListener(listener);
    }
  }
}
