/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.console;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.console.pydev.*;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.containerview.PyViewNumericContainerAction;
import com.jetbrains.python.debugger.pydev.GetVariableCommand;
import com.jetbrains.python.debugger.pydev.LoadFullValueCommand;
import com.jetbrains.python.debugger.pydev.ProtocolParser;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Communication with Xml-rpc with the client.
 *
 * @author Fabio
 */
public class PydevConsoleCommunication extends AbstractConsoleCommunication implements XmlRpcHandler,
                                                                                       PyFrameAccessor {
  private static final String EXEC_LINE = "execLine";
  private static final String EXEC_MULTILINE = "execMultipleLines";
  private static final String GET_COMPLETIONS = "getCompletions";
  private static final String GET_DESCRIPTION = "getDescription";
  private static final String GET_FRAME = "getFrame";
  private static final String GET_VARIABLE = "getVariable";
  private static final String CHANGE_VARIABLE = "changeVariable";
  private static final String CONNECT_TO_DEBUGGER = "connectToDebugger";
  private static final String HANDSHAKE = "handshake";
  private static final String CLOSE = "close";
  private static final String EVALUATE = "evaluate";
  private static final String GET_ARRAY = "getArray";
  private static final String LOAD_FULL_VALUE = "loadFullValue";
  private static final String PYDEVD_EXTRA_ENVS = "PYDEVD_EXTRA_ENVS";

  /**
   * XML-RPC client for sending messages to the server.
   */
  private IPydevXmlRpcClient myClient;

  /**
   * This is the server responsible for giving input to a raw_input() requested.
   */
  @Nullable private MyWebServer myWebServer;

  private static final Logger LOG = Logger.getInstance(PydevConsoleCommunication.class.getName());

  /**
   * Input that should be sent to the server (waiting for raw_input)
   */
  protected volatile String inputReceived;
  /**
   * Response that should be sent back to the shell.
   */
  protected volatile InterpreterResponse nextResponse;
  /**
   * Helper to keep on busy loop.
   */
  private final Object lock2 = new Object();
  /**
   * Keeps a flag indicating that we were able to communicate successfully with the shell at least once
   * (if we haven't we may retry more than once the first time, as jython can take a while to initialize
   * the communication)
   */
  private volatile boolean firstCommWorked = false;

  private boolean myExecuting;
  private PythonDebugConsoleCommunication myDebugCommunication;
  private boolean myNeedsMore = false;

  private int myFullValueSeq = 0;
  private final Map<Integer, List<PyFrameAccessor.PyAsyncValue<String>>> myCallbackHashMap = new ConcurrentHashMap<>();

  private @Nullable PythonConsoleView myConsoleView;
  private final List<PyFrameListener> myFrameListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  /**
   * Initializes the xml-rpc communication.
   *
   * @param port    the port where the communication should happen.
   * @param process this is the process that was spawned (server for the XML-RPC)
   * @throws MalformedURLException
   */
  public PydevConsoleCommunication(Project project, int port, Process process, int clientPort) throws Exception {
    this(project, null, port, process, clientPort);
  }

  public PydevConsoleCommunication(Project project, String host, int port, Process process, int clientPort) throws Exception {
    super(project);

    //start the server that'll handle input requests
    myWebServer = new MyWebServer(clientPort);

    myWebServer.addHandler("$default", this);
    this.myWebServer.start();
    this.myClient = new PydevXmlRpcClient(process, host, port);

    PyDebugValueExecutionService executionService = PyDebugValueExecutionService.getInstance(myProject);
    executionService.sessionStarted(this);
    addFrameListener(new PyFrameListener() {
      @Override
      public void frameChanged() {
        executionService.cancelSubmittedTasks(PydevConsoleCommunication.this);
      }
    });
  }

  public boolean handshake() throws XmlRpcException {
    if (myClient != null) {
      Object ret = myClient.execute(HANDSHAKE, new Object[]{});
      if (ret instanceof String) {
        String retVal = (String)ret;
        return "PyCharm".equals(retVal);
      }
    }
    return false;
  }

  /**
   * Sends {@link #CLOSE} message to the Python console script.
   */
  private void sendCloseMessageToScript() {
    if (this.myClient != null) {
      new Task.Backgroundable(myProject, "Close Console Communication", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            PydevConsoleCommunication.this.myClient.execute(CLOSE, new Object[0]);
          }
          catch (Exception e) {
            //Ok, we can ignore this one on close.
          }
          PydevConsoleCommunication.this.myClient = null;
        }
      }.queue();
    }
  }

  /**
   * Stops the communication with the client (passes message for it to quit).
   */
  public synchronized void close() {
    sendCloseMessageToScript();
    PyDebugValueExecutionService.getInstance(myProject).sessionStopped(this);
    myCallbackHashMap.clear();

    if (myWebServer != null) {
      myWebServer.shutdown();
      myWebServer = null;
    }
  }

  /**
   * Stops the communication with the client (passes message for it to quit).
   *
   * @return {@link Future} that allows to wait for Python console server
   * thread {@link WebServer#listener} to die
   */
  @NotNull
  public synchronized Future<Void> closeAsync() {
    sendCloseMessageToScript();
    PyDebugValueExecutionService.getInstance(myProject).sessionStopped(this);
    myCallbackHashMap.clear();

    if (myWebServer != null) {
      Future<Void> shutdownFuture = myWebServer.shutdownAsync();
      myWebServer = null;
      return shutdownFuture;
    }

    return completedFuture();
  }

  /**
   * Variables that control when we're expecting to give some input to the server or when we're
   * adding some line to be executed
   */

  /**
   * Helper to keep on busy loop.
   */
  private final Object lock = new Object();


  /**
   * Called when the server is requesting some input from this class.
   */
  public Object execute(String method, Vector params) throws Exception {
    if ("NotifyFinished".equals(method)) {
      return execNotifyFinished((Boolean)params.get(0));
    }
    else if ("RequestInput".equals(method)) {
      return execRequestInput();
    }
    else if ("IPythonEditor".equals(method)) {
      return execIPythonEditor(params);
    }
    else if ("NotifyAboutMagic".equals(method)) {
      return execNotifyAboutMagic(params);
    }
    else if ("ShowConsole".equals(method)) {
      if (myConsoleView != null) {
        myConsoleView.setConsoleEnabled(true);
      }
      return "";
    }
    else if ("ReturnFullValue".equals(method)) {
      processFullValueResponse((Integer)params.get(0), (String)params.get(1));
      return "";
    }
    else {
      throw new UnsupportedOperationException();
    }
  }

  private void processFullValueResponse(int seq, String response) {
    final List<PyAsyncValue<String>> values = myCallbackHashMap.remove(seq);
    try {
      List<PyDebugValue> debugValues = ProtocolParser.parseValues(response, this);
      for (int i = 0; i < debugValues.size(); ++i) {
        PyDebugValue resultValue = debugValues.get(i);
        values.get(i).getCallback().ok(resultValue.getValue());
      }
    }
    catch (Exception e) {
      for (PyFrameAccessor.PyAsyncValue vars : values) {
        vars.getCallback().error(new PyDebuggerException(response));
      }
    }
  }

  private Object execNotifyAboutMagic(Vector params) {
    List<String> commands = (List<String>)params.get(0);
    boolean isAutoMagic = (Boolean)params.get(1);

    if (getConsoleFile() != null) {
      PythonConsoleData consoleData = PyConsoleUtil.getOrCreateIPythonData(getConsoleFile());
      consoleData.setIPythonAutomagic(isAutoMagic);
      consoleData.setIPythonMagicCommands(commands);
    }

    return "";
  }

  private Object execIPythonEditor(Vector params) {
    String path = (String)params.get(0);
    final VirtualFile file = StringUtil.isEmpty(path) ? null : LocalFileSystem.getInstance().findFileByPath(path);
    if (file != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        FileEditorManager.getInstance(myProject).openFile(file, true);
      });

      return Boolean.TRUE;
    }

    return Boolean.FALSE;
  }

  private Object execNotifyFinished(boolean more) {
    myNeedsMore = more;
    setExecuting(false);
    notifyCommandExecuted(more);
    return true;
  }

  private void setExecuting(boolean executing) {
    myExecuting = executing;
  }

  private Object execRequestInput() {
    waitingForInput = true;
    inputReceived = null;
    boolean needInput = true;

    //let the busy loop from execInterpreter free and enter a busy loop
    //in this function until execInterpreter gives us an input
    nextResponse = new InterpreterResponse(false, needInput);

    notifyInputRequested();

    //busy loop until we have an input
    while (inputReceived == null) {
      synchronized (lock) {
        try {
          lock.wait(10);
        }
        catch (InterruptedException e) {
          //pass
        }
      }
    }
    return inputReceived;
  }

  /**
   * Executes the needed command
   *
   * @param command
   * @return a Pair with (null, more) or (error, false)
   * @throws XmlRpcException
   */
  protected Pair<String, Boolean> exec(final ConsoleCodeFragment command) throws XmlRpcException {
    setExecuting(true);
    Object execute = myClient.execute(command.isSingleLine() ? EXEC_LINE : EXEC_MULTILINE, new Object[]{command.getText()});

    Object object;
    if (execute instanceof Vector) {
      object = ((Vector)execute).get(0);
    }
    else if (execute.getClass().isArray()) {
      object = ((Object[])execute)[0];
    }
    else {
      object = execute;
    }
    Pair<String, Boolean> result = parseResult(object);
    if (result.second) {
      setExecuting(false);
    }

    return result;
  }

  private Pair<String, Boolean> parseResult(Object object) {
    if (object instanceof Boolean) {
      return new Pair<>(null, (Boolean)object);
    }
    else {
      return parseExecResponseString(object.toString());
    }
  }

  /**
   * @return completions from the client
   */
  @NotNull
  public List<PydevCompletionVariant> getCompletions(String text, String actTok) throws Exception {
    if (myDebugCommunication != null && myDebugCommunication.isSuspended()) {
      return myDebugCommunication.getCompletions(text, actTok);
    }

    if (waitingForInput) {
      return Collections.emptyList();
    }
    final Object fromServer = myClient.execute(GET_COMPLETIONS, new Object[]{text, actTok});

    return PydevXmlUtils.decodeCompletions(fromServer, actTok);
  }

  /**
   * @return the description of the given attribute in the shell
   */
  public String getDescription(String text) throws Exception {
    if (myDebugCommunication != null && myDebugCommunication.isSuspended()) {
      return myDebugCommunication.getDescription(text);
    }
    if (waitingForInput) {
      return "Unable to get description: waiting for input.";
    }

    ThrowableComputable<String, Exception> doGetDesc = () -> myClient.execute(GET_DESCRIPTION, new Object[]{text}, 5000).toString();
    if (ApplicationManager.getApplication().isDispatchThread()) {
      return ProgressManager.getInstance().runProcessWithProgressSynchronously(doGetDesc, "Getting Description", true, myProject);
    }
    else {
      return doGetDesc.compute();
    }
  }

  /**
   * Executes a given line in the interpreter.
   *
   * @param command the command to be executed in the client
   */
  public void execInterpreter(final ConsoleCodeFragment command, final Function<InterpreterResponse, Object> onResponseReceived) {
    if (myDebugCommunication != null && myDebugCommunication.isSuspended()) {
      myDebugCommunication.execInterpreter(command, onResponseReceived);
      return; //TODO: handle text input and other cases
    }
    nextResponse = null;
    if (waitingForInput && myConsoleView != null && myConsoleView.isInitialized()) {
      inputReceived = command.getText();
      waitingForInput = false;
      //the thread that we started in the last exec is still alive if we were waiting for an input.
    }
    else {
      //create a thread that'll keep locked until an answer is received from the server.
      new Task.Backgroundable(myProject, "REPL Communication", true) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          boolean needInput = false;
          try {

            Pair<String, Boolean> executed = null;

            //the 1st time we'll do a connection attempt, we can try to connect n times (until the 1st time the connection
            //is accepted) -- that's mostly because the server may take a while to get started.
            int commAttempts = 0;
            while (true) {
              if (indicator.isCanceled()) {
                return;
              }

              executed = exec(command);

              //executed.o1 is not null only if we had an error

              String refusedConnPattern = "Failed to read servers response";
              // Was "refused", but it didn't
              // work on non English system
              // (in Spanish localized systems
              // it is "rechazada")
              // This string always works,
              // because it is hard-coded in
              // the XML-RPC library)
              if (executed.first != null && executed.first.indexOf(refusedConnPattern) != -1) {
                if (firstCommWorked) {
                  break;
                }
                else {
                  if (commAttempts < MAX_ATTEMPTS) {
                    commAttempts += 1;
                    Thread.sleep(250);
                    executed = Pair.create("", executed.second);
                  }
                  else {
                    break;
                  }
                }
              }
              else {
                break;
              }

              //unreachable code!! -- commented because eclipse will complain about it
              //throw new RuntimeException("Can never get here!");
            }

            firstCommWorked = true;

            boolean more = executed.second;

            nextResponse = new InterpreterResponse(more, needInput);
          }
          catch (ProcessCanceledException e) {
            //ignore
          }
          catch (Exception e) {
            nextResponse = new InterpreterResponse(false, needInput);
          }
        }
      }.queue();

      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Waiting for REPL Response") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          progressIndicator.setText("Waiting for REPL response with " + (int)(TIMEOUT / 10e8) + "s timeout");
          progressIndicator.setIndeterminate(false);
          final long startTime = System.nanoTime();
          while (nextResponse == null) {
            if (progressIndicator.isCanceled()) {
              LOG.debug("Canceled");
              nextResponse = new InterpreterResponse(false, false);
            }

            final long time = System.nanoTime() - startTime;
            progressIndicator.setFraction(((double)time) / TIMEOUT);
            if (time > TIMEOUT) {
              LOG.debug("Timeout exceeded");
              nextResponse = new InterpreterResponse(false, false);
            }
            synchronized (lock2) {
              try {
                lock2.wait(20);
              }
              catch (InterruptedException e) {
                LOG.error(e);
              }
            }
          }
          if (nextResponse.more) {
            myNeedsMore = true;
            notifyCommandExecuted(true);
          }
          onResponseReceived.fun(nextResponse);
        }
      });
    }
  }

  @Override
  public void interrupt() {
    try {
      myClient.execute("interrupt", new Object[]{});
    }
    catch (XmlRpcException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean isExecuting() {
    return myExecuting;
  }

  public boolean needsMore() {
    return myNeedsMore;
  }

  @Override
  public PyDebugValue evaluate(String expression, boolean execute, boolean doTrunc) throws PyDebuggerException {
    if (myClient != null) {
      try {
        Object ret = myClient.execute(EVALUATE, new Object[]{expression});
        if (ret instanceof String) {
          return ProtocolParser.parseValue((String)ret, this);
        }
        else {
          checkError(ret);
        }
      }
      catch (Exception e) {
        throw new PyDebuggerException("Evaluate in console failed", e);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public XValueChildrenList loadFrame() throws PyDebuggerException {
    if (myClient != null) {
      try {
        Object ret = myClient.execute(GET_FRAME, new Object[]{});
        if (ret instanceof String) {
          return parseVars((String)ret, null);
        }
        else {
          checkError(ret);
        }
      }
      catch (XmlRpcException e) {
        throw new PyDebuggerException("Get frame from console failed", e);
      }
    }
    return new XValueChildrenList();
  }

  public synchronized int getNextFullValueSeq() {
    myFullValueSeq++;
    return myFullValueSeq;
  }

  @Override
  public void loadAsyncVariablesValues(@NotNull List<PyAsyncValue<String>> pyAsyncValues) {
    PyDebugValueExecutionService.getInstance(myProject).submitTask(this, () -> {
      if (myClient != null) {
        try {
          List<String> evaluationExpressions = new ArrayList<>();
          for (PyAsyncValue<String> asyncValue : pyAsyncValues) {
            evaluationExpressions.add(GetVariableCommand.composeName(asyncValue.getDebugValue()));
          }
          final int seq = getNextFullValueSeq();
          myCallbackHashMap.put(seq, pyAsyncValues);
          Object ret = myClient
            .execute(LOAD_FULL_VALUE, new Object[]{seq, String.join(LoadFullValueCommand.NEXT_VALUE_SEPARATOR, evaluationExpressions)});

          if (ret instanceof String) {
            List<PyDebugValue> debugValues = ProtocolParser.parseValues((String)ret, this);
            for (int i = 0; i < pyAsyncValues.size(); ++i) {
              pyAsyncValues.get(i).getCallback().ok(debugValues.get(i).getValue());
            }
          }
          else {
            checkError(ret);
          }
        }
        catch (PyDebuggerException e) {
          if (myWebServer != null && !e.getMessage().startsWith("Console already exited")) {
            LOG.error(e);
          }
        }
        catch (XmlRpcException e) {
          for (PyAsyncValue<String> asyncValue : pyAsyncValues) {
            PyDebugValue value = asyncValue.getDebugValue();
            XValueNode node = value.getLastNode();
            if (node != null && !node.isObsolete()) {
              if (e.getMessage().startsWith("Timeout") || e.getMessage().startsWith("Console already exited")) {
                value.updateNodeValueAfterLoading(node, " ", "Timeout Exceeded");
              }
              else {
                LOG.error(e);
              }
            }
          }
        }
      }
    });
  }

  private XValueChildrenList parseVars(String ret, PyDebugValue parent) throws PyDebuggerException {
    final List<PyDebugValue> values = ProtocolParser.parseValues(ret, this);
    XValueChildrenList list = new XValueChildrenList(values.size());
    for (PyDebugValue v : values) {
      PyDebugValue value;
      if (parent != null) {
        value = new PyDebugValue(v);
        value.setParent(parent);
      }
      else {
        value = v;
      }
      list.add(v.getName(), value);
    }
    return list;
  }

  @Override
  public XValueChildrenList loadVariable(PyDebugValue var) throws PyDebuggerException {
    if (myClient != null) {
      try {
        Object ret = myClient.execute(GET_VARIABLE, new Object[]{GetVariableCommand.composeName(var)});
        if (ret instanceof String) {
          return parseVars((String)ret, var);
        }
        else {
          checkError(ret);
        }
      }
      catch (XmlRpcException e) {
        throw new PyDebuggerException("Get variable from console failed", e);
      }
    }
    return new XValueChildrenList();
  }

  @Override
  public void changeVariable(PyDebugValue variable, String value) throws PyDebuggerException {
    if (myClient != null) {
      try {
        // NOTE: The actual change is being scheduled in the exec_queue in main thread
        // This method is async now
        Object ret = myClient.execute(CHANGE_VARIABLE, new Object[]{variable.getEvaluationExpression(), value});
        checkError(ret);
      }
      catch (XmlRpcException e) {
        throw new PyDebuggerException("Get change variable", e);
      }
    }
  }

  @Nullable
  @Override
  public PyReferrersLoader getReferrersLoader() {
    return null;
  }

  @Override
  public ArrayChunk getArrayItems(PyDebugValue var, int rowOffset, int colOffset, int rows, int cols, String format)
    throws PyDebuggerException {
    if (myClient != null) {
      try {
        Object ret = myClient.execute(GET_ARRAY, new Object[]{var.getName(), rowOffset, colOffset, rows, cols, format});
        if (ret instanceof String) {
          return ProtocolParser.parseArrayValues((String)ret, this);
        }
        else {
          checkError(ret);
        }
      }
      catch (Exception e) {
        throw new PyDebuggerException("Evaluate in console failed", e);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePositionForName(String name, String parentType) {
    return null;
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePositionForType(String type) {
    return null;
  }

  /**
   * Request that pydevconsole connect (with pydevd) to the specified port
   *
   * @param localPort port for pydevd to connect to.
   * @param dbgOpts   additional debugger options (that are normally passed via command line) to apply
   * @param extraEnvs
   * @throws Exception if connection fails
   */
  public void connectToDebugger(int localPort, @NotNull Map<String, Boolean> dbgOpts, @NotNull Map<String, String> extraEnvs)
    throws Exception {
    if (waitingForInput) {
      throw new Exception("Can't connect debugger now, waiting for input");
    }
    /* argument needs to be hashtable type for compatability with the RPC library */
    Hashtable<String, Object> opts = new Hashtable<>(dbgOpts);
    opts.put(PYDEVD_EXTRA_ENVS, new Hashtable<>(extraEnvs));
    Object result = myClient.execute(CONNECT_TO_DEBUGGER, new Object[]{localPort, opts});
    Exception exception = null;
    if (result instanceof Vector) {
      Vector resultarray = (Vector)result;
      if (resultarray.size() == 1) {
        if ("connect complete".equals(resultarray.get(0))) {
          return;
        }
        if (resultarray.get(0) instanceof String) {
          exception = new Exception((String)resultarray.get(0));
        }
        if (resultarray.get(0) instanceof Exception) {
          exception = (Exception)resultarray.get(0);
        }
      }
    }
    throw new PyDebuggerException("pydevconsole failed to execute connectToDebugger", exception);
  }


  @Override
  public void notifyCommandExecuted(boolean more) {
    super.notifyCommandExecuted(more);
    for (PyFrameListener listener : myFrameListeners) {
      listener.frameChanged();
    }
  }

  private static void checkError(Object ret) throws PyDebuggerException {
    if (ret instanceof Object[] && ((Object[])ret).length == 1) {
      throw new PyDebuggerException(((Object[])ret)[0].toString());
    }
  }

  public void setDebugCommunication(PythonDebugConsoleCommunication debugCommunication) {
    myDebugCommunication = debugCommunication;
  }

  public PythonDebugConsoleCommunication getDebugCommunication() {
    return myDebugCommunication;
  }

  private static final class MyWebServer extends WebServer {
    public MyWebServer(int port) {
      super(port);
    }

    /**
     * Shutdowns the server and returns {@link Future} that allows to wait for
     * the server thread (i.e. {@link #listener}) to die after it gracefully
     * finished its work.
     *
     * @return {@link Future} that allows to wait for the server thread (i.e.
     * {@link #listener}) to die
     */
    @NotNull
    public synchronized Future<Void> shutdownAsync() {
      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      Thread thread = listener;
      shutdown();
      if (thread != null) {
        return ApplicationManager.getApplication().executeOnPooledThread(() -> {
          thread.join();
          return null;
        });
      }
      else {
        return completedFuture();
      }
    }
  }

  public void setConsoleView(@Nullable PythonConsoleView consoleView) {
    myConsoleView = consoleView;
  }

  @Override
  public void showNumericContainer(@NotNull PyDebugValue value) {
    PyViewNumericContainerAction.showNumericViewer(myProject, value);
  }

  @Override
  public void addFrameListener(@NotNull PyFrameListener listener) {
    myFrameListeners.add(listener);
  }

  @NotNull
  private static Future<Void> completedFuture() {
    return CompletableFuture.completedFuture(null);
  }
}
