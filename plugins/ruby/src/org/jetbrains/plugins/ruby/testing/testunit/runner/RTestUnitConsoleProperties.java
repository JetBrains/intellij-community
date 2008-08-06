package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.config.Storage;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestsRunConfiguration;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitConsoleProperties extends TestConsoleProperties {
  @NonNls private static final String PREFIX = "RubyTestUnitSupport.";
  private final RTestsRunConfiguration myConfiguration;

  public RTestUnitConsoleProperties(final RTestsRunConfiguration config)
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

  public RTestsRunConfiguration getConfiguration() {
    return myConfiguration;
  }
}
