package org.jetbrains.plugins.ruby.testing.sm.runner;

import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.config.Storage;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author: Roman Chernyatchik
 */
public class SMTRunnerConsoleProperties extends TestConsoleProperties {
  @NonNls private static final String PREFIX = "RubyTestUnitSupport.";
  private final RuntimeConfiguration myConfiguration;

  public SMTRunnerConsoleProperties(final RuntimeConfiguration config)
  {
    super(new Storage.PropertiesComponentStorage(PREFIX, PropertiesComponent.getInstance()), config.getProject());
    myConfiguration = config;
  }

  @Override
  public boolean isDebug() {
    return getDebugSession() != null;
  }

  @Override
  public boolean isPaused() {
    final XDebugSession debuggerSession = getDebugSession();
    return debuggerSession != null && debuggerSession.isPaused();
  }

  @Nullable
  public XDebugSession getDebugSession() {
    final XDebuggerManager debuggerManager = XDebuggerManager.getInstance(getProject());
    if (debuggerManager == null) {
      return null;
    }
    final XDebugSession[] sessions = debuggerManager.getDebugSessions();
    for (final XDebugSession debuggerSession : sessions) {
      if (getConsole() == debuggerSession.getRunContentDescriptor().getExecutionConsole()) {
        return debuggerSession;
      }
    }
    return null;
  }

  public RuntimeConfiguration getConfiguration() {
    return myConfiguration;
  }
}
