package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.*;
import com.intellij.debugger.actions.ViewBreakpointsAction;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.settings.DebuggerColors;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.sun.jdi.ReferenceType;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;

/**
 * User: lex
 * Date: Sep 2, 2003
 * Time: 3:22:55 PM
 */
public abstract class BreakpointWithHighlighter extends Breakpoint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.BreakpointWithHighlighter");

  private RangeHighlighter myHighlighter;

  private SourcePosition mySourcePosition;
  private long myTimeStamp;

  private boolean myVisible = true;
  private Icon myIcon = getSetIcon();
  private String myClassName = "";
  private String myInvalidMessage = "";

  protected abstract void createRequestForPreparedClass(final DebugProcessImpl debugProcess,
                                                        final ReferenceType classType);

  protected abstract Icon getDisabledIcon();

  protected abstract Icon getInvalidIcon();

  protected abstract Icon getSetIcon();

  protected abstract Icon getVerifiedIcon();

  public Icon getIcon() {
    return myIcon;
  }

  protected String getClassName() {
    return myClassName;
  }

  protected Breakpoint init() {
    if(!isValid()) {
      getDocument().getMarkupModel(myProject).removeHighlighter(myHighlighter);
      return null;
    }

    if(!ApplicationManager.getApplication().isUnitTestMode()) {
      updateUI();
      updateGutter();
    }

    return this;
  }

  private void updateCaches(DebugProcessImpl debugProcess) {
    myIcon = calcIcon(debugProcess);
    myClassName = calcClassName(debugProcess);
  }

  private String calcClassName(DebugProcessImpl debugProcess) {
    return JVMNameUtil.getClassDisplayName(debugProcess, getSourcePosition());
  }

  private Icon calcIcon(DebugProcessImpl debugProcess) {
    if (!ENABLED) {
      return getDisabledIcon();
    }

    myInvalidMessage = "";

    if (!isValid()) return getInvalidIcon();

    if(debugProcess == null){
      return getSetIcon();
    }

    RequestManagerImpl requestsManager = debugProcess.getRequestsManager();

    if(requestsManager.isVerified(this)){
      return getVerifiedIcon();
    } else if(requestsManager.isInvalid(this)){
      myInvalidMessage = requestsManager.getInvalidMessage(this);
      return getInvalidIcon();
    } else {
      return getSetIcon();
    }
  }

  protected BreakpointWithHighlighter(Project project) {
    //for persistency
    super(project);
  }

  public BreakpointWithHighlighter(final Project project, final RangeHighlighter highlighter) {
    super(project);
    myHighlighter = highlighter;
    highlighter.setEditorFilter(MarkupEditorFilterFactory.createIsNotDiffFilter());
    reload();
  }

  public RangeHighlighter getHighlighter() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    return myHighlighter;
  }

  public boolean isValid() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>(){
      public Boolean compute() {
        return new Boolean(getSourcePosition() != null && getSourcePosition().getFile().isValid());
      }
    }).booleanValue();
  }

  public SourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  public String getDescription() {
    StringBuffer buf = new StringBuffer(64);
    buf.append("<html><body>");
    buf.append(getDisplayName());
    if(!"".equals(myInvalidMessage)) {
      buf.append("<br><font color='red'>Breakpoint is invalid : " + myInvalidMessage + ".</font>");      
    }
    buf.append("&nbsp;<br>&nbsp;Suspend : ");
    if(DebuggerSettings.SUSPEND_ALL.equals(SUSPEND_POLICY)) {
      buf.append("all");
    }
    else if(DebuggerSettings.SUSPEND_THREAD.equals(SUSPEND_POLICY)) {
      buf.append("thread");
    }
    else if (DebuggerSettings.SUSPEND_NONE.equals(SUSPEND_POLICY)) {
      buf.append("none");
    } 
    buf.append("&nbsp;<br>&nbsp;Log message: ");
    buf.append(LOG_ENABLED ? "yes" : "no");
    if (LOG_EXPRESSION_ENABLED) {
      buf.append("&nbsp;<br>&nbsp;Log expression: ");
      buf.append(getLogMessage());
    }
    if (CONDITION_ENABLED && (getCondition() != null && !"".equals(getCondition()))) {
      buf.append("&nbsp;<br>&nbsp;Condition: ");
      buf.append(getCondition());
    }
    if (COUNT_FILTER_ENABLED) {
      buf.append("&nbsp;<br>&nbsp;Pass count: ");
      buf.append(COUNT_FILTER);
    }
    if (CLASS_FILTERS_ENABLED) {
      buf.append("&nbsp;<br>&nbsp;Class filters: ");
      ClassFilter[] classFilters = getClassFilters();
      for (int i = 0; i < classFilters.length; i++) {
        ClassFilter classFilter = classFilters[i];
        buf.append(classFilter.getPattern() + " ");
      }
    }
    if (INSTANCE_FILTERS_ENABLED) {
      buf.append("&nbsp;<br>&nbsp;Instance filters: ");
      InstanceFilter[] instanceFilters = getInstanceFilters();
      for (int i = 0; i < instanceFilters.length; i++) {
        InstanceFilter instanceFilter = instanceFilters[i];
        buf.append(Long.toString(instanceFilter.getId()) + " ");
      }
    }
    buf.append("</body></html>");
    return buf.toString();
  }

  public final void reload() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    if (getHighlighter().isValid()) {
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(getHighlighter().getDocument());
      if(psiFile != null) {
        mySourcePosition = SourcePosition.createFromOffset(psiFile, getHighlighter().getStartOffset());

        long modificationStamp = mySourcePosition.getFile().getModificationStamp();
        if(modificationStamp != myTimeStamp) {
          reload(psiFile);
        }
        return;
      }
    }
    mySourcePosition = null;
  }

  public void createRequest(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    // check is this breakpoint is enabled, vm reference is valid and there're no requests created yet
    if (!ENABLED || !debugProcess.isAttached() || !debugProcess.getRequestsManager().findRequests(this).isEmpty()) {
      return;
    }

    if (!isValid()) {
      return;
    }

    createOrWaitPrepare(debugProcess, getSourcePosition());
    updateUI();
  }

  public void processClassPrepare(final DebugProcess debugProcess, final ReferenceType classType) {
    if (!ENABLED || !isValid()) {
      return;
    }
    createRequestForPreparedClass((DebugProcessImpl)debugProcess, classType);
    updateUI();
  }


  /**
   * updates the state of breakpoint and all the related UI widgets etc
   */
  public final void updateUI(final Runnable afterUpdate) {
    if(ApplicationManager.getApplication().isUnitTestMode()) return;
    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        if(PsiManager.getInstance(myProject).isDisposed()) return;
        if(!isValid()) return;

        DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(myProject).getContext();
        final DebugProcessImpl debugProcess = context.getDebugProcess();

        if(debugProcess == null || !context.getDebuggerSession().isAttached()) {
          updateCaches(null);
          updateGutter();
          afterUpdate.run();
        } else {
          final ModalityState modalityState = ModalityState.current();

          debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
            protected void action() throws Exception {
              ApplicationManager.getApplication().runReadAction(new Runnable() {
                public void run() {
                  updateCaches(debugProcess);
                }
              });
              DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
                          public void run() {
                            updateGutter();
                            afterUpdate.run();
                          }
                        }, modalityState);
            }
          });
        }
      }
    }, ModalityState.defaultModalityState());
  }

  private void updateGutter() {
    if(myVisible) {
      if (getHighlighter() != null && getHighlighter().isValid() && isValid()) {
        setupGutterRenderer();
      } else {
        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().removeBreakpoint(
          BreakpointWithHighlighter.this);
      }
    }
  }

  /**
   * called by BreakpointManeger when destroying the breakpoint
   */
  public void delete() {
    if (isVisible()) {
      final RangeHighlighter highlighter = getHighlighter();
      if (highlighter != null) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
              public void run() {
                if (highlighter.isValid()) {
                  MarkupModel markupModel = highlighter.getDocument().getMarkupModel(myProject);
                  if (markupModel != null) {
                    markupModel.removeHighlighter(highlighter);
                  }
                  //we should delete it here, so gutter will not fire events to deleted breakpoint
                  BreakpointWithHighlighter.super.delete();
                }
              }
            });
      }
    }

  }

  public boolean isAt(Document document, int offset) {
    if (getHighlighter() == null || !getHighlighter().isValid()) return false;
    return (document.equals(getHighlighter().getDocument()) && getSourcePosition().getLine() == document.getLineNumber(offset));
  }

  protected void reload(PsiFile psiFile) {
    if (!(psiFile instanceof PsiJavaFile)) return;
    myTimeStamp = getSourcePosition().getFile().getModificationStamp();
  }

  public PsiClass getPsiClass() {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      public PsiClass compute() {
        return JVMNameUtil.getClassAt(getSourcePosition());
      }
    });
  }

  private void setupGutterRenderer() {
    getHighlighter().setGutterIconRenderer(new GutterIconRenderer() {
      public Icon getIcon() {
        return BreakpointWithHighlighter.this.getIcon();
      }

      public String getTooltipText() {
        return getDescription();
      }

      public AnAction getClickAction() {
        return new AnAction() {
          public void actionPerformed(AnActionEvent e) {
            DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().removeBreakpoint(BreakpointWithHighlighter.this);
          }
        };
      }

      public AnAction getMiddleButtonClickAction() {
        return new AnAction() {
          public void actionPerformed(AnActionEvent e) {
            boolean value = !ENABLED;
            ENABLED = value;
            DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager().breakpointChanged(BreakpointWithHighlighter.this);
            updateUI();
          }
        };
      }

      public ActionGroup getPopupMenuActions() {
        return createMenuActions();
      }

      public GutterDraggableObject getDraggableObject() {
        return new GutterDraggableObject() {
          public void removeSelf() {
            //DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().removeBreakpoint(BreakpointWithHighlighter.this);
          }

          public boolean copy(int line) {
            return moveTo(SourcePosition.createFromLine(getSourcePosition().getFile(), line));
          }

          public Cursor getCursor() {
            return new Cursor (Cursor.MOVE_CURSOR);
          }
        };
      }
    });
  }

  protected boolean moveTo(SourcePosition position) {
    RangeHighlighter oldHighlighter = myHighlighter;

    Document document = getDocument();
    myHighlighter = createHighlighter(myProject, document, position.getLine());

    reload();
    if(!isValid()) {
      document.getMarkupModel(myProject).removeHighlighter(myHighlighter);
      myHighlighter = oldHighlighter;
      reload();
      return false;
    }

    document.getMarkupModel(myProject).removeHighlighter(oldHighlighter);

    DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager().breakpointChanged(this);
    updateUI();

    return true;
  }

  public boolean isVisible() {
    return myVisible;
  }

  public void setVisible(boolean visible) {
    myVisible = visible;
  }

  public Document getDocument() {
    return getHighlighter().getDocument();
  }

  public int getLineIndex() {
    return getSourcePosition().getLine();
  }

  protected static RangeHighlighter createHighlighter(Project project,
                                                   Document document,
                                                   int lineIndex) {
    if (lineIndex < 0 || lineIndex >= document.getLineCount()) {
      return null;
    }

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = scheme.getAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES);

    RangeHighlighter highlighter = ((MarkupModelEx)document.getMarkupModel(project)).addPersistentLineHighlighter(
      lineIndex, HighlighterLayer.ADDITIONAL_SYNTAX + 1, attributes);
    if (!highlighter.isValid()) {
      return null;
    }
    return highlighter;
  }

  public void readExternal(Element breakpointNode) throws InvalidDataException {
    super.readExternal(breakpointNode);
    final String url = breakpointNode.getAttributeValue("url");

    VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
    if (vFile == null) throw new InvalidDataException("File number is invalid for breakpoint");
    final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
    if (doc == null) throw new InvalidDataException("File number is invalid for breakpoint");

    // line number
    final int line;
    try {
      line = Integer.parseInt(breakpointNode.getAttributeValue("line"));
    }
    catch (Exception e) {
      throw new InvalidDataException("Line number is invalid forbreakpoint");
    }
    if (line < 0) throw new InvalidDataException("Line number is invalid forbreakpoint");

    RangeHighlighter highlighter = createHighlighter(myProject, doc, line);

    if (highlighter == null) throw new InvalidDataException("");

    myHighlighter = highlighter;
    reload();
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    super.writeExternal(parentNode);
    PsiFile psiFile = getSourcePosition().getFile();
    String url = psiFile.getVirtualFile().getUrl();
    parentNode.setAttribute("url", url);
    parentNode.setAttribute("line", Integer.toString(getSourcePosition().getLine()));
  }

  private ActionGroup createMenuActions() {
    final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
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
          breakpointManager.removeBreakpoint(myBreakpoint);
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
        breakpointManager.breakpointChanged(myBreakpoint);
        myBreakpoint.updateUI();
      }
    }

      ViewBreakpointsAction viewBreakpointsAction = new ViewBreakpointsAction("Properties");
      viewBreakpointsAction.setInitialBreakpoint(this);

      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new SetEnabledAction(this, !ENABLED));
      group.add(new RemoveAction(this));
      group.addSeparator();
      group.add(viewBreakpointsAction);
      return group;
    }
}
