/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.console.pydev.*;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.debugger.PydevXmlUtils;
import com.jetbrains.python.debugger.pydev.GetVariableCommand;
import com.jetbrains.python.debugger.pydev.ProtocolParser;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

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
  private static final String HANDSHAKE = "handshake";
  private static final String CLOSE = "close";

  /**
   * XML-RPC client for sending messages to the server.
   */
  private IPydevXmlRpcClient myClient;

  /**
   * This is the server responsible for giving input to a raw_input() requested.
   */
  private WebServer myWebServer;

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
  private volatile Object lock2 = new Object();
  /**
   * Keeps a flag indicating that we were able to communicate successfully with the shell at least once
   * (if we haven't we may retry more than once the first time, as jython can take a while to initialize
   * the communication)
   */
  private volatile boolean firstCommWorked = false;

  private boolean myExecuting;

  /**
   * Initializes the xml-rpc communication.
   *
   * @param port    the port where the communication should happen.
   * @param process this is the process that was spawned (server for the XML-RPC)
   * @throws MalformedURLException
   */
  public PydevConsoleCommunication(Project project, int port, Process process, int clientPort) throws Exception {
    super(project);

    //start the server that'll handle input requests
    myWebServer = new WebServer(clientPort, null);
    myWebServer.addHandler("$default", this);
    this.myWebServer.start();

    this.myClient = new PydevXmlRpcClient(process, port);
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
   * Stops the communication with the client (passes message for it to quit).
   */
  public synchronized void close() {
    if (this.myClient != null) {
      new Task.Backgroundable(myProject, "Close console communication", true) {
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

    if (myWebServer != null) {
      myWebServer.shutdown();
      myWebServer = null;
    }
  }

  /**
   * Variables that control when we're expecting to give some input to the server or when we're
   * adding some line to be executed
   */

  /**
   * Helper to keep on busy loop.
   */
  private volatile Object lock = new Object();


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
    else {
      throw new UnsupportedOperationException();
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
    int line = Integer.parseInt((String)params.get(1));

    final VirtualFile file = StringUtil.isEmpty(path) ? null : LocalFileSystem.getInstance().findFileByPath(path);
    if (file != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          AccessToken at = ApplicationManager.getApplication().acquireReadActionLock();

          try {
            FileEditorManager.getInstance(myProject).openFile(file, true);
          }
          finally {
            at.finish();
          }
        }
      });

      return Boolean.TRUE;
    }

    return Boolean.FALSE;
  }

  private Object execNotifyFinished(boolean more) {
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
      return new Pair<String, Boolean>(null, (Boolean)object);
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
    if (waitingForInput) {
      return "Unable to get description: waiting for input.";
    }
    return myClient.execute(GET_DESCRIPTION, new Object[]{text}).toString();
  }

  /**
   * Executes a given line in the interpreter.
   *
   * @param command the command to be executed in the client
   */
  public void execInterpreter(final ConsoleCodeFragment command, final Function<InterpreterResponse, Object> onResponseReceived) {
    nextResponse = null;
    if (waitingForInput) {
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
                    executed = new Pair<String, Boolean>("", executed.second);
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
          catch (Exception e) {
            nextResponse = new InterpreterResponse(false, needInput);
          }
        }
      }.queue();


      //busy loop waiting for the answer (or having the console die).
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          progressIndicator.setText("Waiting for REPL response with " + (int)(TIMEOUT / 10e8) + "s timeout");
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
          onResponseReceived.fun(nextResponse);
        }
      }, "Waiting for REPL response", true, myProject);
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

  @Override
  public PyDebugValue evaluate(String expression, boolean execute, boolean doTrunc) throws PyDebuggerException {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
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

  private XValueChildrenList parseVars(String ret, PyDebugValue parent) throws PyDebuggerException {
    final List<PyDebugValue> values = ProtocolParser.parseValues(ret, this);
    XValueChildrenList list = new XValueChildrenList(values.size());
    for (PyDebugValue v : values) {
      list.add(v.getName(), parent != null ? v.setParent(parent) : v);
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
        Object ret = myClient.execute(CHANGE_VARIABLE, new Object[]{variable.getEvaluationExpression(), value});
        checkError(ret);
      }
      catch (XmlRpcException e) {
        throw new PyDebuggerException("Get change variable", e);
      }
    }
  }

  private static void checkError(Object ret) throws PyDebuggerException {
    if (ret instanceof Object[] && ((Object[])ret).length == 1) {
      throw new PyDebuggerException(((Object[])ret)[0].toString());
    }
  }
}
