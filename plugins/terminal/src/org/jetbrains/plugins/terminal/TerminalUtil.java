// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteSshProcess;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.unix.UnixPtyProcess;
import com.pty4j.util.PtyUtil;
import com.pty4j.windows.WinPtyProcess;
import com.pty4j.windows.conpty.ConsoleProcessListChildProcessMain;
import com.pty4j.windows.conpty.WinConPtyProcess;
import com.sun.jna.Library;
import com.sun.jna.platform.win32.WinDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class TerminalUtil {

  private static final Logger LOG = Logger.getInstance(TerminalUtil.class);

  private TerminalUtil() {}

  public static boolean hasRunningCommands(@NotNull ProcessTtyConnector connector) throws IllegalStateException {
    Process process = connector.getProcess();
    if (!process.isAlive()) return false;
    if (process instanceof RemoteSshProcess) return true;
    if (SystemInfo.isUnix && process instanceof UnixPtyProcess) {
      int shellPid = OSProcessUtil.getProcessID(process);
      MultiMap<Integer, Integer> pidToChildPidsMap = MultiMap.create();
      UnixProcessManager.processPSOutput(UnixProcessManager.getPSCmd(false, false), s -> {
        StringTokenizer st = new StringTokenizer(s, " ");
        int parentPid = Integer.parseInt(st.nextToken());
        int pid = Integer.parseInt(st.nextToken());
        pidToChildPidsMap.putValue(parentPid, pid);
        return false;
      });
      return !pidToChildPidsMap.get(shellPid).isEmpty();
    }
    if (SystemInfo.isWindows && process instanceof WinPtyProcess) {
      WinPtyProcess winPty = (WinPtyProcess)process;
      try {
        String executable = FileUtil.toSystemIndependentName(StringUtil.notNullize(getExecutable(winPty)));
        int consoleProcessCount = winPty.getConsoleProcessCount();
        if (executable.endsWith("/Git/bin/bash.exe")) {
          return consoleProcessCount > 3;
        }
        return consoleProcessCount > 2;
      }
      catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    if (process instanceof WinConPtyProcess) {
      WinConPtyProcess conPtyProcess = (WinConPtyProcess)process;
      try {
        String executable = FileUtil.toSystemIndependentName(StringUtil.notNullize(ContainerUtil.getFirstItem(conPtyProcess.getCommand())));
        int consoleProcessCount = ConsoleProcessListFetcher.getConsoleProcessCount(conPtyProcess.pid());
        if (executable.endsWith("/Git/bin/bash.exe")) {
          return consoleProcessCount > 2;
        }
        return consoleProcessCount > 1;
      }
      catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    LOG.warn("Cannot determine if there are running processes: " + SystemInfo.OS_NAME + ", " + process.getClass().getName());
    return false;
  }

  private static @Nullable String getExecutable(@NotNull WinPtyProcess process) {
    return ContainerUtil.getFirstItem(process.getCommand());
  }
}

final class ConsoleProcessListFetcher {
  private static final Logger LOG = Logger.getInstance(ConsoleProcessListFetcher.class);
  private static final int TIMEOUT_MILLIS = 5000;
  private static final List<String> JAVA_OPTIONS_ENV_VARS = List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS");

  static int getConsoleProcessCount(long pid) throws IOException {
    List<String> args = new ArrayList<>(64);
    args.addAll(
      List.of(
        getPathToJavaExecutable(),
        //  tune JVM to behave more like a client VM for faster startup
        "-XX:TieredStopAtLevel=1", "-XX:CICompilerCount=1", "-XX:+UseSerialGC",
        "-XX:-UsePerfData", // disable the performance monitoring feature
        // Sometimes, ConsoleProcessListChildProcessMain and required dependencies could be packed in huge jars for better classloading performance.
        // For example, when lib\app.jar=438m and lib\3rd-party-rt.jar=73m, the JVM requires at least -Xmx17m to start.
        // Let's give it a bit more.
        "-Xms32m", "-Xmx64m"
        )
    );

    String libDir = System.getProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY);
    if (libDir != null) {
      args.add("-D" + PtyUtil.PREFERRED_NATIVE_FOLDER_KEY + "=" + libDir);
    }

    args.addAll(
      List.of(
        "-cp",
        buildClasspath(ConsoleProcessListChildProcessMain.class, Library.class, WinDef.DWORD.class),
        ConsoleProcessListChildProcessMain.class.getName(),
        String.valueOf(pid)
      )
    );
    ProcessBuilder builder = new ProcessBuilder(args);
    // ignore common Java cli options as it may slow down the VM startup
    JAVA_OPTIONS_ENV_VARS.forEach(builder.environment().keySet()::remove);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    StreamGobbler stdout = new StreamGobbler(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    try {
      process.waitFor(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ignored) {
    }
    if (process.isAlive()) {
      LOG.info("Terminating still running child process");
      process.destroy();
    }
    stdout.awaitReadingEnds();
    int exitCode;
    try {
      exitCode = process.exitValue();
    } catch (IllegalThreadStateException e) {
      throw new IOException("Still running child process");
    }
    if (exitCode != 0) {
      throw new IOException("Failed to get console process list: exit code " + exitCode + ", output: " + stdout.getText());
    }
    String processCountStr = getProcessCountStr(stdout.getText());
    try {
      int result = Integer.parseInt(processCountStr);
      if (result <= 1) {
        throw new IOException("Unexpected amount of console processes: " + result);
      }
      return result - 1; // minus "java ConsoleProcessListChildProcessMain" process
    } catch (NumberFormatException e) {
      throw new IOException("Failed to get console process list: cannot parse int from '" + processCountStr +
          "', all output: " + stdout.getText().trim());
    }
  }

  private static @NotNull String getProcessCountStr(@NotNull String stdout) throws IOException {
    int prefixInd = stdout.lastIndexOf("Process list count: ");
    if (prefixInd != -1) {
      int suffixInd = stdout.indexOf(" attached to the console", prefixInd);
      if (suffixInd != -1) {
        return stdout.substring(prefixInd + "Process list count: ".length(), suffixInd);
      }
    }
    throw new IOException("Cannot find process count in " + stdout);
  }

  private static @NotNull String getPathToJavaExecutable() throws IOException {
    Path javaHome = Path.of(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe");
    if (!Files.isRegularFile(javaHome)) {
      throw new IOException("No such executable " + javaHome);
    }
    return javaHome.toAbsolutePath().toString();
  }

  private static final class StreamGobbler implements Runnable {

    private final Reader myReader;
    private final StringBuilder myBuffer = new StringBuilder();
    private final Thread myThread;
    private boolean myIsStopped = false;

    private StreamGobbler(Reader reader) {
      myReader = reader;
      myThread = new Thread(this, "ConsoleProcessListFetcher output reader");
      myThread.start();
    }

    @Override
    public void run() {
      char[] buf = new char[8192];
      try {
        int readCount;
        while (!myIsStopped && (readCount = myReader.read(buf)) >= 0) {
          myBuffer.append(buf, 0, readCount);
        }
        if (myIsStopped) {
          myBuffer.append("Failed to read output: force stopped");
        }
      }
      catch (Exception e) {
        myBuffer.append("Failed to read output: ").append(e.getClass().getName()).append(" raised");
      }
    }

    private void awaitReadingEnds() {
      try {
        myThread.join(TIMEOUT_MILLIS); // await to read whole buffered output
      } catch (InterruptedException ignored) {
      }
      myIsStopped = true;
    }

    private String getText() {
      return myBuffer.toString();
    }
  }

  private static @NotNull String buildClasspath(Class<?> @NotNull... classes) {
    List<String> paths = Arrays.stream(classes).map(ConsoleProcessListFetcher::getJarPathForClass).toList();
    return String.join(";", new LinkedHashSet<>(paths));
  }

  private static String getJarPathForClass(@NotNull Class<?> aClass) {
    String resourceRoot = getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    return new File(Objects.requireNonNull(resourceRoot)).getAbsolutePath();
  }

  @Nullable
  private static String getResourceRoot(@NotNull Class<?> context, @NotNull String path) {
    URL url = context.getResource(path);
    if (url == null) {
      url = ClassLoader.getSystemResource(path.substring(1));
    }
    return url != null ? extractRoot(url, path) : null;
  }

  @NotNull
  private static String extractRoot(@NotNull URL resourceURL, @NotNull String resourcePath) {
    if (!resourcePath.startsWith("/")) {
      throw new IllegalStateException("precondition failed: " + resourcePath);
    }

    String resultPath = null;
    String protocol = resourceURL.getProtocol();
    if ("file".equals(protocol)) {
      String path = urlToFile(resourceURL).getPath();
      String testPath = path.replace('\\', '/');
      String testResourcePath = resourcePath.replace('\\', '/');
      if (testPath.endsWith(testResourcePath)) {
        resultPath = path.substring(0, path.length() - resourcePath.length());
      }
    }
    else if ("jar".equals(protocol)) {
      resultPath = getJarPath(resourceURL.getFile());
    }

    if (resultPath == null) {
      throw new IllegalStateException("Cannot extract '" + resourcePath + "' from '" + resourceURL + "', " + protocol);
    }
    return resultPath;
  }

  @NotNull
  private static File urlToFile(@NotNull URL url) {
    try {
      return new File(url.toURI().getSchemeSpecificPart());
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException("URL='" + url + "'", e);
    }
  }

  private static @Nullable String getJarPath(@NotNull String urlFilePart) {
    int pivot = urlFilePart.indexOf("!/");
    if (pivot < 0) {
      return null;
    }
    String fileUrlStr = urlFilePart.substring(0, pivot);

    String filePrefix = "file:";
    if (!fileUrlStr.startsWith(filePrefix)) {
      return fileUrlStr;
    }

    URL fileUrl;
    try {
      fileUrl = new URL(fileUrlStr);
    }
    catch (MalformedURLException e) {
      return null;
    }

    File result = urlToFile(fileUrl);
    return result.getPath().replace('\\', '/');
  }
}
