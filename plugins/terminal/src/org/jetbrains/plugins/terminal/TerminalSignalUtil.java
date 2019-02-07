// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.process.UnixProcessManager;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TerminalSignalUtil {

  public static final int SIGQUIT = 3; // POSIX standard constant
  public static final int SIGPIPE = UnixProcessManager.getSignalNumber("PIPE"); // implementation-defined, not a POSIX standard

  private static final Logger LOG = Logger.getInstance(TerminalSignalUtil.class);

  private TerminalSignalUtil() {}

  /**
   * Computes callback with unset ignored handlers for given signals.
   * It can be helpful for creating child processes, since a child process created via fork inherits
   * its parent's signal disposition and the dispositions of ignored signals are left unchanged.
   *
   * @see <a href="http://man7.org/linux/man-pages/man7/signal.7.html">man signal</a>
   *
   * @param signals
   * @param callback
   * @return computed result
   */
  public static <T, E extends Exception> T computeWithIgnoredSignalsResetToDefault(int[] signals,
                                                                                   @NotNull ThrowableComputable<T, E> callback) throws E {
    if (!SystemInfo.isUnix || !JnaLoader.isLoaded()) {
      return callback.compute();
    }
    List<Integer> ignored = ContainerUtil.newArrayList();
    LibC lib = null;
    try {
      lib = Native.loadLibrary("c", LibC.class);
      for (int signal : signals) {
        Memory oldAction = allocSigactionStructMemory();
        if (signal >= 0 && lib.sigaction(signal, null, oldAction) == 0) {
          long handler = Native.POINTER_SIZE == 8 ? oldAction.getLong(0) : oldAction.getInt(0);
          if (handler == LibC.SIG_IGN && setSignalHandler(lib, signal, LibC.SIG_DFL)) {
            ignored.add(signal);
          }
        }
      }
    }
    catch (Throwable t) {
      LOG.warn("Cannot set ignored signals to default", t);
    }
    Throwable exception;
    try {
      return callback.compute();
    }
    catch (Throwable e) {
      exception = e;
    }
    finally {
      if (lib != null) {
        for (int signal : ignored) {
          try {
            setSignalHandler(lib, signal, LibC.SIG_IGN);
          }
          catch (Throwable t) {
            LOG.warn("Cannot restore ignored handler for signal " + signal, t);
          }
        }
      }
    }
    if (exception instanceof RuntimeException) {
      throw (RuntimeException)exception;
    }
    //noinspection unchecked
    throw (E)exception;
  }

  @SuppressWarnings("SpellCheckingInspection")
  @NotNull
  private static Memory allocSigactionStructMemory() {
    return new Memory(1024); // should be enough: sizeof(sigaction) is 152 bytes on a 64-bit machine
  }

  private static boolean setSignalHandler(@NotNull LibC lib, int signal, long SIG_DFL_or_IGN) {
    Memory action = allocSigactionStructMemory();
    if (Native.POINTER_SIZE == 8) {
      action.setLong(0, SIG_DFL_or_IGN);
    }
    else {
      action.setInt(0, (int)SIG_DFL_or_IGN);
    }
    boolean ok = lib.sigaction(signal, action, null) == 0;
    if (!ok) {
      LOG.warn("Cannot set signal disposition for signal " + signal + " to " +
               (SIG_DFL_or_IGN == LibC.SIG_IGN ? "ignored" : "default"));
    }
    return ok;
  }

  @SuppressWarnings("SpellCheckingInspection")
  private interface LibC extends Library {
    long SIG_DFL = 0L;
    long SIG_IGN = 1L;
    int sigaction(int signum, Pointer act, Pointer oldact);
  }
}
