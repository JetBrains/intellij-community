package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.CodeFoldingState;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.LightweightHint;
import com.intellij.util.containers.WeakList;
import org.jdom.Element;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Iterator;

public class CodeFoldingManagerImpl extends CodeFoldingManager implements ProjectComponent {
  private final Project myProject;

  private EditorFactoryListener myEditorFactoryListener;
  private EditorMouseMotionAdapter myMouseMotionListener;

  private WeakList myDocumentsWithFoldingInfo = new WeakList();

  private final Key FOLDING_INFO_IN_DOCUMENT_KEY = Key.create("FOLDING_INFO_IN_DOCUMENT_KEY");

  CodeFoldingManagerImpl(Project project) {
    myProject = project;
  }

  public String getComponentName() {
    return "CodeFoldingManagerImpl";
  }

  public void initComponent() { }

  public void disposeComponent() {
    for (Iterator iterator = myDocumentsWithFoldingInfo.iterator(); iterator.hasNext();) {
      Document document = (Document)iterator.next();
      if (document != null) {
        document.putUserData(FOLDING_INFO_IN_DOCUMENT_KEY, null);
      }
    }
  }

  public void projectOpened() {
    myEditorFactoryListener = new EditorFactoryListener() {
      public void editorCreated(EditorFactoryEvent event) {
        final Editor editor = event.getEditor();
        final Project project = editor.getProject();
        if (project != null && !project.equals(myProject)) return;

        final Document document = editor.getDocument();

        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (file == null || file instanceof XmlFile) return;
        PsiDocumentManager.getInstance(myProject).commitDocument(document);

        Runnable operation = new Runnable() {
          public void run() {
            Runnable runnable = updateFoldRegions(editor, true);
            if (runnable != null) {
              runnable.run();
            }

            DocumentFoldingInfo documentFoldingInfo = getDocumentFoldingInfo(document);
            Editor[] editors = EditorFactory.getInstance().getEditors(document, myProject);
            for (int i = 0; i < editors.length; i++) {
              Editor editor1 = editors[i];
              if (editor1 == editor) continue;
              documentFoldingInfo.loadFromEditor(editor1);
              break;
            }
            documentFoldingInfo.setToEditor(editor);

            documentFoldingInfo.clear();
          }
        };
        editor.getFoldingModel().runBatchFoldingOperation(operation);
      }

      public void editorReleased(EditorFactoryEvent event) {
        Editor editor = event.getEditor();

        final Project project = editor.getProject();
        if (project != null && !project.equals(myProject)) return;

        Document document = editor.getDocument();
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (file == null || !file.isValid()) return;
        PsiDocumentManager.getInstance(myProject).commitDocument(document);

        Editor[] otherEditors = EditorFactory.getInstance().getEditors(document, myProject);
        if (otherEditors.length == 0) {
          getDocumentFoldingInfo(document).loadFromEditor(editor);
        }
        EditorFoldingInfo.get(editor).dispose();
      }
    };

    myMouseMotionListener = new EditorMouseMotionAdapter() {
      LightweightHint myCurrentHint = null;
      FoldRegion myCurrentFold = null;

      public void mouseMoved(EditorMouseEvent e) {
        if (e.getArea() != EditorMouseEventArea.FOLDING_OUTLINE_AREA) return;
        LightweightHint hint = null;
        FoldRegion fold = null;
        try {
          Editor editor = e.getEditor();
          if (PsiDocumentManager.getInstance(myProject).isUncommited(editor.getDocument())) return;

          MouseEvent mouseEvent = e.getMouseEvent();
          fold = ((EditorEx)editor).getGutterComponentEx().findFoldingAnchorAt(mouseEvent.getX(), mouseEvent.getY());

          if (fold == null) return;
          if (fold == myCurrentFold && myCurrentHint != null) {
            hint = myCurrentHint;
            return;
          }

          PsiElement psiElement = EditorFoldingInfo.get(editor).getPsiElement(fold);
          if (psiElement == null) return;

          int textOffset = psiElement.getTextOffset();
          Point foldStartXY = editor.visualPositionToXY(editor.offsetToVisualPosition(textOffset));
          Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
          if (visibleArea.y > foldStartXY.y) {
            if (myCurrentHint != null) {
              myCurrentHint.hide();
              myCurrentHint = null;
            }
            TextRange textRange = new TextRange(textOffset, fold.getStartOffset());
            hint = EditorFragmentComponent.showEditorFragmentHint(editor, textRange, true);
            myCurrentFold = fold;
            myCurrentHint = hint;
          }
        }
        finally {
          if (hint == null) {
            if (myCurrentHint != null) {
              myCurrentHint.hide();
              myCurrentHint = null;
            }
            myCurrentFold = null;
          }
        }
      }
    };

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);
        EditorFactory.getInstance().getEventMulticaster().addEditorMouseMotionListener(myMouseMotionListener);
      }
    });
  }

  public void projectClosed() {
    EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
    EditorFactory.getInstance().getEventMulticaster().removeEditorMouseMotionListener(myMouseMotionListener);
  }

  public FoldRegion findFoldRegion(Editor editor, PsiElement element) {
    return FoldingUtil.findFoldRegion(editor, element);
  }

  public FoldRegion findFoldRegion(Editor editor, int startOffset, int endOffset) {
    return FoldingUtil.findFoldRegion(editor, startOffset, endOffset);
  }

  public FoldRegion[] getFoldRegionsAtOffset(Editor editor, int offset) {
    return FoldingUtil.getFoldRegionsAtOffset(editor, offset);
  }

  public void updateFoldRegions(Editor editor) {
    PsiDocumentManager.getInstance(myProject).commitDocument(editor.getDocument());
    Runnable runnable = updateFoldRegions(editor, false);
    if (runnable != null) {
      runnable.run();
    }
  }

  public Runnable updateFoldRegionsAsync(Editor editor) {
    return updateFoldRegions(editor, false);
  }

  private Runnable updateFoldRegions(Editor editor, boolean applyDefaultState) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
    if (file != null) {
      final PsiElement[] psiRoots = file.getPsiRoots();
      final Runnable[] cascade = new Runnable[psiRoots.length];

      for (int i = 0; i < psiRoots.length; i++) {
        final PsiElement psiRoot = psiRoots[i];
        cascade[i] = FoldingUpdate.updateFoldRegions(editor, psiRoot, applyDefaultState);
      }
      return new Runnable() {
        public void run() {
          for (int i = 0; i < cascade.length; i++) {
            final Runnable runnable = cascade[i];
            if(runnable != null) runnable.run();
          }
        }
      };
    }
    else {
      return null;
    }
  }

  public CodeFoldingState saveFoldingState(Editor editor) {
    DocumentFoldingInfo info = getDocumentFoldingInfo(editor.getDocument());
    info.loadFromEditor(editor);
    return info;
  }

  public void restoreFoldingState(Editor editor, CodeFoldingState state) {
    ((DocumentFoldingInfo)state).setToEditor(editor);
  }

  public void writeFoldingState(CodeFoldingState state, Element element) throws WriteExternalException {
    ((DocumentFoldingInfo)state).writeExternal(element);
  }

  public CodeFoldingState readFoldingState(Element element, Document document) {
    DocumentFoldingInfo info = getDocumentFoldingInfo(document);
    info.readExternal(element);
    return info;
  }

  private DocumentFoldingInfo getDocumentFoldingInfo(Document document) {
    DocumentFoldingInfo info = (DocumentFoldingInfo)document.getUserData(FOLDING_INFO_IN_DOCUMENT_KEY);
    if (info == null) {
      info = new DocumentFoldingInfo(myProject, document);
      document.putUserData(FOLDING_INFO_IN_DOCUMENT_KEY, info);
      myDocumentsWithFoldingInfo.add(document);
    }
    return info;
  }
}
