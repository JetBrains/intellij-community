package com.intellij.debugger.engine.requests;

import com.intellij.debugger.ClassFilter;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.requests.RequestManager;
import com.intellij.debugger.requests.Requestor;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.util.containers.HashMap;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.sun.jdi.*;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.*;

import java.util.*;

/**
 * @author lex
 * Date: May 6, 2003
 * Time: 5:32:38 PM
 */
public class RequestManagerImpl extends DebugProcessAdapterImpl implements RequestManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.RequestManagerImpl");

  private static final Key CLASS_NAME = Key.create("ClassName");

  private DebugProcessImpl myDebugProcess;
  private HashMap<Requestor, String> myInvalidRequestors = new HashMap<Requestor, String>();

  private Map<Requestor, Set<EventRequest>> myRequestorToBelongedRequests        = new com.intellij.util.containers.HashMap<Requestor, Set<EventRequest>>();
  private Map<EventRequest, Requestor>      myRequestsToProcessingRequestor     = new HashMap<EventRequest, Requestor>();
  private EventRequestManager myEventRequestManager;

  public RequestManagerImpl(DebugProcessImpl debugProcess) {
    myDebugProcess = debugProcess;
    myDebugProcess.addDebugProcessListener(this);
  }


  public Set findRequests(Requestor requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!myRequestorToBelongedRequests.containsKey(requestor)) {
      return Collections.EMPTY_SET;
    }
    return Collections.unmodifiableSet((Set) myRequestorToBelongedRequests.get(requestor));
  }

  public Requestor findRequestor(EventRequest request) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myRequestsToProcessingRequestor.get(request);
  }

  private void addClassFilter(EventRequest request, String pattern){
    if(request instanceof AccessWatchpointRequest){
      ((AccessWatchpointRequest) request).addClassFilter(pattern);
    }
    else if(request instanceof ExceptionRequest){
      ((ExceptionRequest) request).addClassFilter(pattern);
    }
    else if(request instanceof MethodEntryRequest) {
      ((MethodEntryRequest)request).addClassFilter(pattern);
    }
    else if(request instanceof MethodExitRequest) {
      ((MethodExitRequest)request).addClassFilter(pattern);
    }
    else if(request instanceof ModificationWatchpointRequest) {
      ((ModificationWatchpointRequest)request).addClassFilter(pattern);
    }
    else if(request instanceof WatchpointRequest) {
      ((WatchpointRequest)request).addClassFilter(pattern);
    }
  }

  private void addClassExclusionFilter(EventRequest request, String pattern){
    if(request instanceof AccessWatchpointRequest){
      ((AccessWatchpointRequest) request).addClassExclusionFilter(pattern);
    }
    else if(request instanceof ExceptionRequest){
      ((ExceptionRequest) request).addClassExclusionFilter(pattern);
    }
    else if(request instanceof MethodEntryRequest) {
      ((MethodEntryRequest)request).addClassExclusionFilter(pattern);
    }
    else if(request instanceof MethodExitRequest) {
      ((MethodExitRequest)request).addClassExclusionFilter(pattern);
    }
    else if(request instanceof ModificationWatchpointRequest) {
      ((ModificationWatchpointRequest)request).addClassExclusionFilter(pattern);
    }
    else if(request instanceof WatchpointRequest) {
      ((WatchpointRequest)request).addClassExclusionFilter(pattern);
    }
  }

  private void addLocatableRequest(Breakpoint requestor, EventRequest request) {
    if(DebuggerSettings.SUSPEND_ALL.equals(requestor.SUSPEND_POLICY)) {
      request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
    }
    else {
      //when requestor.SUSPEND_POLICY == SUSPEND_NONE
      //we should pause thread in order to evaluate conditions
      request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
    }

    if (requestor.COUNT_FILTER_ENABLED) {
      request.addCountFilter(requestor.COUNT_FILTER);
    }

    if (requestor.CLASS_FILTERS_ENABLED) {
      ClassFilter[] classFilters = requestor.getClassFilters();
      for (int idx = 0; idx < classFilters.length; idx++) {
        final ClassFilter filter = classFilters[idx];
        if (!filter.isEnabled()) {
          continue;
        }
        final JVMName jvmClassName = ApplicationManager.getApplication().runReadAction(new Computable<JVMName>() {
          public JVMName compute() {
            PsiClass psiClass = DebuggerUtilsEx.findClass(filter.getPattern(), myDebugProcess.getProject());
            if(psiClass == null) {
              return null;
            }
            return JVMNameUtil.getJVMQualifiedName(psiClass);
          }
        });
        String pattern = filter.getPattern();
        try {
          if(jvmClassName != null) {
            pattern = jvmClassName.getName(myDebugProcess);
          }
        }
        catch (EvaluateException e) {
        }

        addClassFilter(request, pattern);
      }

      final ClassFilter[] iclassFilters = requestor.getClassExclusionFilters();
      for (int idx = 0; idx < iclassFilters.length; idx++) {
        ClassFilter filter = iclassFilters[idx];
        if (filter.isEnabled()) {
          addClassExclusionFilter(request, filter.getPattern());
        }
      }
    }

    belongsToRequestor(requestor, request);
    callbackOnEvent(requestor, request);
  }

  private void belongsToRequestor(Requestor requestor, EventRequest request) {
    Set<EventRequest> reqSet = myRequestorToBelongedRequests.get(requestor);
    if(reqSet == null) {
      reqSet = new HashSet<EventRequest>();
      myRequestorToBelongedRequests.put(requestor, reqSet);
    }
    reqSet.add(request);

  }

  private void callbackOnEvent(Requestor requestor, EventRequest request) {
    myRequestsToProcessingRequestor.put(request, requestor);
  }

  // requests creation
  public ClassPrepareRequest createClassPrepareRequest(ClassPrepareRequestor requestor, String pattern) {
    ClassPrepareRequest classPrepareRequest = myEventRequestManager.createClassPrepareRequest();
    classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
    classPrepareRequest.addClassFilter(pattern);
    classPrepareRequest.putProperty(CLASS_NAME, pattern);

    belongsToRequestor(requestor, classPrepareRequest);
    callbackOnEvent(requestor, classPrepareRequest);
    return classPrepareRequest;
  }

  public ExceptionRequest createExceptionRequest(Breakpoint requestor, ReferenceType referenceType, boolean notifyCaught, boolean notifyUnCaught) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ExceptionRequest req = myEventRequestManager.createExceptionRequest(referenceType, notifyCaught, notifyUnCaught);
    addLocatableRequest(requestor, req);
    return req;
  }

  public MethodEntryRequest createMethodEntryRequest(Breakpoint requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    MethodEntryRequest req = myEventRequestManager.createMethodEntryRequest();
    addLocatableRequest(requestor, req);
    return req;
  }

  public MethodExitRequest createMethodExitRequest(Breakpoint requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    MethodExitRequest req = myEventRequestManager.createMethodExitRequest();
    addLocatableRequest(requestor, req);
    return req;
  }

  public BreakpointRequest createBreakpointRequest(Breakpoint requestor, Location location) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    BreakpointRequest req = myEventRequestManager.createBreakpointRequest(location);
    addLocatableRequest(requestor, req);
    return req;
  }

  public AccessWatchpointRequest createAccessWatchpointRequest(Breakpoint requestor, Field field) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    AccessWatchpointRequest req = myEventRequestManager.createAccessWatchpointRequest(field);
    addLocatableRequest(requestor, req);
    return req;
  }

  public ModificationWatchpointRequest createModificationWatchpointRequest(Breakpoint requestor, Field field) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ModificationWatchpointRequest req = myEventRequestManager.createModificationWatchpointRequest(field);
    addLocatableRequest(requestor, req);
    return req;
  }

  public void createRequest(Breakpoint breakpoint) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if(!myDebugProcess.isAttached() || !findRequests(breakpoint).isEmpty()) return;
    breakpoint.createRequest(myDebugProcess);
  }

  public void deleteRequest(Requestor requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if(!myDebugProcess.isAttached()) return;
    Set<EventRequest> requests = myRequestorToBelongedRequests.get(requestor);
    if(requests == null) return;
    myRequestorToBelongedRequests.remove(requestor);
    for (Iterator iterator = requests.iterator(); iterator.hasNext();) {
      EventRequest eventRequest = (EventRequest) iterator.next();
      myRequestsToProcessingRequestor.remove(eventRequest);
    }
    for (Iterator iterator = requests.iterator(); iterator.hasNext();) {
      EventRequest eventRequest = (EventRequest)iterator.next();
      try {
        myEventRequestManager.deleteEventRequest(eventRequest);
      } catch (InternalException e) {
        if(e.errorCode() == 41) {
          //event request not found
          //there could be no requests after hotswap
        } else {
          LOG.error(e);
        }
      }
    }
  }

  public void callbackOnPrepareClasses(final ClassPrepareRequestor requestor, final SourcePosition classPosition) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ClassPrepareRequest prepareRequest = myDebugProcess.getPositionManager().createPrepareRequest(requestor, classPosition);

    if(prepareRequest == null) {
      setInvalid(requestor, "Breakpoint does not belong to any class");
      return;
    }

    belongsToRequestor(requestor, prepareRequest);
    prepareRequest.enable();
  }

  public void callbackOnPrepareClasses(ClassPrepareRequestor requestor, String classOrPatternToBeLoaded) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ClassPrepareRequest classPrepareRequest = createClassPrepareRequest(requestor, classOrPatternToBeLoaded);

    belongsToRequestor(requestor, classPrepareRequest);
    classPrepareRequest.enable();
    if (LOG.isDebugEnabled()) {
      LOG.debug("classOrPatternToBeLoaded = " + classOrPatternToBeLoaded);
    }
  }

  public void enableRequest(EventRequest request) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    LOG.assertTrue(findRequestor(request) != null);
    try {
      request.enable();
    } catch (InternalException e) {
      if(e.errorCode() == 41) {
        //event request not found
        //there could be no requests after hotswap
      } else {
        LOG.error(e);
      }
    }
  }

  public void setInvalid(Requestor requestor, String message) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myInvalidRequestors.put(requestor, message);
  }

  public boolean isInvalid(Requestor requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myInvalidRequestors.containsKey(requestor);
  }

  public String getInvalidMessage(Requestor requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myInvalidRequestors.get(requestor);
  }

  public boolean isVerified(Requestor requestor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    //ClassPrepareRequest is added in any case
    return findRequests(requestor).size() > 1;
  }

  public void processDetached(DebugProcessImpl process) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myEventRequestManager = null;
    myInvalidRequestors.clear();
    myRequestorToBelongedRequests.clear();
    myRequestsToProcessingRequestor.clear();
  }

  public void processAttached(DebugProcessImpl process) {
    myEventRequestManager = myDebugProcess.getVirtualMachineProxy().eventRequestManager();

    BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myDebugProcess.getProject()).getBreakpointManager();

    for (Iterator<Breakpoint> iterator = breakpointManager.getBreakpoints().iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      myDebugProcess.getRequestsManager().createRequest(breakpoint);
    }
  }

  public void processClassPrepared(final ClassPrepareEvent event) {
    if (!myDebugProcess.isAttached()) {
      return;
    }

    final ReferenceType refType = event.referenceType();

    if (refType instanceof ClassType) {
      ClassType classType = (ClassType)refType;
      if (LOG.isDebugEnabled()) {
        LOG.debug("signature = " + classType.signature());
      }
      ClassPrepareRequestor requestor = (ClassPrepareRequestor)myRequestsToProcessingRequestor.get((ClassPrepareRequest)event.request());
      if (requestor != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("requestor found " + classType.signature());
        }
        requestor .processClassPrepare(myDebugProcess, classType);
      }
    }
  }

  private static interface AllProcessesCommand {
    void action(DebugProcessImpl process);
  }

  private static void invoke(Project project, final AllProcessesCommand command) {
    Collection<DebuggerSession> sessions = (DebuggerManagerEx.getInstanceEx(project)).getSessions();
    for (Iterator<DebuggerSession> iterator = sessions.iterator(); iterator.hasNext();) {
      DebuggerSession debuggerSession = iterator.next();
      final DebugProcessImpl process = debuggerSession.getProcess();
      if(process != null) {
        process.getManagerThread().invoke(new DebuggerCommandImpl() {
          protected void action() throws Exception {
            command.action(process);
          }
        });
      }
    }
  }

  public static void createRequests(final Breakpoint breakpoint) {
    invoke(breakpoint.getProject(), new AllProcessesCommand (){
      public void action(DebugProcessImpl process)  {
        process.getRequestsManager().createRequest(breakpoint);
      }
    });
  }

  public static void updateRequests(final Breakpoint breakpoint) {
    invoke(breakpoint.getProject(), new AllProcessesCommand (){
      public void action(DebugProcessImpl process)  {
        process.getRequestsManager().myInvalidRequestors.remove(breakpoint);
        process.getRequestsManager().deleteRequest(breakpoint);
        process.getRequestsManager().createRequest(breakpoint);
      }
    });
  }

  public static void deleteRequests(final Breakpoint breakpoint) {
    invoke(breakpoint.getProject(), new AllProcessesCommand (){
      public void action(DebugProcessImpl process)  {
        process.getRequestsManager().deleteRequest(breakpoint);
      }
    });
  }
}
