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

  private SdkUtil() {
    // explicitly none
  }

  /**
   * A holder for stdout and stderr lines of a finished process.
   */
  public static class ProcessCallInfo {
    private List<String> myStdoutLines;
    private List<String> myStderrLines;
    private int exit_code;

    protected ProcessCallInfo(List<String> stdout_lines, List<String> stderr_lines, int exit_code) {
      myStdoutLines = stdout_lines;
      myStderrLines = stderr_lines;
    }

    public List<String> getStdout() {
      return myStdoutLines;
    }

    public List<String> getStderr() {
      return myStderrLines;
    }
    
    public int exitValue() {
      return exit_code;
    }
  }

  /**
   * Executes a process and returns its stdout and stderr outputs as lists of lines.
   * @param homePath process run directory
   * @param command command to execute and its arguments
   * @return a tuple of (stdout lines, stderr lines), lines in them have line terminators stripped, or may be null.
   */
  @NotNull
  public static ProcessCallInfo getProcessOutput(String homePath, @NonNls String[] command) {
    if (homePath == null || !new File(homePath).exists()) {
      return new ProcessCallInfo(null, null, -1);
    }
    List<String> stdout = null;
    List<String> stderr = null;
    int exit_code = -1;
    try {
      //noinspection HardCodedStringLiteral
      Application app = ApplicationManager.getApplication();
      Process process = Runtime.getRuntime().exec(command);
      
      ReadLinesThread stdout_thread = new ReadLinesThread(process.getInputStream());
      final Future<?> stdout_future = app.executeOnPooledThread(stdout_thread);
      
      ReadLinesThread stderr_thread = new ReadLinesThread(process.getErrorStream());
      final Future<?> stderr_future = app.executeOnPooledThread(stderr_thread);

      try {
        try {
          process.waitFor();
        }
        catch (InterruptedException e) {
          LOG.info(e);
          process.destroy();
        }
      }
      finally {
        try {
          stdout_future.get();
          stderr_future.get();
          stdout = stdout_thread.getResult();
          stderr = stderr_thread.getResult();
          exit_code = process.exitValue();
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
    private InputStream myStream;
    private List<String> my_lines = new ArrayList<String>();

    protected ReadLinesThread(InputStream stream) {
      myStream = stream;
    }

    public void run() {
      BufferedReader reader = new BufferedReader(new InputStreamReader(myStream)); // [dch] I wonder if it needs closing
      try {
        while (true) {
          String s = reader.readLine();
          if (s == null) break;
          my_lines.add(s);
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    
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
