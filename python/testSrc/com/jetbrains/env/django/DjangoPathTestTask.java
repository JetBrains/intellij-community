package com.jetbrains.env.django;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.django.facet.DjangoFacet;
import com.jetbrains.django.fixtures.DjangoTestCase;
import com.jetbrains.django.manage.DjangoManageTask;
import com.jetbrains.django.ui.DjangoBundle;
import com.jetbrains.env.python.debug.PyExecutionFixtureTestTask;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.templateLanguages.TemplatesService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.util.Set;

/**
 * User : catherine
 */
public abstract class DjangoPathTestTask extends DjangoManageTestTask {

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/django/path/djangoPath";
  }

  @Override
  protected String getSubcommand() {
    return "validate";
  }
}
