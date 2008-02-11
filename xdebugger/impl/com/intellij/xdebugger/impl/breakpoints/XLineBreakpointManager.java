package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xdebugger.XDebuggerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.Collection;

/**
 * @author nik
 */
public class XLineBreakpointManager {
  private BidirectionalMap<XLineBreakpointImpl, Document> myBreakpoints = new BidirectionalMap<XLineBreakpointImpl, Document>();
  private MergingUpdateQueue myBreakpointsUpdateQueue = new MergingUpdateQueue("XLine breakpoints", 300, true, null);
  private DocumentAdapter myDocumentListener;
  private final Project myProject;
  private final StartupManagerEx myStartupManager;
  private EditorMouseAdapter myEditorMouseListener;

  public XLineBreakpointManager(Project project, final StartupManager startupManager) {
    myProject = project;
    myStartupManager = (StartupManagerEx)startupManager;

    if (!myProject.isDefault()) {
      myDocumentListener = new MyDocumentListener();
      myEditorMouseListener = new MyEditorMouseListener();

      EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
      editorEventMulticaster.addDocumentListener(myDocumentListener);
      editorEventMulticaster.addEditorMouseListener(myEditorMouseListener);
    }
  }

  public void updateBreakpointsUI() {
    if (myProject.isDefault()) return;

    Runnable runnable = new Runnable() {
      public void run() {
        for (XLineBreakpointImpl breakpoint : myBreakpoints.keySet()) {
          breakpoint.updateUI();
        }
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode() || myStartupManager.startupActivityPassed()) {
      runnable.run();
    }
    else {
      myStartupManager.registerPostStartupActivity(runnable);
    }
  }

  public void registerBreakpoint(XLineBreakpointImpl breakpoint, final boolean initUI) {
    if (initUI) {
      breakpoint.updateUI();
    }
    Document document = breakpoint.getDocument();
    if (document != null) {
      myBreakpoints.put(breakpoint, document);
    }
  }

  public void unregisterBreakpoint(final XLineBreakpointImpl breakpoint) {
    RangeHighlighter highlighter = breakpoint.getHighlighter();
    if (highlighter != null) {
      myBreakpoints.remove(breakpoint);
    }
  }

  private void updateBreakpoints(final Document document) {
    Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(document);
    if (breakpoints == null) return;
    for (XLineBreakpointImpl breakpoint : breakpoints) {
      breakpoint.updatePosition();
    }
  }

  public void dispose() {
    if (!myProject.isDefault()) {
      EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
      editorEventMulticaster.removeDocumentListener(myDocumentListener);
      editorEventMulticaster.removeEditorMouseListener(myEditorMouseListener);
    }
    myBreakpointsUpdateQueue.dispose();
  }

  public void breakpointChanged(final XLineBreakpointImpl breakpoint) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    breakpoint.updateUI();
  }

  public void queueBreakpointUpdate(@NotNull final XLineBreakpointImpl<?> breakpoint) {
    myBreakpointsUpdateQueue.queue(new Update(breakpoint) {
      public void run() {
        breakpoint.updateUI();
      }
    });
  }

  public void queueAllBreakpointsUpdate() {
    myBreakpointsUpdateQueue.queue(new Update("all breakpoints") {
      public void run() {
        for (XLineBreakpointImpl breakpoint : myBreakpoints.keySet()) {
          breakpoint.updateUI();
        }
      }
    });
  }

  private class MyDocumentListener extends DocumentAdapter {
    public void documentChanged(final DocumentEvent e) {
      final Document document = e.getDocument();
      Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(document);
      if (breakpoints != null && !breakpoints.isEmpty()) {
        myBreakpointsUpdateQueue.queue(new Update(document) {
          public void run() {
            updateBreakpoints(document);
          }
        });
      }
    }
  }

  private class MyEditorMouseListener extends EditorMouseAdapter {
    public void mouseClicked(final EditorMouseEvent e) {
      final Editor editor = e.getEditor();
      final MouseEvent mouseEvent = e.getMouseEvent();
      if (mouseEvent.isPopupTrigger() ||
          mouseEvent.getButton() != MouseEvent.BUTTON1 ||
          MarkupEditorFilterFactory.createIsDiffFilter().avaliableIn(editor) ||
          e.getArea() != EditorMouseEventArea.LINE_MARKERS_AREA) {
        return;
      }

      PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Runnable() {
        public void run() {
          final int line = editor.xyToLogicalPosition(mouseEvent.getPoint()).line;
          final Document document = editor.getDocument();
          if (line >= 0 && line < document.getLineCount()) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                VirtualFile file = FileDocumentManager.getInstance().getFile(document);
                XDebuggerUtil.getInstance().toggleLineBreakpoint(myProject, file, line);
              }
            });
          }
        }
      });
    }
  }
}
