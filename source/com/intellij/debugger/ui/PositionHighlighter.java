package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.actions.ViewBreakpointsAction;
import com.intellij.debugger.engine.DebugProcessEvents;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.DebuggerColors;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointWithHighlighter;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.sun.jdi.event.Event;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jul 9, 2003
 * Time: 6:24:35 PM
 * To change this template use Options | File Templates.
 */
public class PositionHighlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.PositionHighlighter");
  private final Project myProject;
  private DebuggerContextImpl myContext = DebuggerContextImpl.EMPTY_CONTEXT;
  private SelectionDescription      mySelectionDescription = null;
  private ExecutionPointDescription myExecutionPointDescription = null;

  public PositionHighlighter(Project project, DebuggerStateManager stateManager) {
    myProject = project;

    stateManager.addListener(new DebuggerContextListener() {
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        myContext = newContext;
        refresh();
      }
    });
  }

  private void showLocationInEditor() {
    myContext.getDebugProcess().getManagerThread().invokeLater(new ShowLocationCommand(myContext));
  }

  private void refresh() {
    clearSelections();
    final DebuggerSession session = myContext.getDebuggerSession();
    if(session != null) {
      switch(session.getState()) {
        case DebuggerSession.STATE_PAUSED:
          if(myContext.getFrameProxy() != null) {
            showLocationInEditor();
            return;
          }
          break;
      }
    }
  }

  protected static class ExecutionPointDescription extends SelectionDescription {
    private RangeHighlighter myHighlighter;
    private final int myLineIndex;

    protected ExecutionPointDescription(Editor editor, int lineIndex) {
      super(editor);
      myLineIndex = lineIndex;
    }

    public void select() {
      if(myIsActive) return;
      myIsActive = true;
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      myHighlighter = myEditor.getMarkupModel().addLineHighlighter(
        myLineIndex,
        HighlighterLayer.SELECTION - 1,
        scheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES)
      );
      myHighlighter.setErrorStripeTooltip("Execution line");
    }

    public void remove() {
      if(!myIsActive) return;
      myIsActive = false;
      myEditor.getMarkupModel().removeHighlighter(myHighlighter);
      myHighlighter = null;
    }

    public RangeHighlighter getHighlighter() {
      return myHighlighter;
    }
  }

  protected abstract static class SelectionDescription {
      protected Editor myEditor;
      protected boolean myIsActive;

      public SelectionDescription(Editor editor) {
        myEditor = editor;
      }

      public abstract void select();
      public abstract void remove();

      public static ExecutionPointDescription createExecutionPoint(final Editor editor,
                                                                   final int lineIndex) {
        return new ExecutionPointDescription(editor, lineIndex);
      }

      public static SelectionDescription createSelection(final Editor editor, final int lineIndex) {
        return new SelectionDescription(editor) {
          public void select() {
            if(myIsActive) return;
            myIsActive = true;
            DocumentEx doc = (DocumentEx)editor.getDocument();
            editor.getSelectionModel().setSelection(
              doc.getLineStartOffset(lineIndex),
              doc.getLineEndOffset(lineIndex) + doc.getLineSeparatorLength(lineIndex)
            );
          }

          public void remove() {
            if(!myIsActive) return;
            myIsActive = false;
            myEditor.getSelectionModel().removeSelection();
          }
        };
      }
    }

  private void showSelection(SourcePosition position) {
    Editor editor = getEditor(position);
    if(editor == null) {
      return;
    }
    if (mySelectionDescription != null) {
      mySelectionDescription.remove();
    }
    mySelectionDescription = SelectionDescription.createSelection(editor, position.getLine());
    mySelectionDescription.select();
  }

  private void showExecutionPoint(final SourcePosition position, List<Pair<Breakpoint, Event>> events) {
    if (myExecutionPointDescription != null) {
      myExecutionPointDescription.remove();
    }
    int lineIndex = position.getLine();
    Editor editor = getEditor(position);
    if(editor == null) {
      return;
    }
    myExecutionPointDescription = SelectionDescription.createExecutionPoint(editor, lineIndex);
    myExecutionPointDescription.select();

    RangeHighlighter highlighter = myExecutionPointDescription.getHighlighter();

    if(highlighter != null) {
      final List<Pair<Breakpoint, Event>> eventsOutOfLine = new ArrayList<Pair<Breakpoint, Event>>();

      for (Iterator<Pair<Breakpoint, Event>> iterator = events.iterator(); iterator.hasNext();) {
        Pair<Breakpoint, Event> eventDescriptor = iterator.next();
        Breakpoint breakpoint = eventDescriptor.getFirst();
        if(breakpoint instanceof BreakpointWithHighlighter) {
          breakpoint.reload();
          SourcePosition sourcePosition = ((BreakpointWithHighlighter)breakpoint).getSourcePosition();
          if(sourcePosition == null || sourcePosition.getLine() != lineIndex) {
            eventsOutOfLine.add(eventDescriptor);
          }
        } else {
          eventsOutOfLine.add(eventDescriptor);
        }
      }

      if(eventsOutOfLine.size() > 0) {
        highlighter.setGutterIconRenderer(new GutterIconRenderer() {
          public Icon getIcon() {
            return eventsOutOfLine.get(0).getFirst().getIcon();
          }

          public String getTooltipText() {
            DebugProcessImpl debugProcess = DebuggerManagerEx.getInstanceEx(myProject).getContext().getDebugProcess();
            if(debugProcess != null) {
              StringBuffer buf = new StringBuffer();
              buf.append("<html><body>");
              for (Iterator<Pair<Breakpoint, Event>> iterator = eventsOutOfLine.iterator(); iterator.hasNext();) {
                Pair<Breakpoint, Event> eventDescriptor = iterator.next();
                buf.append(DebugProcessEvents.getEventText(eventDescriptor));
                if(iterator.hasNext()) {
                  buf.append("<br>");
                }
              }
              buf.append("</body></html>");
              return buf.toString();
            } else {
              return null;
            }
          }

          public ActionGroup getPopupMenuActions() {
            DefaultActionGroup group = new DefaultActionGroup();
            for (Iterator<Pair<Breakpoint, Event>> iterator = eventsOutOfLine.iterator(); iterator.hasNext();) {
              Pair<Breakpoint, Event> eventDescriptor = iterator.next();
              Breakpoint breakpoint = eventDescriptor.getFirst();
              ViewBreakpointsAction viewBreakpointsAction = new ViewBreakpointsAction(breakpoint.getDisplayName());
              viewBreakpointsAction.setInitialBreakpoint(breakpoint);
              group.add(viewBreakpointsAction);
            }

            return group;
          }
        });
      }
    }
  }

  private Editor getEditor(SourcePosition position) {
    final PsiFile psiFile = position.getFile();
    Document doc = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
    if (!psiFile.isValid()) {
      return null;
    }
    final int lineIndex = position.getLine();
    if (lineIndex < 0 || lineIndex > doc.getLineCount()) {
      //LOG.assertTrue(false, "Incorrect lineIndex " + lineIndex + " in file " + psiFile.getName());
      return null;
    }
    return position.openEditor(false);
  }

  private void clearSelections() {
    if (mySelectionDescription != null || myExecutionPointDescription != null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          if (mySelectionDescription != null) {
            mySelectionDescription.remove();
            mySelectionDescription = null;
          }
          if (myExecutionPointDescription != null) {
            myExecutionPointDescription.remove();
            myExecutionPointDescription = null;
          }
        }
      });
    }
  }


  public void updateContextPointDescription() {
    if(myContext.getDebuggerSession() == null) return;

    showLocationInEditor();
  }

  private class ShowLocationCommand extends DebuggerContextCommandImpl {
    private final DebuggerContextImpl myContext;

    public ShowLocationCommand(DebuggerContextImpl context) {
      super(context);
      myContext = context;
    }

    public void threadAction() {
      SourcePosition position = myContext.getSourcePosition();
      if (position == null) {
        return;
      }

      boolean isExecutionPoint = false;

      try {
        StackFrameProxyImpl frameProxy = myContext.getFrameProxy();
        final ThreadReferenceProxyImpl thread = getSuspendContext().getThread();
        isExecutionPoint = (thread != null)? frameProxy.equals(thread.frame(0)) : false;
      } catch(Throwable th) {
        LOG.debug(th);
      }

      final List<Pair<Breakpoint, Event>> events = DebuggerUtilsEx.getEventDescriptors(getSuspendContext());

      Document document = PsiDocumentManager.getInstance(myProject).getDocument(position.getFile());
      if(document != null) {
        if(position.getLine() < 0 || position.getLine() >= document.getLineCount()) {
          position = SourcePosition.createFromLine(position.getFile(), 0);
        }
      }

      final SourcePosition position1 = position;
      if(isExecutionPoint) {
        DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
            public void run() {
              SourcePosition position2 = updatePositionFromBreakpoint(events, position1);
              showExecutionPoint(position2, events);
            }
          });
      } else {
        DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
            public void run() {
              showSelection(position1);
            }
          });
      }
    }

    private SourcePosition updatePositionFromBreakpoint(final List<Pair<Breakpoint, Event>> events, SourcePosition position) {
      for (Iterator<Pair<Breakpoint, Event>> iterator = events.iterator(); iterator.hasNext();) {
        Pair<Breakpoint, Event> eventDescriptor = iterator.next();
        Breakpoint breakpoint = eventDescriptor.getFirst();
        if(breakpoint instanceof LineBreakpoint || breakpoint instanceof MethodBreakpoint) {
          breakpoint.reload();


          SourcePosition breakPosition = ((BreakpointWithHighlighter)breakpoint).getSourcePosition();
          if(breakPosition != null && breakPosition.getLine() != position.getLine()) {
            position = SourcePosition.createFromLine(position.getFile(), breakPosition.getLine());
          }
        }
      }
      return position;
    }
  }

}
