/*
 * Class ExceptionBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.ExceptionRequest;
import org.jdom.Element;

import javax.swing.*;

public class ExceptionBreakpoint extends Breakpoint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.ExceptionBreakpoint");

  public boolean NOTIFY_CAUGHT   = true;
  public boolean NOTIFY_UNCAUGHT = true;
  private String myQualifiedName;

  private static Icon ourIcon = IconLoader.getIcon("/debugger/db_exception_breakpoint.png");
  private static Icon ourDisabledExceptionIcon = IconLoader.getIcon("/debugger/db_disabled_exception_breakpoint.png");
  protected final static String READ_NO_CLASS_NAME = "No class_name for exception breakpoint";

  private ExceptionBreakpoint(Project project) {
    super(project);
  }

  protected ExceptionBreakpoint(Project project, String qualifiedName) {
    super(project);
    myQualifiedName = qualifiedName;
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  public PsiClass getPsiClass() {
    return PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Computable<PsiClass>() {
      public PsiClass compute() {
        return myQualifiedName != null ? DebuggerUtilsEx.findClass(myQualifiedName, myProject) : null;
      }
    });
  }

  public String getDisplayName() {
    return "Exception breakpoint, class '" + myQualifiedName + "'";
  }

  public Icon getIcon() {
    if (!ENABLED) {
      return ourDisabledExceptionIcon;
    }
    return ourIcon;
  }

  public void reload() {
  }

  public void createRequest(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!ENABLED || !debugProcess.isAttached() || !debugProcess.getRequestsManager().findRequests(this).isEmpty()) {
      return;
    }

    SourcePosition classPosition = PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Computable<SourcePosition>() {
      public SourcePosition compute() {
        PsiClass psiClass = DebuggerUtilsEx.findClass(myQualifiedName, myProject);

        return psiClass != null ? SourcePosition.createFromElement(psiClass) : null;
      }
    });

    if(classPosition == null) {
      createOrWaitPrepare(debugProcess, myQualifiedName);
    }
    else {
      createOrWaitPrepare(debugProcess, classPosition);
    }
  }

  public void processClassPrepare(DebugProcess process, ReferenceType refType) {
    DebugProcessImpl debugProcess = (DebugProcessImpl)process;
    if (!ENABLED) {
      return;
    }
    // trying to create a request
    ExceptionRequest request = debugProcess.getRequestsManager().createExceptionRequest(this, refType, NOTIFY_CAUGHT, NOTIFY_UNCAUGHT);
    debugProcess.getRequestsManager().enableRequest(request);
    if (LOG.isDebugEnabled()) {
      if (refType != null) {
        LOG.debug("Created exception request for reference type " + refType.name());
      }
      else {
        LOG.debug("Created exception request for reference type null");
      }
    }
  }

  protected ObjectReference getThisObject(SuspendContextImpl context, LocatableEvent event) throws EvaluateException {
    if(event instanceof ExceptionEvent) {
      return ((ExceptionEvent) event).exception();
    }
    return super.getThisObject(context, event);    //To change body of overriden methods use Options | File Templates.
  }

  public String getEventMessage(LocatableEvent event) {
    String exceptionName = (myQualifiedName != null)? myQualifiedName : "java.lang.Throwable";
    String threadName    = null;
    if (event instanceof ExceptionEvent) {
      ExceptionEvent exceptionEvent = (ExceptionEvent)event;
      try {
        exceptionName = exceptionEvent.exception().type().name();
        threadName = exceptionEvent.thread().name();
      }
      catch (Exception e) {
      }
    }

    final StringBuffer message = new StringBuffer(64);
    message.append("Exception '");
    message.append(exceptionName);
    if (threadName != null) {
      message.append("' occurred in thread '");
      message.append(threadName);
      message.append('\'');
    }
    else {
      message.append("' occurred");
    }
    message.append("\n");
    message.append(message.toString());

    return message.toString();
  }

  public boolean isValid() {
    return true;
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    super.writeExternal(parentNode);
    if(getQualifiedName() != null) {
      parentNode.setAttribute("class_name", getQualifiedName());
    }
  }

  public PsiElement getEvaluationElement() {
    if(getQualifiedName() == null) return null;
    return PsiManager.getInstance(myProject).findClass(getQualifiedName(), GlobalSearchScope.allScope(myProject));
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    super.readExternal(parentNode);
    String className = parentNode.getAttributeValue("class_name");
    myQualifiedName = className;
    if(className == null) {
      throw new InvalidDataException(READ_NO_CLASS_NAME);
    }
  }

  public static ExceptionBreakpoint read(Project project, Element parentNode) throws InvalidDataException {
    ExceptionBreakpoint exceptionBreakpoint = new ExceptionBreakpoint(project);
    exceptionBreakpoint.readExternal(parentNode);
    return exceptionBreakpoint;
  }

}