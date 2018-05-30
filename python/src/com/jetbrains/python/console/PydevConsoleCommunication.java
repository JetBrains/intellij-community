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

import com.google.common.util.concurrent.SettableFuture;
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
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.console.pydev.AbstractConsoleCommunication;
import com.jetbrains.python.console.pydev.InterpreterResponse;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.console.thrift.server.TNettyServerTransport;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.containerview.PyViewNumericContainerAction;
import com.jetbrains.python.debugger.pydev.GetVariableCommand;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TTransport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.jetbrains.python.console.PydevConsoleCommunicationUtil.*;

/**
 * Communication with Xml-rpc with the client.
 *
 * @author Fabio
 */
public class PydevConsoleCommunication extends AbstractConsoleCommunication implements PyFrameAccessor {
  /**
   * Thrift RPC client for sending messages to the server.
   */
  private PythonConsole.Client myClient;

  /**
   * This is the server responsible for giving input to a raw_input() requested.
   */
  @Nullable private TThreadPoolServer myServer;

  private static final Logger LOG = Logger.getInstance(PydevConsoleCommunication.class);

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

  @Nullable private PythonConsoleView myConsoleView;
  private final List<PyFrameListener> myFrameListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @Nullable private XCompositeNode myCurrentRootNode;

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

    IDEHandler serverHandler = new IDEHandler();
    IDE.Processor<IDE.Iface> serverProcessor = new IDE.Processor<>(serverHandler);
    //noinspection IOResourceOpenedButNotSafelyClosed
    TNettyServerTransport serverTransport = new TNettyServerTransport(clientPort);
    TThreadPoolServer server = new TThreadPoolServer(
      new TThreadPoolServer.Args(serverTransport).processor(serverProcessor).protocolFactory(new TBinaryProtocol.Factory()));
    // @alexander todo do not `Thread.start()` here!
    new Thread(() -> server.serve()).start();
    Thread.sleep(1000L);

    TTransport clientTransport = serverTransport.getReverseTransport();
    TBinaryProtocol clientProtocol = new TBinaryProtocol(clientTransport);
    PythonConsole.Client client = new PythonConsole.Client(clientProtocol);

    this.myServer = server;
    this.myClient = client;

    PyDebugValueExecutionService executionService = PyDebugValueExecutionService.getInstance(myProject);
    executionService.sessionStarted(this);
    addFrameListener(new PyFrameListener() {
      @Override
      public void frameChanged() {
        executionService.cancelSubmittedTasks(PydevConsoleCommunication.this);
      }
    });
  }

  public boolean handshake() {
    if (myClient != null) {
      try {
        return "PyCharm".equals(myClient.handshake());
      }
      catch (TException e) {
        throw new RuntimeException(e);
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
            PydevConsoleCommunication.this.myClient.close();
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

    if (myServer != null) {
      myServer.stop();
      myServer = null;
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

    if (myServer != null) {
      /*
      Future<Void> shutdownFuture = myWebServer.shutdownAsync();
      */
      myServer.stop();
      myServer = null;

      // @alexander todo remove workaround and wait for the shutdown
      SettableFuture<Void> future = SettableFuture.create();
      future.set(null);
      return future;
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

  private void execNotifyAboutMagic(List<String> commands, boolean isAutoMagic) {
    if (getConsoleFile() != null) {
      PythonConsoleData consoleData = PyConsoleUtil.getOrCreateIPythonData(getConsoleFile());
      consoleData.setIPythonAutomagic(isAutoMagic);
      consoleData.setIPythonMagicCommands(commands);
    }
  }

  private boolean execIPythonEditor(String path) {
    final VirtualFile file = StringUtil.isEmpty(path) ? null : LocalFileSystem.getInstance().findFileByPath(path);
    if (file != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        FileEditorManager.getInstance(myProject).openFile(file, true);
      });

      return true;
    }

    return false;
  }

  private void execNotifyFinished(boolean more) {
    myNeedsMore = more;
    setExecuting(false);
    notifyCommandExecuted(more);
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
   */
  protected Pair<String, Boolean> exec(final ConsoleCodeFragment command) {
    setExecuting(true);

    boolean more;
    try {
      if (command.isSingleLine()) {
        more = myClient.execLine(command.getText());
      }
      else {
        more = myClient.execMultipleLines(command.getText());
      }
    }
    catch (TException e) {
      throw new RuntimeException(e);
    }

    if (more) {
      setExecuting(false);
    }

    return Pair.create(null, more);
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
    List<CompletionOption> fromServer = myClient.getCompletions(text, actTok);

    return fromServer.stream().map(option -> toPydevCompletionVariant(option)).collect(Collectors.toList());
  }

  @NotNull
  private static PydevCompletionVariant toPydevCompletionVariant(@NotNull CompletionOption option) {
    String args = option.arguments.stream().collect(Collectors.joining(" "));
    return new PydevCompletionVariant(option.name, option.documentation, args, option.type.getValue());
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

    // @alexander todo [regression] add timeout in 5s
    ThrowableComputable<String, Exception> doGetDesc = () -> myClient.getDescription(text);
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
      myClient.interrupt();
    }
    catch (TException e) {
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
        // @alexander todo make `evaluate` return the single value... or rewrite this code?
        // @alexander todo add specific exception to the method (previously processed by `checkError()`)
        List<DebugValue> debugValues = myClient.evaluate(expression);
        return createPyDebugValue(debugValues.iterator().next(), this);
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
        // @alexander todo add specific exception to the method (previously processed by `checkError()`)
        List<DebugValue> frame = myClient.getFrame();
        return parseVars(frame, null, this);
      }
      catch (TException e) {
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
          // @alexander todo add specific exception to the method (previously processed by `checkError()`)
          myClient.loadFullValue(seq, evaluationExpressions);

          myCallbackHashMap.put(seq, pyAsyncValues);

          // @alexander todo it was expected here that `loadFullValue()` might return `List<PyDebugValue>` somehow...
        }
        // @alexander todo uncomment probably
        /*
        catch (PyDebuggerException e) {
          if (myWebServer != null && !e.getMessage().startsWith("Console already exited")) {
            LOG.error(e);
          }
        }
        */
        catch (TException e) {
          for (PyAsyncValue<String> asyncValue : pyAsyncValues) {
            PyDebugValue value = asyncValue.getDebugValue();
            XValueNode node = value.getLastNode();
            if (node != null && !node.isObsolete()) {
              if (e.getMessage().startsWith("Timeout") || e.getMessage().startsWith("Console already exited")) {
                value.updateNodeValueAfterLoading(node, " ", "", PyVariableViewSettings.LOADING_TIMED_OUT);
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

  @Override
  public XValueChildrenList loadVariable(PyDebugValue var) throws PyDebuggerException {
    if (myClient != null) {
      try {
        // @alexander todo add specific exception to the method (previously processed by `checkError()`)
        List<DebugValue> ret = myClient.getVariable(GetVariableCommand.composeName(var));
        return parseVars(ret, var, this);
      }
      catch (TException e) {
        throw new PyDebuggerException("Get variable from console failed", e);
      }
    }
    return new XValueChildrenList();
  }

  @Override
  public void setCurrentRootNode(@NotNull XCompositeNode node) {
    myCurrentRootNode = node;
  }

  @Override
  @Nullable
  public XCompositeNode getCurrentRootNode() {
    return myCurrentRootNode;
  }

  @Override
  public void changeVariable(PyDebugValue variable, String value) throws PyDebuggerException {
    if (myClient != null) {
      try {
        // NOTE: The actual change is being scheduled in the exec_queue in main thread
        // This method is async now
        // @alexander todo add specific exception to the method (previously processed by `checkError()`)
        myClient.changeVariable(variable.getEvaluationExpression(), value);
      }
      catch (TException e) {
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
        // @alexander todo add specific exception to the method (previously processed by `checkError()`)
        GetArrayResponse ret = myClient.getArray(var.getName(), rowOffset, colOffset, rows, cols, format);
        return createArrayChunk(ret, this);
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
    try {
      // though `connectToDebugger` returns "connect complete" string, let us just ignore it
      myClient.connectToDebugger(localPort, dbgOpts, extraEnvs);
    }
    catch (TException e) {
      throw new PyDebuggerException("pydevconsole failed to execute connectToDebugger", e);
    }
  }


  @Override
  public void notifyCommandExecuted(boolean more) {
    super.notifyCommandExecuted(more);
    for (PyFrameListener listener : myFrameListeners) {
      listener.frameChanged();
    }
  }

  public void setDebugCommunication(PythonDebugConsoleCommunication debugCommunication) {
    myDebugCommunication = debugCommunication;
  }

  public PythonDebugConsoleCommunication getDebugCommunication() {
    return myDebugCommunication;
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

  private class IDEHandler implements IDE.Iface {

    @Override
    public void notifyFinished(boolean needsMoreInput) {
      execNotifyFinished(needsMoreInput);
    }

    @Override
    public String requestInput(String path) {
      return (String)execRequestInput();
    }

    @Override
    public void notifyAboutMagic(List<String> commands, boolean isAutoMagic) {
      execNotifyAboutMagic(commands, isAutoMagic);
    }

    @Override
    public void showConsole() {
      if (myConsoleView != null) {
        myConsoleView.setConsoleEnabled(true);
      }
    }

    @Override
    public void returnFullValue(int requestSeq, List<DebugValue> response) {
      final List<PyAsyncValue<String>> values = myCallbackHashMap.remove(requestSeq);
      try {
        List<PyDebugValue> debugValues = response.stream()
                                                 .map(value -> createPyDebugValue(value, PydevConsoleCommunication.this))
                                                 .collect(Collectors.toList());
        for (int i = 0; i < debugValues.size(); ++i) {
          PyDebugValue resultValue = debugValues.get(i);
          values.get(i).getCallback().ok(resultValue.getValue());
        }
      }
      catch (Exception e) {
        for (PyFrameAccessor.PyAsyncValue vars : values) {
          vars.getCallback().error(new PyDebuggerException(response.toString()));
        }
      }
    }

    @Override
    public boolean IPythonEditor(String path, String line) {
      return execIPythonEditor(path);
    }
  }
}
