/*
 * Class BreakpointManager
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.actions.ViewBreakpointsAction;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.HashMap;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import gnu.trove.TIntHashSet;
import org.jdom.Element;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class BreakpointManager implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.BreakpointManager");

  private final Project myProject;
  private AnyExceptionBreakpoint myAnyExceptionBreakpoint;
  private List<Breakpoint> myBreakpoints = new ArrayList<Breakpoint>(); // breakpoints storage, access should be synchronized
  private List<Breakpoint> myBreakpointsListForIteration = null; // another list for breakpoints iteration, unsynchronized access ok
  private Map<Document, List<BreakpointWithHighlighter>> myDocumentBreakpoints = new HashMap<Document, List<BreakpointWithHighlighter>>();

  private BreakpointsConfigurationDialogFactory myBreakpointsConfigurable;
  private EditorMouseListener myEditorMouseListener;

  private final EventDispatcher<BreakpointManagerListener> myDispatcher = EventDispatcher.create(BreakpointManagerListener.class);

  private StartupManager myStartupManager;

  private final DocumentListener myDocumentListener = new DocumentAdapter() {
    Alarm myUpdateAlarm = new Alarm();

    public void documentChanged(final DocumentEvent e) {
      final Document document = e.getDocument();
      synchronized (BreakpointManager.this) {
        List<BreakpointWithHighlighter> breakpoints = myDocumentBreakpoints.get(document);

        if(breakpoints != null) {
          myUpdateAlarm.cancelAllRequests();
          // must create new array in order to avoid "concurrent modification" errors
          final List<BreakpointWithHighlighter> breakpointsToUpdate = new ArrayList<BreakpointWithHighlighter>(breakpoints);
          myUpdateAlarm.addRequest(new Runnable() {
            public void run() {
              if(!myProject.isDisposed()) {
                PsiDocumentManager.getInstance(myProject).commitDocument(document);
                update(breakpointsToUpdate);
              }
            }
          }, 300);
        }
      }
    }
  };

  private void update(List<BreakpointWithHighlighter> breakpoints) {
    final TIntHashSet intHash = new TIntHashSet();

    for (Iterator<BreakpointWithHighlighter> iterator = breakpoints.iterator(); iterator.hasNext();) {
      BreakpointWithHighlighter breakpoint = iterator.next();
      SourcePosition sourcePosition = breakpoint.getSourcePosition();
      breakpoint.reload();

      if(breakpoint.isValid()) {
        if(breakpoint.getSourcePosition().getLine() != sourcePosition.getLine()) {
          breakpointChanged(breakpoint);
        }

        if(intHash.contains(breakpoint.getLineIndex())) {
          remove(breakpoint);
        }
        else {
          intHash.add(breakpoint.getLineIndex());
        }
      }
      else {
        remove(breakpoint);
      }
    }
  }

  /*
  // todo: not needed??
  private void setInvalid(final BreakpointWithHighlighter breakpoint) {
    Collection<DebuggerSession> sessions = DebuggerManagerEx.getInstanceEx(myProject).getSessions();

    for (Iterator<DebuggerSession> iterator = sessions.iterator(); iterator.hasNext();) {
      DebuggerSession session = iterator.next();
      final DebugProcessImpl process = session.getProcess();
      process.getManagerThread().invokeLater(new DebuggerCommandImpl() {
        protected void action() throws Exception {
          process.getRequestsManager().deleteRequest(breakpoint);
          process.getRequestsManager().setInvalid(breakpoint, "Source code changed");
          breakpoint.updateUI();
        }
      });
    }
  }
  */

  private void remove(final BreakpointWithHighlighter breakpoint) {
    DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
      public void run() {
        removeBreakpoint(breakpoint);
      }
    });
  }

  public BreakpointManager(Project project, StartupManager startupManager, DebuggerManagerImpl debuggerManager) {
    myProject = project;
    myStartupManager = startupManager;
    myAnyExceptionBreakpoint = new AnyExceptionBreakpoint(project);
    debuggerManager.getContextManager().addListener(new DebuggerContextListener() {
      private DebuggerSession myPreviousSession;

      public void changeEvent(DebuggerContextImpl newContext, int event) {
        if (newContext.getDebuggerSession() != myPreviousSession || event == DebuggerSession.EVENT_DETACHED) {
          updateBreakpointsUI();
          myPreviousSession = newContext.getDebuggerSession();
        }
      }
    });
  }

  public void init() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    myEditorMouseListener = new EditorMouseAdapter() {
      private EditorMouseEvent myMousePressedEvent;

      private Breakpoint toggleBreakpoint(final boolean mostSuitingBreakpoint, int line) {
        Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
        Document document = editor.getDocument();
        PsiDocumentManager.getInstance(myProject).commitDocument(document);

        int offset = editor.getCaretModel().getOffset();
        int editorLine = editor.getDocument().getLineNumber(offset);
        if(editorLine != line) {
          offset = editor.getDocument().getLineStartOffset(line);
        }

        Breakpoint breakpoint = findBreakpoint(document, offset);
        if (breakpoint == null) {
          if(mostSuitingBreakpoint) {
            breakpoint = addFieldBreakpoint(document, offset);
            if (breakpoint == null) {
              breakpoint = addMethodBreakpoint(document, line);
            }
            if (breakpoint == null) {
              breakpoint = addLineBreakpoint(document, line);
            }
          }
          else {
            breakpoint = addLineBreakpoint(document, line);

            if (breakpoint == null) {
              breakpoint = addMethodBreakpoint(document, line);
            }
          }

          if(breakpoint != null) {
            RequestManagerImpl.createRequests(breakpoint);
          }
          return breakpoint;
        }
        else {
          removeBreakpoint(breakpoint);
          return null;
        }
      }

      private boolean isFromMyProject(Editor editor) {
        FileEditor[] allEditors = FileEditorManager.getInstance(myProject).getAllEditors();
        for (int idx = 0; idx < allEditors.length; idx++) {
          FileEditor ed = allEditors[idx];
          if (!(ed instanceof TextEditor)) {
            continue;
          }
          if (((TextEditor)ed).getEditor().equals(editor)) {
            return true;
          }
        }
        return false;
      }

      //mousePressed + mouseReleased is a hack to keep selection in editor when shift is pressed
      public void mousePressed(EditorMouseEvent e) {
        if (MarkupEditorFilterFactory.createIsDiffFilter().avaliableIn(e.getEditor())) return;

        if (e.isConsumed()) return;

        if (e.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA && e.getMouseEvent().isShiftDown()) {
          myMousePressedEvent = e;
          e.consume();
        }
      }

      public void mouseReleased(EditorMouseEvent e) {
        if(myMousePressedEvent != null) {
          mouseClicked(e);
        }
        myMousePressedEvent = null;
      }

      public void mouseClicked(final EditorMouseEvent e) {
        if (MarkupEditorFilterFactory.createIsDiffFilter().avaliableIn(e.getEditor())) return;

        if (e.isConsumed()) return;

        if (e.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
          PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Runnable() {
            public void run() {
              final Editor editor = e.getEditor();
              if (!isFromMyProject(editor)) {
                return;
              }
              final int line = editor.xyToLogicalPosition(e.getMouseEvent().getPoint()).line;
              if (line < 0) {
                return;
              }
              MouseEvent event = e.getMouseEvent();
              if (event.isPopupTrigger()) {
                return;
              }
              if (event.getButton() != 1) {
                return;
              }

              e.consume();

              DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
                public void run() {
                  Breakpoint breakpoint = toggleBreakpoint(e.getMouseEvent().isAltDown(), line);

                  if(e.getMouseEvent().isShiftDown() && breakpoint != null) {
                    breakpoint.LOG_EXPRESSION_ENABLED = true;
                    breakpoint.setLogMessage(DebuggerUtilsEx.getEditorText(editor));
                    breakpoint.SUSPEND_POLICY = DebuggerSettings.SUSPEND_NONE;

                    DialogWrapper dialog = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().createConfigurationDialog(breakpoint, BreakpointPropertiesPanel.CONTROL_LOG_MESSAGE);
                    dialog.show();

                    if(!dialog.isOK()) {
                      removeBreakpoint(breakpoint);
                    }
                  }
                }
              });
            }
          });
        }
      }
    };

    eventMulticaster.addEditorMouseListener(myEditorMouseListener);
    eventMulticaster.addDocumentListener(myDocumentListener);
  }

  public void dispose() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.removeEditorMouseListener(myEditorMouseListener);
    eventMulticaster.removeDocumentListener(myDocumentListener);
  }

  public DialogWrapper createConfigurationDialog(Breakpoint initialBreakpoint, String selectComponent) {
    if (myBreakpointsConfigurable == null) {
      myBreakpointsConfigurable = new BreakpointsConfigurationDialogFactory(myProject);
    }
    return myBreakpointsConfigurable.createDialog(initialBreakpoint, selectComponent);
  }

  public LineBreakpoint addRunToCursorBreakpoint(Document document, int lineIndex) {
    return LineBreakpoint.create(myProject, document, lineIndex, false);
  }

  public LineBreakpoint addLineBreakpoint(Document document, int lineIndex) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    if (!LineBreakpoint.canAddLineBreakpoint(myProject, document, lineIndex)) return null;

    LineBreakpoint breakpoint = LineBreakpoint.create(myProject, document, lineIndex, true);
    if (breakpoint == null) {
      return null;
    }

    addBreakpoint(breakpoint);
    return breakpoint;
  }

  public FieldBreakpoint addFieldBreakpoint(Field field, ObjectReference object) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    FieldBreakpoint fieldBreakpoint = FieldBreakpoint.create(myProject, field, object);
    if (fieldBreakpoint != null) {
      addBreakpoint(fieldBreakpoint);
    }
    return fieldBreakpoint;
  }

  public FieldBreakpoint addFieldBreakpoint(Document document, int offset) {
    PsiField field = FieldBreakpoint.findField(myProject, document, offset);
    if (field == null) return null;

    int line = document.getLineNumber(offset);

    if (document.getLineNumber(field.getNameIdentifier().getTextOffset()) < line) return null;

    return addFieldBreakpoint(document, line, field.getName());
  }

  public FieldBreakpoint addFieldBreakpoint(Document document, int lineIndex, String fieldName) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    FieldBreakpoint fieldBreakpoint = FieldBreakpoint.create(myProject, document, lineIndex, fieldName);
    if (fieldBreakpoint != null) {
      addBreakpoint(fieldBreakpoint);
    }
    return fieldBreakpoint;
  }

  public ExceptionBreakpoint addExceptionBreakpoint(String exceptionClassName) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    LOG.assertTrue(exceptionClassName != null);
    ExceptionBreakpoint breakpoint = new ExceptionBreakpoint(myProject, exceptionClassName);
    addBreakpoint(breakpoint);
    if (LOG.isDebugEnabled()) {
      LOG.debug("ExceptionBreakpoint Added");
    }
    return breakpoint;
  }

  public MethodBreakpoint addMethodBreakpoint(Document document, int lineIndex) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    MethodBreakpoint breakpoint = MethodBreakpoint.create(myProject, document, lineIndex);
    if (breakpoint == null) {
      return null;
    }
    addBreakpoint(breakpoint);
    return breakpoint;
  }

  /**
   * @return null if not found or a breakpoint object
   */
  public List<BreakpointWithHighlighter> findBreakpoints(final Document document, final int offset) {
    LinkedList<BreakpointWithHighlighter> result = new LinkedList<BreakpointWithHighlighter>();

    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (Iterator iterator = getBreakpoints().iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = (Breakpoint)iterator.next();
      if (breakpoint instanceof BreakpointWithHighlighter && ((BreakpointWithHighlighter)breakpoint).isAt(document, offset)) {
        result.add((BreakpointWithHighlighter)breakpoint);
      }
    }

    return result;
  }

  public BreakpointWithHighlighter findBreakpoint(final Document document, final int offset) {
    List<BreakpointWithHighlighter> breakpoints = findBreakpoints(document, offset);
    return breakpoints.isEmpty() ? null : breakpoints.get(0);
  }

  public LineBreakpoint findLineBreakpoint(final Document document, final int offset) {
    List<BreakpointWithHighlighter> breakpoints = findBreakpoints(document, offset);
    for (Iterator<BreakpointWithHighlighter> iterator = breakpoints.iterator(); iterator.hasNext();) {
      BreakpointWithHighlighter breakpointWithHighlighter = iterator.next();
      if(breakpointWithHighlighter instanceof LineBreakpoint) return (LineBreakpoint)breakpointWithHighlighter;
    }
    return null;
  }

  public MethodBreakpoint findMethodBreakpoint(final Document document, final int offset) {
    List<BreakpointWithHighlighter> breakpoints = findBreakpoints(document, offset);
    for (Iterator<BreakpointWithHighlighter> iterator = breakpoints.iterator(); iterator.hasNext();) {
      BreakpointWithHighlighter breakpointWithHighlighter = iterator.next();
      if(breakpointWithHighlighter instanceof MethodBreakpoint) return (MethodBreakpoint)breakpointWithHighlighter;
    }
    return null;
  }

  public FieldBreakpoint findFieldBreakpoint(final Document document, final int offset) {
    List<BreakpointWithHighlighter> breakpoints = findBreakpoints(document, offset);
    for (Iterator<BreakpointWithHighlighter> iterator = breakpoints.iterator(); iterator.hasNext();) {
      BreakpointWithHighlighter breakpointWithHighlighter = iterator.next();
      if(breakpointWithHighlighter instanceof FieldBreakpoint) return (FieldBreakpoint)breakpointWithHighlighter;
    }
    return null;
  }

  public void readExternal(final Element parentNode) throws InvalidDataException {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    myStartupManager.registerPostStartupActivity(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Runnable() {
          public void run() {
            try {
              final List groups = parentNode.getChildren();
              for (Iterator it = groups.iterator(); it.hasNext();) {
                final Element group = (Element)it.next();
                final String category = group.getName();
                Element anyExceptionBreakpointGroup = null;
                if (!AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT.equals(category)) {
                  readBreakpoints(group);
                  // for compatibility with previous format
                  anyExceptionBreakpointGroup = group.getChild(AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT);
                }
                else {
                  anyExceptionBreakpointGroup = group;
                }

                if (anyExceptionBreakpointGroup != null) {
                  final Element breakpointElement = group.getChild("breakpoint");
                  if (breakpointElement != null) {
                    myAnyExceptionBreakpoint.readExternal(breakpointElement);
                  }
                }
              }
            }
            catch (InvalidDataException e) {
            }

            DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
              public void run() {
                updateBreakpointsUI();
              }
            });            
          }
        });
      }
    });

  }
  // todo: later, when breakpoints are created via factories, inline this method

  private void readBreakpoints(final Element group) throws InvalidDataException {
    try {
      final String category = group.getName();
      Class breakpointClass = getBreakpointClass(category);
      Constructor constructor = breakpointClass.getDeclaredConstructor(new Class[] {Project.class});
      constructor.setAccessible(true);
      for (Iterator i = group.getChildren("breakpoint").iterator(); i.hasNext();) {
        Element breakpointNode = (Element)i.next();
        Breakpoint breakpoint = (Breakpoint)constructor.newInstance(new Object[] {myProject });
        breakpoint.readExternal(breakpointNode);
        addBreakpoint(breakpoint);
      }
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (InstantiationException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }
  }

  private synchronized void addBreakpoint(Breakpoint breakpoint) {
    myBreakpoints.add(breakpoint);
    myBreakpointsListForIteration = null;
    if(breakpoint instanceof BreakpointWithHighlighter) {
      BreakpointWithHighlighter breakpointWithHighlighter = ((BreakpointWithHighlighter) breakpoint);
      Document document = breakpointWithHighlighter.getDocument();
      if(document != null) {
        List<BreakpointWithHighlighter> breakpoints = myDocumentBreakpoints.get(document);

        if(breakpoints == null) {
          breakpoints = new ArrayList<BreakpointWithHighlighter>();
          myDocumentBreakpoints.put(document, breakpoints);
        }
        breakpoints.add(breakpointWithHighlighter);
      }
    }
    myDispatcher.getMulticaster().breakpointsChanged();
  }

  public synchronized void removeBreakpoint(final Breakpoint breakpoint) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    if (breakpoint == null) {
      return;
    }

    if (myBreakpoints.remove(breakpoint)) {
      myBreakpointsListForIteration = null;
      if(breakpoint instanceof BreakpointWithHighlighter) {
        //breakpoint.saveToString() may be invalid

        for (Iterator<Document> iterator = myDocumentBreakpoints.keySet().iterator(); iterator.hasNext();) {
          final Document document = iterator.next();
          final List<BreakpointWithHighlighter> documentBreakpoints = myDocumentBreakpoints.get(document);
          final boolean reallyRemoved = documentBreakpoints.remove(breakpoint);
          if (reallyRemoved) {
            if (documentBreakpoints.isEmpty()) {
              myDocumentBreakpoints.remove(document);
            }
            break;
          }
        }
      }
      //we delete breakpoints inside release, so gutter will not fire events to deleted breakpoints
      breakpoint.delete();

      myDispatcher.getMulticaster().breakpointsChanged();
    }
  }

  public void writeExternal(final Element parentNode) throws WriteExternalException {
    final WriteExternalException[] exception = new WriteExternalException[1];

    PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Runnable() {
      public void run() {
        try {
          removeInvalidBreakpoints();
          final Map<String, Element> categoryToElementMap = new java.util.HashMap<String, Element>();
          for (Iterator<Breakpoint> it = getBreakpoints().iterator(); it.hasNext(); ) {
            final Breakpoint breakpoint = it.next();
            final String category = breakpoint.getCategory();
            final Element group = getCategoryGroupElement(categoryToElementMap, category, parentNode);
            if(breakpoint.isValid()) {
              writeBreakpoint(group, breakpoint);
            }
          }
          final Element group = getCategoryGroupElement(categoryToElementMap, myAnyExceptionBreakpoint.getCategory(), parentNode);
          writeBreakpoint(group, myAnyExceptionBreakpoint);
        }
        catch (WriteExternalException e) {
          exception[0] = e;
        }
      }
    });
    if (exception[0] != null) throw exception[0];
  }

  private void writeBreakpoint(final Element group, final Breakpoint breakpoint) throws WriteExternalException {
    Element breakpointNode = new Element("breakpoint");
    group.addContent(breakpointNode);
    breakpoint.writeExternal(breakpointNode);
  }

  private Element getCategoryGroupElement(final Map<String, Element> categoryToElementMap, final String category, final Element parentNode) {
    Element group = categoryToElementMap.get(category);
    if (group == null) {
      group = new Element(category);
      categoryToElementMap.put(category, group);
      parentNode.addContent(group);
    }
    return group;
  }

  private void removeInvalidBreakpoints() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    ArrayList<Breakpoint> toDelete = new ArrayList<Breakpoint>();

    for (Iterator it = getBreakpoints().listIterator(); it.hasNext();) {
      Breakpoint breakpoint = (Breakpoint)it.next();
      if (!breakpoint.isValid()) {
        toDelete.add(breakpoint);
      }
    }

    for (Iterator<Breakpoint> iterator = toDelete.iterator(); iterator.hasNext();) {
      removeBreakpoint(iterator.next());
    }
  }

  /**
   * @return breakpoints of one of the category:
   *         LINE_BREAKPOINTS, EXCEPTION_BREKPOINTS, FIELD_BREAKPOINTS, METHOD_BREAKPOINTS
   */
  public Breakpoint[] getBreakpoints(final String category) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    removeInvalidBreakpoints();

    final ArrayList<Breakpoint> breakpoints = new ArrayList<Breakpoint>();

    for (Iterator<Breakpoint> iterator = getBreakpoints().iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      if(category.equals(breakpoint.getCategory())) {
        breakpoints.add(breakpoint);
      }
    }

    return breakpoints.toArray(new Breakpoint[breakpoints.size()]);
  }

  private Class getBreakpointClass(String category) {
    if (LineBreakpoint.LINE_BREAKPOINTS.equals(category)) {
      return LineBreakpoint.class;
    }
    if (ExceptionBreakpoint.EXCEPTION_BREAKPOINTS.equals(category)) {
      return ExceptionBreakpoint.class;
    }
    if (FieldBreakpoint.FIELD_BREAKPOINTS.equals(category)) {
      return FieldBreakpoint.class;
    }
    if (MethodBreakpoint.METHOD_BREAKPOINTS.equals(category)) {
      return MethodBreakpoint.class;
    }
    return null;
  }

  public synchronized List<Breakpoint> getBreakpoints() {
    if (myBreakpointsListForIteration == null) {
      myBreakpointsListForIteration = new ArrayList<Breakpoint>(myBreakpoints);
    }
    return myBreakpointsListForIteration;
  }

  public AnyExceptionBreakpoint getAnyExceptionBreakpoint() {
    return myAnyExceptionBreakpoint;
  }

  ActionGroup createMenuActions(final Breakpoint breakpoint) {
    /**
     * Used from Popup Menu
     */
    class RemoveAction extends AnAction {
      private Breakpoint myBreakpoint;

      public RemoveAction(Breakpoint breakpoint) {
        super("Remove");
        myBreakpoint = breakpoint;
      }

      public void actionPerformed(AnActionEvent e) {
        if (myBreakpoint != null) {
          removeBreakpoint(myBreakpoint);
          myBreakpoint = null;
        }
      }
    }

    /**
     * Used from Popup Menu
     */
    class SetEnabledAction extends AnAction {
      private boolean myNewValue;
      private Breakpoint myBreakpoint;

      public SetEnabledAction(Breakpoint breakpoint, boolean newValue) {
        super(newValue ? "Enable" : "Disable");
        myBreakpoint = breakpoint;
        myNewValue = newValue;
      }

      public void actionPerformed(AnActionEvent e) {
        myBreakpoint.ENABLED = myNewValue;
        breakpointChanged(myBreakpoint);
        myBreakpoint.updateUI();
      }
    }

      ViewBreakpointsAction viewBreakpointsAction = new ViewBreakpointsAction("Properties");
      viewBreakpointsAction.setInitialBreakpoint(breakpoint);

      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new SetEnabledAction(breakpoint, !breakpoint.ENABLED));
      group.add(new RemoveAction(breakpoint));
      group.addSeparator();
      group.add(viewBreakpointsAction);
      return group;
    }

  //interaction with RequestManagerImpl
  public void disableBreakpoints(final DebugProcessImpl debugProcess) {
    List<Breakpoint> breakpoints = getBreakpoints();

    for (Iterator<Breakpoint> iterator = breakpoints.iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      debugProcess.getRequestsManager().deleteRequest(breakpoint);
    }
  }

  public void enableBreakpoints(final DebugProcessImpl debugProcess) {
    List<Breakpoint> breakpoints = getBreakpoints();
    for (Iterator<Breakpoint> iterator = breakpoints.iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      breakpoint.createRequest(debugProcess);
    }
  }

  public void updateBreakpoints(final DebugProcessImpl debugProcess) {
    List<Breakpoint> breakpoints = getBreakpoints();
    for (Iterator<Breakpoint> iterator = breakpoints.iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      RequestManagerImpl.updateRequests(breakpoint);
    }
  }

  public void updateAllRequests() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    List<Breakpoint> breakpoints = getBreakpoints();
    for (Iterator<Breakpoint> iterator = breakpoints.iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      breakpointChanged(breakpoint);
    }
  }

  public void updateBreakpointsUI() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (Iterator<Breakpoint> iterator = getBreakpoints().iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      breakpoint.updateUI();
    }
  }

  public void reloadBreakpoints() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    for (Iterator<Breakpoint> iterator = getBreakpoints().iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      breakpoint.reload();
    }
  }

  public void addBreakpointManagerListener(BreakpointManagerListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeBreakpointManagerListener(BreakpointManagerListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void breakpointChanged(Breakpoint breakpoint) {
    RequestManagerImpl.updateRequests(breakpoint);
    myDispatcher.getMulticaster().breakpointsChanged();
  }
}
