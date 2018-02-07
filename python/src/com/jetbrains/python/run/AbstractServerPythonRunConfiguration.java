/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
abstract public class AbstractServerPythonRunConfiguration<T extends AbstractServerPythonRunConfiguration<T>> extends AbstractPythonRunConfiguration<T>{
  @NonNls private static final String LAUNCH_JAVASCRIPT_DEBUGGER = "launchJavascriptDebuger";
  private boolean myLaunchJavascriptDebugger;

  public AbstractServerPythonRunConfiguration(final Project project, final ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    setLaunchJavascriptDebugger(Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, LAUNCH_JAVASCRIPT_DEBUGGER)));
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, LAUNCH_JAVASCRIPT_DEBUGGER, Boolean.toString(isLaunchJavascriptDebugger()));
  }

  public boolean isLaunchJavascriptDebugger() {
    return myLaunchJavascriptDebugger;
  }

  public void setLaunchJavascriptDebugger(boolean launchJavascriptDebugger) {
    myLaunchJavascriptDebugger = launchJavascriptDebugger;
  }
}
