/*
 * Class LineBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.ClassFilter;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.BreakpointRequest;

import javax.swing.*;
import java.util.List;

public class LineBreakpoint extends BreakpointWithHighlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.LineBreakpoint");

  // icons
  public static Icon ICON = IconLoader.getIcon("/gutter/db_set_breakpoint.png");
  public static final Icon DISABLED_ICON = IconLoader.getIcon("/gutter/db_disabled_breakpoint.png");
  private static Icon ourInvalidIcon = IconLoader.getIcon("/gutter/db_invalid_breakpoint.png");
  private static Icon ourVerifiedIcon = IconLoader.getIcon("/gutter/db_verified_breakpoint.png");

  private String myMethodName;
  public static final String CATEGORY = "line_breakpoints";

  protected LineBreakpoint(Project project) {
    super(project);
  }

  private LineBreakpoint(Project project, RangeHighlighter highlighter) {
    super(project, highlighter);
  }

  protected Icon getDisabledIcon() {
    return DISABLED_ICON;
  }

  protected Icon getSetIcon() {
    return ICON;
  }

  protected Icon getInvalidIcon() {
    return ourInvalidIcon;
  }

  protected Icon getVerifiedIcon() {
    return ourVerifiedIcon;
  }

  public String getCategory() {
    return CATEGORY;
  }

  protected void reload(PsiFile file) {
    super.reload(file);
    myMethodName = LineBreakpoint.findMethodName(file, getHighlighter().getStartOffset());
  }

  protected void createRequestForPreparedClass(final DebugProcessImpl debugProcess, final ReferenceType classType) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          List<Location> locs = debugProcess.getPositionManager().locationsOfLine(classType, getSourcePosition());
          Location location = locs.size() > 0 ? locs.get(0) : null;
          if (location != null) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Found location for reference type " + classType.name() + " at line " + getLineIndex() + "; isObsolete: " + (debugProcess.getVirtualMachineProxy().versionHigher("1.4") && location.method().isObsolete()));
            }
            BreakpointRequest request = debugProcess.getRequestsManager().createBreakpointRequest(LineBreakpoint.this, location);
            debugProcess.getRequestsManager().enableRequest(request);
            if (LOG.isDebugEnabled()) {
              LOG.debug("Created breakpoint request for reference type " + classType.name() + " at line " + getLineIndex());
            }
          }
          else {
            // there's no executable code in this class
            debugProcess.getRequestsManager().setInvalid(LineBreakpoint.this, "No executable code found at line " + (getLineIndex()  + 1) + " in class " + classType.name());
            if (LOG.isDebugEnabled()) {
              LOG.debug("No locations of type " + classType.name() + " found at line " + getLineIndex());
            }
          }
        }
        catch (ClassNotPreparedException ex) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("ClassNotPreparedException: " + ex.getMessage());
          }
          // there's a chance to add a breakpoint when the class is prepared
        }
        catch (ObjectCollectedException ex) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("ObjectCollectedException: " + ex.getMessage());
          }
          // there's a chance to add a breakpoint when the class is prepared
        }
        catch (InvalidLineNumberException ex) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("InvalidLineNumberException: " + ex.getMessage());
          }
          debugProcess.getRequestsManager().setInvalid(LineBreakpoint.this, "Line number is invalid");
        }
        catch (InternalException ex) {
          LOG.info(ex);
        }
        catch(Exception ex) {
          LOG.info(ex);
        }
        updateUI();
      }
    });
  }

  public boolean evaluateCondition(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    if(CLASS_FILTERS_ENABLED){
      Value value = context.getThisObject();
      ObjectReference thisObject = (ObjectReference)value;
      if(thisObject == null) {
        return false;
      }
      String name = DebuggerUtilsEx.getQualifiedClassName(thisObject.referenceType().name(), getProject());
      if(name == null) {
        return false;
      }
      ClassFilter [] filters = getClassFilters();
      boolean matches = false;
      for (int i = 0; i < filters.length; i++) {
        ClassFilter classFilter = filters[i];
        if(classFilter.isEnabled() && classFilter.matches(name)) {
          matches = true;
          break;
        }
      }
      if(!matches) {
        return false;
      }
      
      ClassFilter [] ifilters = getClassExclusionFilters();
      for (int i = 0; i < ifilters.length; i++) {
        ClassFilter classFilter = ifilters[i];
        if(classFilter.isEnabled() && classFilter.matches(name)) {
          return false;
        }
      }
    }
    return super.evaluateCondition(context, event);
  }

  public String toString() {
    return getDescription();
  }


  public String getDisplayName() {
    StringBuffer buffer = new StringBuffer();
    final int lineNumber = (getHighlighter().getDocument().getLineNumber(getHighlighter().getStartOffset()) + 1);
    if(isValid()) {
      buffer.append("Line ").append(lineNumber).append(", in ").append(getClassName());
      if(myMethodName != null) {
        buffer.append(".");
        buffer.append(myMethodName);
      }
    } 
    else {
      buffer.append("INVALID");
    }
    return buffer.toString();
  }

  private static String findMethodName(final PsiFile file, final int offset) {
    if (!(file instanceof PsiJavaFile)) {
      return null;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)file;
    final String[] rv = new String[]{""};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        PsiMethod method = DebuggerUtilsEx.findPsiMethod(javaFile, offset);
        if (method != null) {
          rv[0] = method.getName() + "()";
        }
      }
    });
    return rv[0];
  }

  public String getEventMessage(LocatableEvent event) {
    final StringBuffer buf = new StringBuffer(64);
    buf.append("Reached breakpoint");
    String name = event.location().declaringType().name();
    buf.append(" in class ");
    buf.append(name);
    buf.append(",");
    buf.append(" at line ");
    buf.append(getLineIndex() + 1);
    return buf.toString();
  }

  public PsiElement getEvaluationElement() {
    return PositionUtil.getContextElement(getSourcePosition());
  }

  protected static LineBreakpoint create(Project project, Document document, int lineIndex, boolean isVisible) {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null) return null;

    LineBreakpoint breakpoint = new LineBreakpoint(project, createHighlighter(project, document, lineIndex));

    if(!isVisible) {
      document.getMarkupModel(project).removeHighlighter(breakpoint.getHighlighter());
    }

    breakpoint.setVisible(isVisible);

    return (LineBreakpoint)breakpoint.init();
  }

  public boolean canMoveTo(SourcePosition position) {
    if (!canAddLineBreakpoint(myProject, getDocument(), position.getLine())) {
      return false;
    }
    return super.canMoveTo(position);
  }
  
  public static boolean canAddLineBreakpoint(Project project, final Document document, final int lineIndex) {
    if (lineIndex < 0 || lineIndex >= document.getLineCount()) {
      return false;
    }
    final BreakpointWithHighlighter breakpointAtLine = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().findBreakpoint(
      document,
      document.getLineStartOffset(lineIndex)
    );
    if (breakpointAtLine != null && CATEGORY.equals(breakpointAtLine.getCategory())) {
      // there already exists a line breakpoint at this line
      return false;
    }
    final boolean[] canAdd = new boolean[]{false};

    PsiDocumentManager.getInstance(project).commitDocument(document);

    DebuggerUtilsEx.iterateLine(project, document, lineIndex, new DebuggerUtilsEx.ElementVisitor() {
      public boolean acceptElement(PsiElement element) {
        if ((element instanceof PsiWhiteSpace) || (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null)) {
          return false;
        }
        PsiElement child = element;
        while(element != null) {

          if (document.getLineNumber(element.getTextOffset()) != lineIndex) {
            break;
          }
          child = element;
          element = element.getParent();
        }

        if(child instanceof PsiMethod && child.getTextRange().getEndOffset() >= document.getLineEndOffset(lineIndex)) {
          PsiCodeBlock body = ((PsiMethod)child).getBody();
          if(body == null) {
            canAdd[0] = false;
          }
          else {
            PsiStatement[] statements = body.getStatements();
            canAdd[0] = statements.length > 0 && document.getLineNumber(statements[0].getTextOffset()) == lineIndex;
          }
        }
        else {
          canAdd[0] = true;
        }
        return true;
      }
    });

    return canAdd[0];
  }
}
