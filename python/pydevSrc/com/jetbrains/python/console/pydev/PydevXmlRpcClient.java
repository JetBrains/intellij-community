package com.jetbrains.python.console.pydev;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.net.NetUtils;
import org.apache.xmlrpc.AsyncCallback;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Vector;

/**
 * Subclass of XmlRpcClient that will monitor the process so that if the process is destroyed, we stop waiting
 * for messages from it.
 *
 * @author Fabio
 */
public class PydevXmlRpcClient implements IPydevXmlRpcClient {

  /**
   * Internal xml-rpc client (responsible for the actual communication with the server)
   */
  private XmlRpcClient impl;

  /**
   * The process where the server is being executed.
   */
  private Process process;

  /**
   * This is the thread that's reading the error stream from the process.
   */
  private ThreadStreamReader stdErrReader;

  /**
   * This is the thread that's reading the output stream from the process.
   */
  private ThreadStreamReader stdOutReader;

  /**
   * ItelliJ Logging
   */
  private static final Logger LOG = Logger.getInstance(PydevXmlRpcClient.class.getName());


  /**
   * Constructor (see fields description)
   */
  public PydevXmlRpcClient(Process process, ThreadStreamReader stdErrReader, ThreadStreamReader stdOutReader, int port)
    throws MalformedURLException {
    this.impl = new XmlRpcClient(NetUtils.getLocalHostString(), port);
    this.process = process;
    this.stdErrReader = stdErrReader;
    this.stdOutReader = stdOutReader;
  }

  /**
   * Executes a command in the server.
   * <p/>
   * Within this method, we should be careful about being able to return if the server dies.
   * If we wanted to have a timeout, this would be the place to add it.
   *
   * @return the result from executing the given command in the server.
   */
  public Object execute(String command, Object[] args, long timeout) throws XmlRpcException {
    final Object[] result = new Object[]{null};

    //make an async call so that we can keep track of not actually having an answer.
    this.impl.executeAsync(command, new Vector(Arrays.asList(args)), new AsyncCallback() {

      public void handleError(Exception error, URL url, String method) {
        result[0] = new Object[]{error.getMessage()};
      }

      public void handleResult(Object recievedResult, URL url, String method) {
        result[0] = recievedResult;
      }
    });

    final ProgressManager progressManager = ProgressManager.getInstance();
    if (progressManager.hasProgressIndicator()){
      progressManager.getProgressIndicator().setText("Communicating with Pydev console with " + (int)(timeout/10e8) + "s timeout");
    }
    final long startTime = System.nanoTime();
    //busy loop waiting for the answer (or having the console die).
    while (result[0] == null) {
      try {
        ProgressManager.checkCanceled();
      }
      catch (ProcessCanceledException e) {
        result[0] = new Object[]{"Canceled"};
        break;
      }

      final long time = System.nanoTime() - startTime;
      if (progressManager.hasProgressIndicator()){
        progressManager.getProgressIndicator().setFraction(((double)time) / timeout);
      }
      if (time > timeout){
        LOG.debug("Timeout exceeded");
        result[0] = new Object[]{"Timeout exceeded"};
        break;
      }
      try {
        if (process != null) {
          final String errStream = stdErrReader.getContents();
          if (errStream.indexOf("sys.exit called. Interactive console finishing.") != -1) {
            result[0] = new Object[]{errStream};
            break;
          }

          int exitValue = process.exitValue();
          result[0] = new Object[]{String.format("Console already exited with value: %s while waiting for an answer.\n" +
                                                 "Error stream: " +
                                                 errStream +
                                                 "\n" +
                                                 "Output stream: " +
                                                 stdOutReader.getContents(), exitValue)};

          //ok, we have an exit value!
          break;
        }
      }
      catch (IllegalThreadStateException e) {
        //that's ok... let's sleep a bit
        synchronized (this) {
          try {
            wait(10);
          }
          catch (InterruptedException e1) {
            LOG.error(e1);
          }
        }
      }
    }
    return result[0];
  }
}
