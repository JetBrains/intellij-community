/*
 * Class MethodBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.util.text.CharArrayUtil;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;

import javax.swing.*;
import java.util.Iterator;
import java.util.Set;

import org.jetbrains.annotations.NonNls;

public class MethodBreakpoint extends BreakpointWithHighlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.MethodBreakpoint");
  public boolean WATCH_ENTRY = true;
  public boolean WATCH_EXIT  = true;

  private String myMethodName;
  private JVMName mySignature;
  private boolean myIsStatic;

  public static Icon ICON = IconLoader.getIcon("/debugger/db_method_breakpoint.png");
  public static Icon DISABLED_ICON = IconLoader.getIcon("/debugger/db_disabled_method_breakpoint.png");
  public static Icon DISABLED_DEP_ICON = IconLoader.getIcon("/debugger/db_dep_method_breakpoint.png");
  private static Icon ourInvalidIcon = IconLoader.getIcon("/debugger/db_invalid_method_breakpoint.png");
  private static Icon ourVerifiedIcon = IconLoader.getIcon("/debugger/db_verified_method_breakpoint.png");
  public static final @NonNls String CATEGORY = "method_breakpoints";

  protected MethodBreakpoint(Project project) {
    super(project);
  }

  private MethodBreakpoint(Project project, RangeHighlighter highlighter) {
    super(project, highlighter);
  }

  public boolean isStatic() {
    return myIsStatic;
  }

  public String getCategory() {
    return CATEGORY;
  }

  public PsiMethod getPsiMethod() {
    Document document = getDocument();
    if(document == null) return null;
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if(psiFile instanceof PsiJavaFile) {
      int line = getLineIndex();
      final int offset = CharArrayUtil.shiftForward(document.getCharsSequence(), document.getLineStartOffset(line), " \t");
      PsiMethod method = DebuggerUtilsEx.findPsiMethod(psiFile, offset);
      return method;
    }
    return null;
  }

  public boolean isValid() {
    return super.isValid() && myMethodName != null;
  }

  protected void reload(PsiFile psiFile) {
    myMethodName = null;
    mySignature = null;
    if(psiFile instanceof PsiJavaFile){
      MethodDescriptor descriptor = getMethodDescriptor(myProject, (PsiJavaFile)psiFile, getHighlighter().getDocument().getLineNumber(getHighlighter().getStartOffset()));
      if (descriptor != null) {
        myMethodName = descriptor.methodName;
        mySignature = descriptor.methodSignature;
        myIsStatic = descriptor.isStatic;
      }
    }
    if (myIsStatic) {
      INSTANCE_FILTERS_ENABLED = false;
    }
  }

  protected void createRequestForPreparedClass(DebugProcessImpl debugProcess,
                                               ReferenceType classType) {
    try {
      boolean hasMethod = false;
      for (Iterator iterator = classType.allMethods().iterator(); iterator.hasNext();) {
        Method method = (Method)iterator.next();
        String signature = method.signature();
        String name = method.name();

        if (myMethodName.equals(name) && mySignature.getName(debugProcess).equals(signature)) {
          hasMethod = true;
          break;
        }
      }

      if(!hasMethod) {
        debugProcess.getRequestsManager().setInvalid(
          this, DebuggerBundle.message("error.invalid.breakpoint.method.not.found", classType.name())
        );
        return;
      }

      RequestManagerImpl requestManager = debugProcess.getRequestsManager();
      if (WATCH_ENTRY) {
        MethodEntryRequest entryRequest = (MethodEntryRequest)findRequest(debugProcess, MethodEntryRequest.class);
        if (entryRequest == null) {
          entryRequest = requestManager.createMethodEntryRequest(this);
        }
        else {
          entryRequest.disable();
        }
        //entryRequest.addClassFilter(myClassQualifiedName);
        // use addClassFilter(ReferenceType) in order to stop on subclasses also!
        entryRequest.addClassFilter(classType);
        debugProcess.getRequestsManager().enableRequest(entryRequest);
      }
      if (WATCH_EXIT) {
        MethodExitRequest exitRequest = (MethodExitRequest)findRequest(debugProcess, MethodExitRequest.class);
        if (exitRequest == null) {
          exitRequest = requestManager.createMethodExitRequest(this);
        }
        else {
          exitRequest.disable();
        }
        //exitRequest.addClassFilter(myClassQualifiedName);
        exitRequest.addClassFilter(classType);
        debugProcess.getRequestsManager().enableRequest(exitRequest);
      }
    }
    catch (Exception e) {
      LOG.debug(e);
    }
  }


  public String getEventMessage(LocatableEvent event) {
    if (event instanceof MethodEntryEvent) {
      MethodEntryEvent entryEvent = (MethodEntryEvent)event;
      final Method method = entryEvent.method();
      return DebuggerBundle.message("status.method.entry.breakpoint.reached", method.declaringType().name() + "." + method.name() + "()");
    }
    else if (event instanceof MethodExitEvent) {
      MethodExitEvent exitEvent = (MethodExitEvent)event;
      final Method method = exitEvent.method();
      return DebuggerBundle.message("status.method.exit.breakpoint.reached", method.declaringType().name() + "." + method.name() + "()");
    }
    return "";
  }

  public PsiElement getEvaluationElement() {
    return getPsiClass();
  }

  protected Icon getDisabledIcon() {
    final Breakpoint master = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this);
    return master == null? DISABLED_ICON : DISABLED_DEP_ICON;
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

  public String getDisplayName() {
    StringBuffer buffer = new StringBuffer();
    if(isValid()) {
      final String className = getClassName();
      final boolean classNameExists = className != null && className.length() > 0;
      if (classNameExists) {
        buffer.append(className);
      }
      if(myMethodName != null) {
        if (classNameExists) {
          buffer.append(".");
        }
        buffer.append(myMethodName);
      }
    }
    else {
      buffer.append(DebuggerBundle.message("status.breakpoint.invalid"));
    }
    return buffer.toString();
  }

  public boolean evaluateCondition(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    Method method = null;
    if (event instanceof MethodEntryEvent) {
      MethodEntryEvent entryEvent = (MethodEntryEvent)event;
      method = entryEvent.method();
    }
    else if (event instanceof MethodExitEvent) {
      MethodExitEvent exitEvent = (MethodExitEvent)event;
      method = exitEvent.method();
    }
    if (method == null) {
      return false;
    }
    String signature = method.signature();
    String name = method.name();
    if (!(myMethodName.equals(name) && mySignature.getName(context.getDebugProcess()).equals(signature))) {
      return false;
    }
    return super.evaluateCondition(context, event);
  }

  public static MethodBreakpoint create(Project project, Document document, int lineIndex) {
    final MethodBreakpoint breakpoint = new MethodBreakpoint(project, createHighlighter(project, document, lineIndex));
    return (MethodBreakpoint)breakpoint.init();
  }


  /*
   not needed for a while
  public static MethodBreakpoint create(Method method) {
    return null;
  }
  */

  /**
   * finds FQ method's class name and method's signature
   */
  private static MethodDescriptor getMethodDescriptor(final Project project, final PsiJavaFile psiJavaFile, final int line) {
    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiJavaFile);
    if(document == null) return null;
    final int endOffset = document.getLineEndOffset(line);
    final MethodDescriptor[] descriptor = new MethodDescriptor[]{null};
    PsiDocumentManager.getInstance(project).commitAndRunReadAction(new Runnable() {
      public void run() {
        try {
          PsiMethod method = DebuggerUtilsEx.findPsiMethod(psiJavaFile, endOffset);
          if(method == null || document.getLineNumber(method.getTextOffset()) < line) return;

          int methodNameOffset = method.getNameIdentifier().getTextOffset();
          descriptor[0] = new MethodDescriptor();
          //noinspection HardCodedStringLiteral
          descriptor[0].methodName = method.isConstructor() ? "<init>" : method.getName();
          descriptor[0].methodSignature = JVMNameUtil.getJVMSignature(method);
          descriptor[0].isStatic = method.hasModifierProperty(PsiModifier.STATIC);
          descriptor[0].methodLine = document.getLineNumber(methodNameOffset);
        }
        catch (EvaluateException e) {
          descriptor[0] = null;
        }
      }
    });
    if (descriptor[0] == null) {
      return null;
    }
    if (descriptor[0].methodName == null || descriptor[0].methodSignature == null) {
      return null;
    }
    return descriptor[0];
  }

  private EventRequest findRequest(DebugProcessImpl debugProcess, Class requestClass) {
    Set reqSet = debugProcess.getRequestsManager().findRequests(this);
    for (Iterator iterator = reqSet.iterator(); iterator.hasNext();) {
      EventRequest eventRequest = (EventRequest) iterator.next();
      if(eventRequest.getClass().equals(requestClass)) {
        return eventRequest;
      }
    }

    return null;
  }

  public String toString() {
    return getDescription();
  }

  public boolean isBodyAt(Document document, int offset) {
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if(psiFile instanceof PsiJavaFile) {
      PsiMethod method = DebuggerUtilsEx.findPsiMethod(psiFile, offset);
      return method == getPsiMethod();
    }

    return false;
  }

  private static final class MethodDescriptor {
    String methodName;
    JVMName methodSignature;
    boolean isStatic;
    int methodLine;
  }
}