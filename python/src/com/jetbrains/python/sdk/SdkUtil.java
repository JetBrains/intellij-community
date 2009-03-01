package com.jetbrains.python.sdk;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A more flexible cousin of SdkVersionUtil.
 * Needs not to be instantiated and only holds static methods.
 * @author dcheryasov
 * Date: Apr 24, 2008
 * Time: 1:19:47 PM
 */
public class SdkUtil {
  protected static final Logger LOG = Logger.getInstance("#com.jetbrains.python.sdk.SdkVersionUtil");

  private static final List<String> NO_LINES = new ArrayList<String>();

  private SdkUtil() {
    // explicitly none
  }

  /**
   * A holder for stdout and stderr lines of a finished process.
   */
  public static class ProcessCallInfo {
    private final List<String> myStdoutLines;
    private final List<String> myStderrLines;
    private final int myExitCode;

    public static final int TIMEOUT_CODE = -32768;

    protected ProcessCallInfo(List<String> stdout_lines, List<String> stderr_lines, int exit_code) {
      myStdoutLines = stdout_lines;
      myStderrLines = stderr_lines;
      myExitCode = exit_code;
    }

    public List<String> getStdout() {
      return myStdoutLines;
    }

    public List<String> getStderr() {
      return myStderrLines;
    }

    public int getExitValue() {
      return myExitCode;
    }
  }

  /**
   * Executes a process and returns its stdout and stderr outputs as lists of lines.
   * @param homePath process run directory
   * @param command command to execute and its arguments
   * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
   */
  @NotNull
  public static ProcessCallInfo getProcessOutput(String homePath, @NonNls String[] command) {
    return getProcessOutput(homePath, command, -1);
  }

  /**
   * Executes a process and returns its stdout and stderr outputs as lists of lines.
   * Waits for process for possibly limited duration.
   * @param homePath process run directory
   * @param command command to execute and its arguments
   * @param timeout how many milliseconds to wait until the process terminates; non-positive means inifinity.
   * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null. If
   * the process timed out, exit code is ProcessCallInfo.TIMEOUT_CODE.
   */
  @NotNull
  public static ProcessCallInfo getProcessOutput(String homePath, @NonNls String[] command, final int timeout) {
    if (homePath == null || !new File(homePath).exists()) {
      return new ProcessCallInfo(null, null, -1);
    }
    List<String> stdout = NO_LINES;
    List<String> stderr = NO_LINES;
    int exit_code = -1;
    try {
      //noinspection HardCodedStringLiteral
      Application app = ApplicationManager.getApplication();
      Process process = Runtime.getRuntime().exec(command);

      ReadLinesThread stdout_thread = new ReadLinesThread(process.getInputStream());
      final Future<?> stdout_future = app.executeOnPooledThread(stdout_thread);

      ReadLinesThread stderr_thread = new ReadLinesThread(process.getErrorStream());
      final Future<?> stderr_future = app.executeOnPooledThread(stderr_thread);

      final AtomicBoolean done = new AtomicBoolean(false);
      final AtomicBoolean timed_out = new AtomicBoolean(false);

      if (timeout > 0) {
        final Thread worker = Thread.currentThread();
        Runnable watchdog = new Runnable() {
          public void run() {
            try {
              Thread.sleep(timeout);
              if (! done.get()) {
                timed_out.set(true);
                worker.interrupt();
              }
            }
            catch (InterruptedException ignore) { }
          }
        };
        app.executeOnPooledThread(watchdog);
      }

      try {
        try {
          process.waitFor();
        }
        catch (InterruptedException e) {
          if (! timed_out.get()) {
            LOG.info(e);
          }
          process.destroy();
        }
      }
      finally {
        done.set(true);
        try {
          stdout_future.get();
          stderr_future.get();
          stdout = stdout_thread.getResult();
          stderr = stderr_thread.getResult();
          if (timed_out.get()){
            exit_code = process.exitValue();
          }
          else {
            exit_code = ProcessCallInfo.TIMEOUT_CODE;
          }
        }
        catch (Exception e) {
          LOG.info(e);
        }
      }
    }
    catch (IOException ex) {
      LOG.info(ex);
    }
    return new ProcessCallInfo(stdout, stderr, exit_code);
  }

  public static class ReadLinesThread implements Runnable {
    private final InputStream myStream;
    private final List<String> my_lines = new ArrayList<String>();

    protected ReadLinesThread(InputStream stream) {
      myStream = stream;
    }

    public void run() {
      BufferedReader reader = new BufferedReader(new InputStreamReader(myStream)); // NOTE: [dch] I wonder if it needs closing
      try {
        while (true) {
          String s = reader.readLine();
          if (s == null) break;
          my_lines.add(s);
        }
        reader.close();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    @NotNull
    public List<String> getResult() {
      return my_lines;
    }
   
  }
  
  /**
  * Finds the first match in a list os Strings.
  * @param lines list of lines, may be null.
  * @param regex pattern to match to.
  * @return pattern's first matched group, or entire matched string if pattern has no groups, or null.
  */
  @Nullable
  public static String getFirstMatch(List<String> lines, Pattern regex) {
    if (lines == null) return null;
    for (String s: lines) {
      Matcher m = regex.matcher(s);
      if (m.matches()) {
        if (m.groupCount() > 0)
          return m.group(1);
      }
      else return s;
    }
    return null;
  }

}
