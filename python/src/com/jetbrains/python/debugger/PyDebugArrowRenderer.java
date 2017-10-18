// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.reference.SoftReference;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DragSource;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class PyDebugArrowRenderer {
  // contains a lot of copy-paste from Bookmarks

  private XDebugSession mySession;
  private Reference<RangeHighlighter> myHighlighterRef;
  private int myLine;
  private VirtualFile myFile;

  public PyDebugArrowRenderer(XDebugSession session) {
    mySession = session;
  }

  public void addHighlighter() {
    final XStackFrame currentFrame = mySession.getCurrentStackFrame();
    if (currentFrame == null) return;
    XSourcePosition sourcePosition = currentFrame.getSourcePosition();
    if (sourcePosition != null) {
      myLine = sourcePosition.getLine();
      myFile = sourcePosition.getFile();
      ApplicationManager.getApplication().invokeLater(() -> {
        Document document = FileDocumentManager.getInstance().getDocument(sourcePosition.getFile());
        if (document == null) return;
        final MarkupModel model = DocumentMarkupModel.forDocument(document, mySession.getProject(), true);
        RangeHighlighter highlighter = model.addLineHighlighter(sourcePosition.getLine(), 0, null);
        myHighlighterRef = new WeakReference<>(highlighter);
        highlighter.setGutterIconRenderer(new PyDebugArrowRenderer.MyGutterIconRenderer(this));
      });
    }
  }

  private static class MyGutterIconRenderer extends GutterIconRenderer implements DumbAware {
    PyDebugArrowRenderer myArrowRenderer;

    public MyGutterIconRenderer(PyDebugArrowRenderer arrowRenderer) {
      myArrowRenderer = arrowRenderer;
    }

    @Override
    @NotNull
    public Icon getIcon() {
      return AllIcons.Vcs.Arrow_right;
    }

    @Override
    public String getTooltipText() {
      return "This is the next statement that will be executed. To change which statement is executed next, drag the arrow.";
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MyGutterIconRenderer &&
             Comparing.equal(getTooltipText(), ((MyGutterIconRenderer)obj).getTooltipText()) &&
             Comparing.equal(getIcon(), ((MyGutterIconRenderer)obj).getIcon());
    }

    @Nullable
    @Override
    public GutterDraggableObject getDraggableObject() {
      return new GutterDraggableObject() {
        @Override
        public boolean copy(int line, VirtualFile file, int actionId) {
          myArrowRenderer.updateHighlighter(file, line);
          return true;
        }

        @Override
        public Cursor getCursor(int line, int actionId) {
          return DragSource.DefaultMoveDrop;
        }
      };
    }

    @Override
    public int hashCode() {
      return getIcon().hashCode();
    }
  }

  public void updateHighlighter(VirtualFile file, int line) {
    XDebugProcess process = mySession.getDebugProcess();
    if (process instanceof PyDebugProcess) {
      PyDebugProcess debugProcess = (PyDebugProcess)process;
      XSourcePosition position = XDebuggerUtil.getInstance().createPosition(file, line);
      if (position == null) return;
      Editor editor = FileEditorManager.getInstance(mySession.getProject()).getSelectedTextEditor();
      PySetNextStatementAction.Companion.executeSetNextStatement(debugProcess, position, editor);
    }
  }

  @Nullable
  public Document getDocument() {
    return FileDocumentManager.getInstance().getCachedDocument(myFile);
  }

  public void release() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myLine < 0) {
        return;
      }
      final Document document = getDocument();
      if (document == null) return;
      MarkupModelEx markup = (MarkupModelEx)DocumentMarkupModel.forDocument(document, mySession.getProject(), true);
      final Document markupDocument = markup.getDocument();
      if (markupDocument.getLineCount() <= myLine) return;
      RangeHighlighter highlighter = findMyHighlighter();
      if (highlighter != null) {
        myHighlighterRef = null;
        highlighter.dispose();
      }
    });
  }

  private RangeHighlighter findMyHighlighter() {
    final Document document = getDocument();
    if (document == null) return null;
    RangeHighlighter result = SoftReference.dereference(myHighlighterRef);
    if (result != null) {
      return result;
    }
    MarkupModelEx markup = (MarkupModelEx)DocumentMarkupModel.forDocument(document, mySession.getProject(), true);
    final Document markupDocument = markup.getDocument();
    final int startOffset = 0;
    final int endOffset = markupDocument.getTextLength();

    final Ref<RangeHighlighter> found = new Ref<>();
    markup.processRangeHighlightersOverlappingWith(startOffset, endOffset, highlighter -> {
      GutterMark renderer = highlighter.getGutterIconRenderer();
      if (renderer instanceof PyDebugArrowRenderer.MyGutterIconRenderer) {
        found.set(highlighter);
        return false;
      }
      return true;
    });
    result = found.get();
    myHighlighterRef = result == null ? null : new WeakReference<>(result);
    return result;
  }
}
