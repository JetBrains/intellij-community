package com.intellij.debugger.engine;

import com.intellij.Patches;
import com.intellij.debugger.ClassFilter;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.ui.DebuggerSmoothManager;
import com.intellij.debugger.ui.DescriptorHistoryManagerImpl;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.debugger.ui.impl.watch.DescriptorHistoryManager;
import com.intellij.debugger.ui.tree.render.ArrayRenderer;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.debugger.ui.tree.render.PrimitiveRenderer;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.InternalIterator;
import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import com.sun.tools.jdi.ConnectionService;
import com.sun.tools.jdi.TransportService;
import com.sun.tools.jdi.VirtualMachineManagerService;

import javax.swing.*;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;

public abstract class DebugProcessImpl implements DebugProcess {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.DebugProcessImpl");

  private static final String SOCKET_ATTACHING_CONNECTOR_NAME = "com.sun.jdi.SocketAttach";
  private static final String SHMEM_ATTACHING_CONNECTOR_NAME = "com.sun.jdi.SharedMemoryAttach";
  private static final String SOCKET_LISTENING_CONNECTOR_NAME = "com.sun.jdi.SocketListen";
  private static final String SHMEM_LISTENING_CONNECTOR_NAME = "com.sun.jdi.SharedMemoryListen";

  public static final String ALWAYS_FORCE_CLASSIC_VM = "always";
  public static final String NEVER_FORCE_CLASSIC_VM = "never";
  public static final String IF_SELECTED_FORCE_CLASSIC_VM = "if_selected";

  public static final String JAVA_STRATUM = "Java";

  private final Project myProject;
  private final RequestManagerImpl myRequestManager;

  private VirtualMachineProxyImpl myVirtualMachineProxy = null;
  protected EventDispatcher<DebugProcessListener> myDebugProcessDispatcher = EventDispatcher.create(DebugProcessListener.class, false);
  protected EventDispatcher<EvaluationListener> myEvaluationDispatcher = EventDispatcher.create(EvaluationListener.class, false);

  private List<ProcessListener> myProcessListeners = new ArrayList<ProcessListener>();

  private static final int STATE_INITIAL   = 0;
  private static final int STATE_ATTACHED  = 1;
  private static final int STATE_DETACHING = 2;
  private static final int STATE_DETACHED  = 3;
  private int myState = STATE_INITIAL;

  private boolean myCanRedefineClasses;
  private boolean myCanWatchFieldModification;

  private ExecutionResult  myExecution;
  private RemoteConnection myConnection;

  private ConnectionService myConnectionService;
  private Map myArguments;

  private LinkedList<String> myStatusStack = new LinkedList<String>();
  private String myStatusText;
  private int mySuspendPolicy = DebuggerSettings.getInstance().isSuspendAllThreads()
                                ? EventRequest.SUSPEND_ALL
                                : EventRequest.SUSPEND_EVENT_THREAD;

  private final DescriptorHistoryManager myDescriptorHistoryManager;

  private final List<NodeRenderer> myRenderers = new ArrayList<NodeRenderer>();
  private final Map<Type, NodeRenderer>  myNodeRederersMap = new com.intellij.util.containers.HashMap<Type, NodeRenderer>();
  private final NodeRendererSettingsListener  mySettingsListener = new NodeRendererSettingsListener() {
      public void renderersChanged() {
        myNodeRederersMap.clear();
        myRenderers.clear();
        loadRenderers();
      }
    };

  private final SuspendManagerImpl mySuspendManager = new SuspendManagerImpl(this);
  protected CompoundPositionManager myPositionManager = null;
  DebuggerManagerThreadImpl myDebuggerManagerThread;
  public static final String MSG_FAILD_TO_CONNECT = "Failed to establish connection to the target VM";
  private HashMap myUserData = new HashMap();
  private static int LOCAL_START_TIMEOUT = 15000;

  private final Semaphore myWaitFor = new Semaphore();

  protected DebugProcessImpl(Project project) {
    myProject = project;
    myRequestManager = new RequestManagerImpl(this);
    myDescriptorHistoryManager = new DescriptorHistoryManagerImpl(project);
    setSuspendPolicy(DebuggerSettings.getInstance().isSuspendAllThreads());
    NodeRendererSettings.getInstance().addListener(mySettingsListener);
    loadRenderers();
  }

  private void loadRenderers() {
    getManagerThread().invoke(new DebuggerCommandImpl() {
      protected void action() throws Exception {
        final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();
        for (Iterator<NodeRenderer> it = rendererSettings.getAllRenderers().iterator(); it.hasNext();) {
          final NodeRenderer renderer = it.next();
          if(renderer.isEnabled() && renderer instanceof ValueLabelRenderer) {
            myRenderers.add(renderer);
          }
        }
      }
    });
  }

  public NodeRenderer getAutoRenderer(ValueDescriptor descriptor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final Value value = descriptor.getValue();
    Type type = value != null ? value.type() : null;

    NodeRenderer renderer = myNodeRederersMap.get(type);
    if(renderer == null) {
      for (Iterator<NodeRenderer> iterator = myRenderers.iterator(); iterator.hasNext();) {
        final NodeRenderer nodeRenderer = iterator.next();
        if(nodeRenderer.isApplicable(type)) {
          renderer = nodeRenderer;
          break;
        }
      }
      if (renderer == null) {
        renderer = getDefaultRenderer(type);
      }
      myNodeRederersMap.put(type, renderer);
    }

    return renderer;
  }

  public NodeRenderer getDefaultRenderer(Type type) {
    final NodeRendererSettings settings = NodeRendererSettings.getInstance();

    final PrimitiveRenderer primitiveRenderer = settings.getPrimitiveRenderer();
    if(primitiveRenderer.isApplicable(type)) {
      return primitiveRenderer;
    }

    final ArrayRenderer arrayRenderer = settings.getArrayRenderer();
    if(arrayRenderer.isApplicable(type)) {
      return arrayRenderer;
    }

    final ClassRenderer classRenderer = settings.getClassRenderer();
    LOG.assertTrue(classRenderer.isApplicable(type), type.name());
    return classRenderer;
  }


  protected void commitVM(VirtualMachine vm) {
    LOG.assertTrue(myState == STATE_INITIAL, "State is invalid " + myState);
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myPositionManager = createPositionManager();
    if (LOG.isDebugEnabled()) {
      LOG.debug("*******************VM attached******************");
    }
    checkVirtualMachineVersion(vm);

    myVirtualMachineProxy = new VirtualMachineProxyImpl(this, vm);
    myCanRedefineClasses = myVirtualMachineProxy.canRedefineClasses();
    myCanWatchFieldModification = myVirtualMachineProxy.canWatchFieldModification();

    String trace = System.getProperty("idea.debugger.trace");
    if (trace != null) {
      int mask = 0;
      StringTokenizer tokenizer = new StringTokenizer(trace);
      while (tokenizer.hasMoreTokens()) {
        String token = tokenizer.nextToken();
        if ("SENDS".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_SENDS;
        }
        else if ("RAW_SENDS".compareToIgnoreCase(token) == 0) {
          mask |= 0x01000000;
        }
        else if ("RECEIVES".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_RECEIVES;
        }
        else if ("RAW_RECEIVES".compareToIgnoreCase(token) == 0) {
          mask |= 0x02000000;
        }
        else if ("EVENTS".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_EVENTS;
        }
        else if ("REFTYPES".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_REFTYPES;
        }
        else if ("OBJREFS".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_OBJREFS;
        }
        else if ("ALL".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_ALL;
        }
      }

      vm.setDebugTraceMode(mask);
    }
  }

  private void stopConnecting() {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    Map arguments = myArguments;
    try {
      if (arguments == null) {
        return;
      }
      if(myConnection.isServerMode()) {
        ListeningConnector connector = (ListeningConnector)findConnector(SOCKET_LISTENING_CONNECTOR_NAME);
        LOG.assertTrue(connector != null, "Cannot find connector: " + SOCKET_LISTENING_CONNECTOR_NAME);
        connector.stopListening(arguments);
      }
      else {
        if(myConnectionService != null) {
          myConnectionService.close();
        }
      }
    }
    catch (IOException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
    }
    catch (IllegalConnectorArgumentsException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
    finally {
      closeProcess(false);
    }
  }

  protected CompoundPositionManager createPositionManager() {
    return new CompoundPositionManager(new PositionManagerImpl(this));
  }

  public void printToConsole(final String text) {
    myExecution.getProcessHandler().notifyTextAvailable(text, ProcessOutputTypes.SYSTEM);
  }

  /**
   *
   * @param stepThread
   * @param depth
   * @param hint may be null
   */
  protected void doStep(final ThreadReferenceProxyImpl stepThread, int depth, RequestHint hint) {
    /*
    if (stepThread == null || !stepThread.isSuspended()) {
      return false;
    }
    */
    if (LOG.isDebugEnabled()) {
      LOG.debug("DO_STEP: creating step request for " + stepThread.getThreadReference());
    }
    deleteStepRequests(stepThread);
    EventRequestManager requestManager = getVirtualMachineProxy().eventRequestManager();
    StepRequest stepRequest = requestManager.createStepRequest(stepThread.getThreadReference(), StepRequest.STEP_LINE, depth);
    DebuggerSettings settings = DebuggerSettings.getInstance();
    if (!(hint != null && hint.isIgnoreFilters()) && depth == StepRequest.STEP_INTO) {
      if (settings.TRACING_FILTERS_ENABLED) {
        String currentClassName = getCurrentClassName(stepThread);
        if (currentClassName == null || !settings.isNameFiltered(currentClassName)) {
          // add class filters
          ClassFilter[] filters = settings.getSteppingFilters();
          for (int idx = 0; idx < filters.length; idx++) {
            if (filters[idx].isEnabled()) {
              stepRequest.addClassExclusionFilter(filters[idx].getPattern());
            }
          }
        }
      }
    }

    stepRequest.setSuspendPolicy(getSuspendPolicy());

    if (hint != null) {
      stepRequest.putProperty("hint", hint);
    }
    stepRequest.enable();
  }

  void deleteStepRequests(ThreadReferenceProxy requestsInThread) {
    EventRequestManager requestManager = getVirtualMachineProxy().eventRequestManager();
    List stepRequests = requestManager.stepRequests();
    if (stepRequests.size() > 0) {
      List toDelete = new ArrayList();
      for (Iterator iterator = stepRequests.iterator(); iterator.hasNext();) {
        StepRequest request = (StepRequest)iterator.next();
        ThreadReference threadReference = request.thread();

        if (threadReference.status() == ThreadReference.THREAD_STATUS_UNKNOWN) {
          // [jeka] on attempt to delete a request assigned to a thread with unknown status, a JDWP error occures
          continue;
        }
        else /*if(threadReference.equals(requestsInThread.getThreadReference())) */{
          toDelete.add(request);
        }
      }
      for (Iterator iterator = toDelete.iterator(); iterator.hasNext();) {
        StepRequest request = (StepRequest)iterator.next();
        requestManager.deleteEventRequest(request);
      }
    }
  }

  private String getCurrentClassName(ThreadReferenceProxyImpl thread) {
    try {
      final ThreadReferenceProxyImpl currentThreadProxy = thread;
      if (currentThreadProxy != null) {
        if (currentThreadProxy.frameCount() > 0) {
          StackFrameProxyImpl stackFrame = currentThreadProxy.frame(0);
          Location location = stackFrame.location();
          ReferenceType referenceType = location.declaringType();
          if (referenceType != null) {
            return referenceType.name();
          }
        }
      }
    }
    catch (EvaluateException e) {
    }
    return null;
  }

  private VirtualMachine createVirtualMachineInt()
    throws ExecutionException {

    try {
      if (myArguments != null) {
        throw new IOException("DebugProcessImpl is already listening");
      }

      String address = myConnection.getAddress();
      if (myConnection.isServerMode()) {
        ListeningConnector connector = (ListeningConnector)findConnector(
          myConnection.isUseSockets() ? SOCKET_LISTENING_CONNECTOR_NAME : SHMEM_LISTENING_CONNECTOR_NAME);
        if (connector == null) {
          throw new CantRunException("Cannot listen using " +
                                     (!myConnection.isUseSockets() ? "shared memory" : "socket") +
                                     " transport: required connector not found. Check your JDK installation.");
        }
        myArguments = connector.defaultArguments();
        if (myArguments == null) {
          throw new CantRunException("The port to listen at unspecified");
        }

        if (address == null) {
          throw new CantRunException("The port to listen at unspecified");
        }
        // negative port number means the caller leaves to debugger to decide at which hport to listen
        Connector.Argument portArg = myConnection.isUseSockets()
                                     ? (Connector.Argument)myArguments.get("port")
                                     : (Connector.Argument)myArguments.get("name");
        if (portArg != null) {
          portArg.setValue(address);
        }
        connector.startListening(myArguments);
        myDebugProcessDispatcher.getMulticaster().connectorIsReady();
        try {
          return connector.accept(myArguments);
        }
        finally {
          if(myArguments != null) {
            connector.stopListening(myArguments);
          }
        }
      }
      else {
        AttachingConnector connector = (AttachingConnector)findConnector(
          myConnection.isUseSockets() ? SOCKET_ATTACHING_CONNECTOR_NAME : SHMEM_ATTACHING_CONNECTOR_NAME);

        if (connector == null) {
          throw new CantRunException("Cannot connect using " +
                                     (myConnection.isUseSockets() ? "socket" : "shared memory") +
                                     " transport: required connector not found. Check your JDK installation.");
        }
        myArguments = connector.defaultArguments();
        Connector.Argument argument;
        if (myConnection.isUseSockets()) {
          argument = (Connector.Argument)myArguments.get("hostname");
          if (argument != null && myConnection.getHostName() != null) {
            argument.setValue(myConnection.getHostName());
          }
          if (address == null) {
            throw new CantRunException("The port to attach to unspecified");
          }
          argument = (Connector.Argument)myArguments.get("port");
          if (argument != null) {
            argument.setValue(address);
          }
        }
        else {
          if (address == null) {
            throw new CantRunException("Shared memory address unspecified");
          }
          argument = (Connector.Argument)myArguments.get("name");
          if (argument != null) {
            argument.setValue(address);
          }
        }
        myDebugProcessDispatcher.getMulticaster().connectorIsReady();
        try {
          if(SOCKET_ATTACHING_CONNECTOR_NAME.equals(connector.name()) && Patches.SUN_BUG_338675) {
            String portString = myConnection.getAddress();
            String hostString = myConnection.getHostName();

            if (hostString == null || hostString.length() == 0) {
                hostString = "localhost";
            }
            hostString = hostString + ":";

            myConnectionService = ((TransportService) connector.transport()).attach(hostString + portString);
            return ((VirtualMachineManagerService) Bootstrap.virtualMachineManager()).createVirtualMachine(myConnectionService);
          }
          else {
            return connector.attach(myArguments);
          }
        }
        catch (IllegalArgumentException e) {
          throw new CantRunException("Connector myArguments invalid : " + e.getMessage());
        }
      }
    }
    catch (IOException e) {
      throw new ExecutionException(createConnectionStatusMessage(processError(e), myConnection), e);
    }
    catch (IllegalConnectorArgumentsException e) {
      throw new ExecutionException(createConnectionStatusMessage(processError(e), myConnection), e);
    }
    finally {
      myArguments = null;
      myConnectionService = null;
    }
  }

  private void pushStatisText(String text) {
    if (myStatusText == null) {
      myStatusText = ((StatusBarEx)WindowManager.getInstance().getStatusBar(getProject())).getInfo();
    }

    myStatusStack.addLast(myStatusText);
    showStatusText(text);
  }

  private void popStatisText() {
    if (!myStatusStack.isEmpty()) {
      showStatusText(myStatusStack.removeFirst());
    }
  }

  public void showStatusText(final String text) {
    myStatusText = text;
    DebuggerSmoothManager.getInstanceEx().action("DebugProcessImpl.showStatusText", new Runnable() {
      public void run() {
        if (ProjectManagerEx.getInstanceEx().isProjectOpened(getProject())) {
          WindowManager.getInstance().getStatusBar(getProject()).setInfo(text);
          myStatusText = null;
        }
      }
    });
  }

  private static Connector findConnector(String connectorName) throws ExecutionException {
    VirtualMachineManager virtualMachineManager = null;
    try {
      virtualMachineManager = Bootstrap.virtualMachineManager();
    }
    catch (Error e) {
      throw new ExecutionException(e.getClass().getName() + " : " + e.getMessage() + ". Check your JDK installation.");
    }
    List connectors;
    if (SOCKET_ATTACHING_CONNECTOR_NAME.equals(connectorName) || SHMEM_ATTACHING_CONNECTOR_NAME.equals(connectorName)) {
      connectors = virtualMachineManager.attachingConnectors();
    }
    else if (SOCKET_LISTENING_CONNECTOR_NAME.equals(connectorName) || SHMEM_LISTENING_CONNECTOR_NAME.equals(connectorName)) {
      connectors = virtualMachineManager.listeningConnectors();
    }
    else {
      return null;
    }
    for (Iterator it = connectors.iterator(); it.hasNext();) {
      Connector connector = (Connector)it.next();
      if (connectorName.equals(connector.name())) {
        return connector;
      }
    }
    return null;
  }

  private void checkVirtualMachineVersion(VirtualMachine vm) {
    final String version = vm.version();
    if ("1.4.0".equals(version)) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          Messages.showMessageDialog(getProject(),
                                     "The debuggee VM version is \"" + version +
                                     "\".\nJ2SDK 1.4.0 documented bugs may cause unstable debugger behavior.\nWe recommend you using J2SDK 1.4.0_01 or higher.",
                                     "VM Version Warning",
                                     Messages.getWarningIcon());
        }
      });
    }
  }

  /*Event dispatching*/
  public void addEvaluationListener(EvaluationListener evaluationListener) {
    myEvaluationDispatcher.addListener(evaluationListener);
  }

  public void removeEvaluationListener(EvaluationListener evaluationListener) {
    myEvaluationDispatcher.removeListener(evaluationListener);
  }

  public void addDebugProcessListener(DebugProcessListener listener) {
    myDebugProcessDispatcher.addListener(listener);
  }

  public void removeDebugProcessListener(DebugProcessListener listener) {
    myDebugProcessDispatcher.removeListener(listener);
  }

  public void addProcessListener(ProcessListener processListener) {
    synchronized(myProcessListeners) {
      if(getExecutionResult() != null) {
        getExecutionResult().getProcessHandler().addProcessListener(processListener);
      }
      else {
        myProcessListeners.add(processListener);
      }
    }
  }

  public void removeProcessListener(ProcessListener processListener) {
    synchronized (myProcessListeners) {
      if(getExecutionResult() != null) {
        getExecutionResult().getProcessHandler().removeProcessListener(processListener);
      }
      else {
        myProcessListeners.remove(processListener);
      }
    }
  }

  /* getters */
  public RemoteConnection getConnection() {
    return myConnection;
  }

  public ExecutionResult getExecutionResult() {
    return myExecution;
  }

  public <T> T getUserData(Key<T> key) {
    return (T)myUserData.get(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    myUserData.put(key, value);
  }

  public Project getProject() {
    return myProject;
  }

  public boolean canRedefineClasses() {
    return myCanRedefineClasses;
  }

  public boolean canWatchFieldModification() {
    return myCanWatchFieldModification;
  }

  public boolean isAttached() {
    return myState == STATE_ATTACHED;
  }

  public boolean isDetached() {
    return myState == STATE_DETACHED;
  }

  public boolean isDetaching() {
    return myState == STATE_DETACHING;
  }

  protected void setIsAttached() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myState = STATE_ATTACHED;
  }

  protected void setIsDetaching() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myState = STATE_DETACHING;
  }

  protected void setIsDetached() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myState = STATE_DETACHED;
  }

  public RequestManagerImpl getRequestsManager() {
    return myRequestManager;
  }

  public VirtualMachineProxyImpl getVirtualMachineProxy() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myVirtualMachineProxy == null) throw new VMDisconnectedException();
    return myVirtualMachineProxy;
  }

  public void appendPositionManager(final PositionManager positionManager) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myPositionManager.appendPositionManager(positionManager);
    DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().updateBreakpoints(this);
  }

  private LineBreakpoint myRunToCursorBreakpoint;

  public void cancelRunToCursorBreakpoint() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myRunToCursorBreakpoint != null) {
      getRequestsManager().deleteRequest(myRunToCursorBreakpoint);
      myRunToCursorBreakpoint.delete();
      myRunToCursorBreakpoint = null;
    }
  }

  protected void closeProcess(boolean fireDetached) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    if (isDetached() || isDetaching()) return;

    setIsDetaching();
    myVirtualMachineProxy = null;
    myPositionManager = null;

    DebugProcessImpl.this.getManagerThread().close();

    setIsDetached();
    if(fireDetached) {
      myDebugProcessDispatcher.getMulticaster().processDetached(DebugProcessImpl.this);
    }
    myWaitFor.up();
  }

  private static String formatMessage(String message) {
    final int lineLength = 90;
    StringBuffer buf = new StringBuffer(message.length());
    int index = 0;
    while (index < message.length()) {
      buf.append(message.substring(index, Math.min(index + lineLength, message.length()))).append('\n');
      index += lineLength;
    }
    return buf.toString();
  }

  public static String processError(Exception e) {
    String message;

    if (e instanceof VMStartException) {
      VMStartException e1 = (VMStartException)e;
      message = e1.getMessage();
    }
    else if (e instanceof IllegalConnectorArgumentsException) {
      IllegalConnectorArgumentsException e1 = (IllegalConnectorArgumentsException)e;
      message = formatMessage("Bad Argument : " + e1.getMessage()) + e1.argumentNames();
      if (LOG.isDebugEnabled()) {
        LOG.debug(e1);
      }
    }
    else if (e instanceof CantRunException) {
      message = "Error Launching Debuggee.\n" + e.getMessage();
    }
    else if (e instanceof VMDisconnectedException) {
      message = "VM Disconnected.\n" + "Target virtual machine closed connection.";
    }
    else if (e instanceof UnknownHostException) {
      message = "Cannot Connect to Remote Process.\n" +
                "Host unknown: " + e.getMessage();
    }
    else if (e instanceof IOException) {
      IOException e1 = (IOException)e;
      StringBuffer buf = new StringBuffer("Unable to open debugger port : ");
      buf.append(e1.getClass().getName() + " ");
      if (e1.getMessage() != null && e1.getMessage().length() > 0) {
        buf.append('"');
        buf.append(e1.getMessage());
        buf.append('"');
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug(e1);
      }
      message = buf.toString();
    }
    else if (e instanceof ExecutionException) {
      message = e.getMessage();
    }
    else  {
      message = "Error Connecting to Remote Process.\n" +
                "Exception occured: " + e.getClass().getName() + "\n" +
                "Exception message: " + e.getMessage();
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
    }
    return message;
  }

  public DescriptorHistoryManager getDescriptorHistoryManager() {
    return myDescriptorHistoryManager;
  }

  public static String createConnectionStatusMessage(String connectStatus, RemoteConnection connection) {
    StringBuffer message = new StringBuffer(128);
    message.append(connectStatus);
    if (connection.isUseSockets()) {
      message.append(" at '");
      message.append(connection.getHostName());
      message.append(':');
      message.append(connection.getAddress());
      message.append("' using socket transport");
    }
    else {
      message.append(" at address '");
      message.append(connection.getAddress());
      message.append("' using shared memory transport");
    }
    message.append(". ");
    String _msg = message.toString();
    return _msg;
  }

  public void dispose() {
    LOG.assertTrue(!isAttached());
    NodeRendererSettings.getInstance().addListener(mySettingsListener);
    myDescriptorHistoryManager.dispose();
  }

  public DebuggerManagerThreadImpl getManagerThread() {
    synchronized (this) {
      if (myDebuggerManagerThread == null) {
        myDebuggerManagerThread = new DebuggerManagerThreadImpl();
      }
      return myDebuggerManagerThread;
    }
  }

  private static int getInvokePolicy(SuspendContext suspendContext) {
    return suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD ? ThreadReference.INVOKE_SINGLE_THREADED : 0;
  }

  public void waitFor() {
    LOG.assertTrue(!DebuggerManagerThreadImpl.isManagerThread());
    myWaitFor.waitFor();
  }

  private abstract class InvokeCommand <E extends Value> {
    protected abstract E invokeMethod(int invokePolicy) throws InvocationException,
                                                               ClassNotLoadedException,
                                                               IncompatibleThreadStateException,
                                                               InvalidTypeException;

    public E start(EvaluationContextImpl evaluationContext, Method method) throws EvaluateException {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      SuspendContextImpl suspendContext = evaluationContext.getSuspendContext();
      SuspendManagerUtil.assertSuspendContext(suspendContext);

      myEvaluationDispatcher.getMulticaster().evaluationStarted(suspendContext);

      beforeMethodInvocation(suspendContext, method);
      ThreadReferenceProxyImpl invokeThread = suspendContext.getThread();

      if (SuspendManagerUtil.isEvaluating(getSuspendManager(), invokeThread)) {
        throw EvaluateExceptionUtil.NESTED_EVALUATION_ERROR;
      }

      Set<SuspendContextImpl> suspendingContexts = SuspendManagerUtil.getSuspendingContexts(getSuspendManager(), invokeThread);
      for (Iterator<SuspendContextImpl> iterator = suspendingContexts.iterator(); iterator.hasNext();) {
        SuspendContextImpl suspendingContext = iterator.next();
        if (suspendingContext.getThread() != invokeThread) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Resuming " + invokeThread + "that is paused by " + suspendingContext.getThread());
          }
          LOG.assertTrue(!suspendingContext.getThread().getThreadReference().equals(invokeThread.getThreadReference()));
          getSuspendManager().resumeThread(suspendingContext, invokeThread);
        }
      }

      Object resumeData = SuspendManagerUtil.prepareForResume(suspendContext);
      suspendContext.setIsEvaluating(evaluationContext);

      getVirtualMachineProxy().clearCaches();

      try {
        for (; ;) {
          try {
            return invokeMethodAndFork(suspendContext);
          }
          catch (ClassNotLoadedException e) {
            ReferenceType loadedClass = loadClass(evaluationContext, e.className(), evaluationContext.getClassLoader());
            if (loadedClass == null) throw EvaluateExceptionUtil.createEvaluateException(e);
          }
        }
      }
      catch (ClassNotLoadedException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (InvocationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (IncompatibleThreadStateException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (InvalidTypeException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (ObjectCollectedException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      finally {
        suspendContext.setIsEvaluating(null);
        SuspendManagerUtil.restoreAfterResume(suspendContext, resumeData);
        for (Iterator<SuspendContextImpl> iterator = getSuspendManager().getEventContexts().iterator(); iterator.hasNext();) {
          SuspendContextImpl suspendingContext = iterator.next();
          if (suspendingContexts.contains(suspendingContext) && !suspendingContext.isEvaluating() && !suspendingContext.suspends(invokeThread)) {
            getSuspendManager().suspendThread(suspendingContext, invokeThread);
          }
        }

        if (LOG.isDebugEnabled()) {
          LOG.debug("getVirtualMachine().clearCaches()");
        }
        getVirtualMachineProxy().clearCaches();
        afterMethodInvocation(suspendContext);

        myEvaluationDispatcher.getMulticaster().evaluationFinished(suspendContext);
      }
    }

    private E invokeMethodAndFork(final SuspendContextImpl context) throws InvocationException,
                                                                 ClassNotLoadedException,
                                                                 IncompatibleThreadStateException,
                                                                 InvalidTypeException {
      final int invokePolicy = getInvokePolicy(context);
      final Exception[] exception = new Exception[1];
      final Value[] result = new Value[1];
      DebugProcessImpl.this.getManagerThread().startLongProcessAndFork(new Runnable() {
        public void run() {
          ThreadReferenceProxyImpl thread = context.getThread();
          try {
            try {
              if (LOG.isDebugEnabled()) {
                getVirtualMachineProxy().logThreads();
                LOG.debug("Invoke in " + thread.name());
                LOG.assertTrue(thread.isSuspended(), thread.toString());
                LOG.assertTrue(context.isEvaluating());
              }
              result[0] = invokeMethod(invokePolicy);
              if(result[0] instanceof ObjectReference) {
                context.keep(((ObjectReference)result[0]));
              }
            }
            finally {
              LOG.assertTrue(thread.isSuspended(), thread.toString());
              LOG.assertTrue(context.isEvaluating());
            }
          }
          catch (Exception e) {
            exception[0] = e;
          }
        }
      });

      if (exception[0] != null) {
        if (exception[0] instanceof InvocationException) {
          throw (InvocationException)exception[0];
        }
        else if (exception[0] instanceof ClassNotLoadedException) {
          throw (ClassNotLoadedException)exception[0];
        }
        else if (exception[0] instanceof IncompatibleThreadStateException) {
          throw (IncompatibleThreadStateException)exception[0];
        }
        else if (exception[0] instanceof InvalidTypeException) {
          throw (InvalidTypeException)exception[0];
        }
        else if (exception[0] instanceof RuntimeException) {
          throw (RuntimeException)exception[0];
        }
        else {
          LOG.assertTrue(false);
        }
      }

      return (E)result[0];
    }
  }

  public Value invokeMethod(final EvaluationContext evaluationContext, final ObjectReference objRef,
                            final Method method,
                            final List args) throws EvaluateException {

    final ThreadReference thread = getEvaluationThread(evaluationContext);
    InvokeCommand<Value> invokeCommand = new InvokeCommand<Value>() {
      protected Value invokeMethod(int invokePolicy) throws InvocationException,
                                                            ClassNotLoadedException,
                                                            IncompatibleThreadStateException,
                                                            InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invoke " + method.name());
        }
        return objRef.invokeMethod(thread, method, args, invokePolicy);
      }
    };
    return invokeCommand.start((EvaluationContextImpl)evaluationContext, method);
  }

  private ThreadReference getEvaluationThread(final EvaluationContext evaluationContext) throws EvaluateException {
    ThreadReferenceProxy evaluationThread = evaluationContext.getSuspendContext().getThread();
    if(evaluationThread == null) throw EvaluateExceptionUtil.NULL_STACK_FRAME;
    return evaluationThread.getThreadReference();
  }

  public Value invokeMethod(final EvaluationContext evaluationContext, final ClassType classType,
                            final Method method,
                            final List args) throws EvaluateException {

    final ThreadReference thread = getEvaluationThread(evaluationContext);
    InvokeCommand<Value> invokeCommand = new InvokeCommand<Value>() {
      protected Value invokeMethod(int invokePolicy) throws InvocationException,
                                                            ClassNotLoadedException,
                                                            IncompatibleThreadStateException,
                                                            InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invoke " + method.name());
        }
        return classType.invokeMethod(thread, method, args, invokePolicy);
      }
    };
    return invokeCommand.start((EvaluationContextImpl)evaluationContext, method);
  }

  public ArrayReference newInstance(final ArrayType arrayType,
                                    final int dimension)
    throws EvaluateException {
    return arrayType.newInstance(dimension);
  }

  public ObjectReference newInstance(final EvaluationContext evaluationContext, final ClassType classType,
                                     final Method method,
                                     final List args) throws EvaluateException {
    final ThreadReference thread = getEvaluationThread(evaluationContext);
    InvokeCommand<ObjectReference> invokeCommand = new InvokeCommand<ObjectReference>() {
      protected ObjectReference invokeMethod(int invokePolicy) throws InvocationException,
                                                                      ClassNotLoadedException,
                                                                      IncompatibleThreadStateException,
                                                                      InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("New instance " + method.name());
        }
        return classType.newInstance(thread, method, args, invokePolicy);
      }
    };
    return invokeCommand.start((EvaluationContextImpl)evaluationContext, method);
  }

  public void clearCashes(int suspendPolicy) {
    if (!isAttached()) return;
    switch (suspendPolicy) {
      case EventRequest.SUSPEND_ALL:
        getVirtualMachineProxy().clearCaches();
        break;
      case EventRequest.SUSPEND_EVENT_THREAD:
        getVirtualMachineProxy().clearCaches();
        //suspendContext.getThread().clearAll();
        break;
    }
  }

  protected void beforeSuspend(SuspendContextImpl suspendContext) {
    clearCashes(suspendContext.getSuspendPolicy());
  }

  private void beforeMethodInvocation(SuspendContextImpl suspendContext, Method method) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "before invocation in  thread " + suspendContext.getThread().name() + " method " + (method == null ? "null" : method.name()));
    }

    if (method != null) {
      pushStatisText("Evaluating " + DebuggerUtilsEx.methodName(method));
    }
    else {
      pushStatisText("Evaluating ...");
    }
  }

  private void afterMethodInvocation(SuspendContextImpl suspendContext) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("after invocation in  thread " + suspendContext.getThread().name());
    }
    popStatisText();
  }

  public ReferenceType findClass(EvaluationContext evaluationContext, String className,
                                 ClassLoaderReference classLoader) throws EvaluateException {
    try {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      ReferenceType result = null;
      final VirtualMachineProxyImpl vmProxy = getVirtualMachineProxy();
      if (vmProxy == null) {
        throw new VMDisconnectedException();
      }
      List list = vmProxy.classesByName(className);
      for (Iterator it = list.iterator(); it.hasNext();) {
        ReferenceType refType = (ReferenceType)it.next();
        if (refType.isPrepared() && Comparing.equal(refType.classLoader(), classLoader)) {
          result = refType;
          break;
        }
      }
      if (result == null) {
        return loadClass((EvaluationContextImpl)evaluationContext, className, classLoader);
      }
      else {
        return result;
      }
    }
    catch (InvocationException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (ClassNotLoadedException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (IncompatibleThreadStateException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (InvalidTypeException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  private String reformatArrayName(String className) {
    if (className.indexOf('[') == -1) return className;

    int dims = 0;
    while (className.endsWith("[]")) {
      className = className.substring(0, className.length() - 2);
      dims++;
    }

    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < dims; i++) {
      buffer.append('[');
    }
    String primitiveSignature = JVMNameUtil.getPrimitiveSignature(className);
    if(primitiveSignature != null) {
      buffer.append(primitiveSignature);
    }
    else {
      buffer.append('L');
      buffer.append(className);
      buffer.append(';');
    }
    return buffer.toString();
  }

  public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String qName,
                                 ClassLoaderReference classLoader) throws InvocationException,
                                                                          ClassNotLoadedException,
                                                                          IncompatibleThreadStateException,
                                                                          InvalidTypeException,
                                                                          EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    qName = reformatArrayName(qName);
    ReferenceType refType = null;
    VirtualMachine virtualMachine = getVirtualMachineProxy().getVirtualMachine();
    final List classClasses = virtualMachine.classesByName("java.lang.Class");
    if (classClasses.size() > 0) {
      ClassType classClassType = (ClassType)classClasses.get(0);
      final Method forNameMethod;
      if (classLoader != null) {
        forNameMethod = classClassType.concreteMethodByName("forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
      }
      else {
        forNameMethod = classClassType.concreteMethodByName("forName", "(Ljava/lang/String;)Ljava/lang/Class;");
      }
      final List args = new ArrayList(); // do not use unmodifiable lists because the list is modified by JPDA
      final StringReference qNameMirror = virtualMachine.mirrorOf(qName);
      qNameMirror.disableCollection();
      args.add(qNameMirror);
      if (classLoader != null) {
        args.add(virtualMachine.mirrorOf(true));
        args.add(classLoader);
      }
      try {
        final Value value = invokeMethod(evaluationContext, classClassType, forNameMethod, args);
        if (value instanceof ClassObjectReference) {
          refType = ((ClassObjectReference)value).reflectedType();
        }
      }
      finally {
        qNameMirror.enableCollection();
      }
    }
    return refType;
  }

  public int getSuspendPolicy() {
    return mySuspendPolicy;
  }

  public void setSuspendPolicy(boolean suspendAll) {
    mySuspendPolicy = suspendAll ? EventRequest.SUSPEND_ALL : EventRequest.SUSPEND_EVENT_THREAD;
    DebuggerSettings.getInstance().setSuspendPolicy(suspendAll);
  }

  public void setSuspendPolicy(int policy) {
    mySuspendPolicy = policy;
    DebuggerSettings.getInstance().setSuspendPolicy(policy == EventRequest.SUSPEND_ALL);
  }

  public void logThreads() {
    if (LOG.isDebugEnabled()) {
      try {
        Collection<ThreadReferenceProxyImpl> allThreads = getVirtualMachineProxy().allThreads();
        for (Iterator<ThreadReferenceProxyImpl> iterator = allThreads.iterator(); iterator.hasNext();) {
          ThreadReferenceProxyImpl threadReferenceProxy = iterator.next();
          LOG.debug("Thread name=" + threadReferenceProxy.name() + " suspendCount()=" + threadReferenceProxy.suspendCount());
        }
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    }
  }

  public SuspendManager getSuspendManager() {
    return mySuspendManager;
  }

  public CompoundPositionManager getPositionManager() {
    return myPositionManager;
  }
  //ManagerCommands

  public void stop(boolean forceTerminate) {
    this.getManagerThread().terminateAndInvoke(createStopCommand(forceTerminate), DebuggerManagerThreadImpl.COMMAND_TIMEOUT);
  }

  public StopCommand createStopCommand(boolean forceTerminate) {
    return new StopCommand(forceTerminate);
  }

  protected class StopCommand extends DebuggerCommandImpl {
    private final boolean myIsTerminateTargetVM;

    public StopCommand(boolean isTerminateTargetVM) {
      myIsTerminateTargetVM = isTerminateTargetVM;
    }

    protected void action() throws Exception {
      if (isAttached()) {
        if (myIsTerminateTargetVM) {
          getVirtualMachineProxy().exit(-1);
        }
        else {
          getVirtualMachineProxy().dispose();
        }
      }
      else {
        stopConnecting();
      }
    }
  }

  private class StepOutCommand extends ResumeCommand {
    public StepOutCommand(SuspendContextImpl suspendContext) {
      super(suspendContext);
    }

    public void contextAction() {
      showStatusText("Stepping out");
      final SuspendContextImpl suspendContext = getSuspendContext();
      doStep(suspendContext.getThread(), StepRequest.STEP_OUT, null);
      super.contextAction();
    }
  }

  private class StepIntoCommand extends ResumeCommand {
    private final boolean myIgnoreFilters;

    public StepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters) {
      super(suspendContext);
      myIgnoreFilters = ignoreFilters;
    }

    public void contextAction() {
      showStatusText("Stepping into");
      final SuspendContextImpl suspendContext = getSuspendContext();
      final ThreadReferenceProxyImpl stepThread = suspendContext.getThread();
      RequestHint hint = new RequestHint(stepThread, suspendContext, StepRequest.STEP_INTO);
      hint.setIgnoreFilters(myIgnoreFilters);
      doStep(stepThread, StepRequest.STEP_INTO, hint);
      super.contextAction();
    }
  }

  private class StepOverCommand extends ResumeCommand {
    private boolean myIsIgnoreBreakpoints;

    public StepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints) {
      super(suspendContext);
      myIsIgnoreBreakpoints = ignoreBreakpoints;
    }

    public void contextAction() {
      showStatusText("Stepping over");
      final SuspendContextImpl suspendContext = getSuspendContext();
      final ThreadReferenceProxyImpl steppingThread = suspendContext.getThread();
      RequestHint hint = new RequestHint(steppingThread, suspendContext, StepRequest.STEP_OVER);
      hint.setRestoreBreakpoints(myIsIgnoreBreakpoints);

      doStep(steppingThread, StepRequest.STEP_OVER, hint);

      if (myIsIgnoreBreakpoints) {
        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().disableBreakpoints(DebugProcessImpl.this);
      }
      super.contextAction();
    }
  }

  private class RunToCursorCommand extends ResumeCommand {
    private final LineBreakpoint myRunToCursorBreakpoint;

    private RunToCursorCommand(SuspendContextImpl suspendContext, Document document, int lineIndex) {
      super(suspendContext);
      myRunToCursorBreakpoint = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().addRunToCursorBreakpoint(document, lineIndex);
    }

    public void contextAction() {
      showStatusText("Run to cursor");
      cancelRunToCursorBreakpoint();
      if (myRunToCursorBreakpoint == null) {
        return;
      }
      myRunToCursorBreakpoint.SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL;
      myRunToCursorBreakpoint.LOG_ENABLED = false;
      myRunToCursorBreakpoint.createRequest(getSuspendContext().getDebugProcess());
      DebugProcessImpl.this.myRunToCursorBreakpoint = myRunToCursorBreakpoint;
      super.contextAction();
    }
  }

  private class ResumeCommand extends SuspendContextCommandImpl {

    public ResumeCommand(SuspendContextImpl suspendContext) {
      super(suspendContext);
    }

    public void contextAction() {
      showStatusText("Process resumed");
      getSuspendManager().resume(getSuspendContext());
      myDebugProcessDispatcher.getMulticaster().resumed(getSuspendContext());
    }
  }

  private class PauseCommand extends DebuggerCommandImpl {
    public PauseCommand() {
    }

    public void action() {
      if (!isAttached()) {
        return;
      }
      logThreads();
      getVirtualMachineProxy().suspend();
      logThreads();
      SuspendContextImpl suspendContext = mySuspendManager.pushSuspendContext(EventRequest.SUSPEND_ALL, 0);
      myDebugProcessDispatcher.getMulticaster().paused(suspendContext);
    }
  }

  private class ResumeThreadCommand extends SuspendContextCommandImpl {
    private final ThreadReferenceProxyImpl myThread;

    public ResumeThreadCommand(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl thread) {
      super(suspendContext);
      myThread = thread;
    }

    public void contextAction() {
      if (getSuspendManager().isFrozen(myThread)) {
        getSuspendManager().unfreezeThread(myThread);
      }

      if (getSuspendContext().getThread() == myThread) {
        DebugProcessImpl.this.getManagerThread().invoke(createResumeCommand(getSuspendContext()));
      }
      else {
        Set<SuspendContextImpl> suspendingContexts = SuspendManagerUtil.getSuspendingContexts(getSuspendManager(), myThread);
        for (Iterator<SuspendContextImpl> iterator = suspendingContexts.iterator(); iterator.hasNext();) {
          SuspendContextImpl suspendContext = iterator.next();
          getSuspendManager().resumeThread(suspendContext, myThread);
        }        
      }
    }
  }

  private class FreezeThreadCommand extends DebuggerCommandImpl {
    private final ThreadReferenceProxyImpl myThread;

    public FreezeThreadCommand(ThreadReferenceProxyImpl thread) {
      myThread = thread;
    }

    protected void action() throws Exception {
      SuspendManager suspendManager = getSuspendManager();
      if (!suspendManager.isFrozen(myThread)) {
        suspendManager.freezeThread(myThread);
      }
    }
  }

  private class PopFrameCommand extends DebuggerContextCommandImpl {
    private final StackFrameProxyImpl myStackFrame;

    public PopFrameCommand(DebuggerContextImpl context, StackFrameProxyImpl frameProxy) {
      super(context);
      myStackFrame = frameProxy;
    }

    public void threadAction() {
      ThreadReferenceProxyImpl thread = myStackFrame.threadProxy();

      if (myStackFrame.isBottom()) {
        DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
          public void run() {
            Messages.showMessageDialog(myProject, "Cannot pop bottom frame", "Action not Perfomed", Messages.getErrorIcon());
          }
        });
        return;
      }

      try {
        thread.popFrames(myStackFrame);
      }
      catch (EvaluateException e) {
        LOG.error(e);
      }
      getSuspendManager().popFrame(getSuspendContext());
    }
  }


  public ExecutionResult attachVirtualMachine(final RunProfileState state, final RemoteConnection remoteConnection, boolean pollConnection)
    throws ExecutionException {
    myWaitFor.down();

    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    LOG.assertTrue(myState == STATE_INITIAL);

    myConnection = remoteConnection;

    createVirtualMachine(pollConnection);

    try {
      synchronized (myProcessListeners) {
        myExecution = state.execute();

        for (Iterator<ProcessListener> iterator = myProcessListeners.iterator(); iterator.hasNext();) {
          ProcessListener processListener = iterator.next();
          myExecution.getProcessHandler().addProcessListener(processListener);
        }
        myProcessListeners.clear();
      }
    }
    catch (ExecutionException e) {
      stop(false);
      throw e;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myExecution;
    }
    
    final Alarm debugPortTimeout = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

    myExecution.getProcessHandler().addProcessListener(new ProcessAdapter() {
      public void processTerminated(ProcessEvent event) {
        debugPortTimeout.cancelAllRequests();
      }

      public void startNotified(ProcessEvent event) {
        debugPortTimeout.addRequest(new Runnable() {
          public void run() {
            if(myState == STATE_INITIAL) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  String message = createConnectionStatusMessage(
                    "Check your run/debug configuration. Failed to establish connection to the target VM",
                    remoteConnection
                  );
                  Messages.showErrorDialog(myProject, message, "Cannot Debug Application");
                }
              });
            }
          }
        }, LOCAL_START_TIMEOUT);
      }
    });

    return myExecution;
  }

  private void createVirtualMachine(final boolean pollConnection) throws ExecutionException {
    final Semaphore semaphore = new Semaphore();
    final ExecutionException[] exception = new ExecutionException[1];

    myDebugProcessDispatcher.addListener(new DebugProcessAdapter() {
      public void connectorIsReady() {
        semaphore.up();
        myDebugProcessDispatcher.removeListener(this);
      }
    });

    final long time = System.currentTimeMillis();

    this.getManagerThread().invokeLater(new DebuggerCommandImpl() {
      protected void action() {
        VirtualMachine vm = null;

        try {
          while (System.currentTimeMillis() - time < LOCAL_START_TIMEOUT) {
            try {
              vm = createVirtualMachineInt();
              break;
            }
            catch (ExecutionException e) {
              if (pollConnection && !myConnection.isServerMode() && e.getCause() instanceof IOException) {
                synchronized (this) {
                  try {
                    wait(500);
                  }
                  catch (InterruptedException ie) {
                    break;
                  }
                }
              }
              else {
                exception[0] = e;
                break;
              }
            }
          }
        }
        finally {
          semaphore.up();
        }

        if(vm != null) {
          final VirtualMachine vm1 = vm;
          afterProcessStarted(new Runnable() {
            public void run() {
              getManagerThread().invokeLater(new DebuggerCommandImpl() {
                protected void action() throws Exception {
                  commitVM(vm1);
                }
              });
            }
          });
        }
      }
    });

    semaphore.down();
    semaphore.waitFor();

    if (exception[0] != null) throw exception[0];
  }

  private void afterProcessStarted(final Runnable run) {
    class MyProcessAdapter extends ProcessAdapter {
      private boolean alreadyRun = false;

      public synchronized void run() {
        if(!alreadyRun) {
          alreadyRun = true;
          run.run();
        }
        removeProcessListener(this);
      }

      public void startNotified(ProcessEvent event) {
        run();
      }
    }
    MyProcessAdapter processListener = new MyProcessAdapter();
    addProcessListener(processListener);
    if(myExecution != null) {
      if(myExecution.getProcessHandler().isStartNotified()) {
        processListener.run();
      }
    }
  }

  public DebuggerCommandImpl createPauseCommand() {
    return new PauseCommand();
  }

  public SuspendContextCommandImpl createResumeCommand(SuspendContextImpl suspendContext) {
    return new ResumeCommand(suspendContext);
  }

  public SuspendContextCommandImpl createStepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints) {
    return new StepOverCommand(suspendContext, ignoreBreakpoints);
  }

  public SuspendContextCommandImpl createStepOutCommand(SuspendContextImpl suspendContext) {
    return new StepOutCommand(suspendContext);
  }

  public SuspendContextCommandImpl createStepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters) {
    return new StepIntoCommand(suspendContext, ignoreFilters);
  }

  public SuspendContextCommandImpl createRunToCursorCommand(SuspendContextImpl suspendContext, Document document, int lineIndex)
    throws EvaluateException {
    RunToCursorCommand runToCursorCommand = new RunToCursorCommand(suspendContext, document, lineIndex);
    if(runToCursorCommand.myRunToCursorBreakpoint == null) {
      PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
      throw new EvaluateException("There is no executable code at " + psiFile.getName() + ":" + lineIndex, null);
    }
    return runToCursorCommand;
  }

  public DebuggerCommandImpl createFreezeThreadCommand(ThreadReferenceProxyImpl thread) {
    return new FreezeThreadCommand(thread);
  }

  public SuspendContextCommandImpl createResumeThreadCommand(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl thread) {
    return new ResumeThreadCommand(suspendContext, thread);
  }

  public SuspendContextCommandImpl createPopFrameCommand(DebuggerContextImpl context, StackFrameProxyImpl stackFrame) {
    return new PopFrameCommand(context, stackFrame);
  }
}

