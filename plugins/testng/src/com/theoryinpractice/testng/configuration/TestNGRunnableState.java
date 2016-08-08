/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.theoryinpractice.testng.configuration;

import com.beust.jcommander.JCommander;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaTestFrameworkRunnableState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.execution.testFrameworks.ForkedDebuggerHelper;
import com.intellij.util.PathUtil;
import com.intellij.util.net.NetUtils;
import com.theoryinpractice.testng.model.TestData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.testng.CommandLineArgs;
import org.testng.IDEATestNGListener;
import org.testng.RemoteTestNGStarter;
import org.testng.remote.RemoteArgs;
import org.testng.remote.RemoteTestNG;
import org.testng.remote.strprotocol.SerializedMessageSender;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TestNGRunnableState extends JavaTestFrameworkRunnableState<TestNGConfiguration> {
  private static final Logger LOG = Logger.getInstance("TestNG Runner");
  private static final String TESTNG_TEST_FRAMEWORK_NAME = "TestNG";
  private final TestNGConfiguration config;
  private int port;

  public TestNGRunnableState(ExecutionEnvironment environment, TestNGConfiguration config) {
    super(environment);
    this.config = config;
    //TODO need to narrow this down a bit
    //setModulesToCompile(ModuleManager.getInstance(config.getProject()).getModules());
  }

  @NotNull
  @Override
  protected OSProcessHandler startProcess() throws ExecutionException {
    final OSProcessHandler processHandler = new KillableColoredProcessHandler(createCommandLine());
    ProcessTerminatedListener.attach(processHandler);
    createSearchingForTestsTask().attachTaskToProcess(processHandler);
    return processHandler;
  }

  @NotNull
  @Override
  protected OSProcessHandler createHandler(Executor executor) throws ExecutionException {
    appendForkInfo(executor);
    return startProcess();
  }

  @NotNull
  @Override
  protected String getFrameworkName() {
    return TESTNG_TEST_FRAMEWORK_NAME;
  }

  @Override
  protected boolean configureByModule(Module module) {
    return module != null && getConfiguration().getPersistantData().getScope() != TestSearchScope.WHOLE_PROJECT;
  }

  @Override
  protected void configureRTClasspath(JavaParameters javaParameters) {
    javaParameters.getClassPath().add(PathUtil.getJarPathForClass(RemoteTestNGStarter.class));
    javaParameters.getClassPath().addTail(PathUtil.getJarPathForClass(JCommander.class));
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createJavaParameters();
    javaParameters.setMainClass("org.testng.RemoteTestNGStarter");

    try {
      port = NetUtils.findAvailableSocketPort();
    }
    catch (IOException e) {
      throw new ExecutionException("Unable to bind to port " + port, e);
    }

    final TestData data = getConfiguration().getPersistantData();

    javaParameters.getProgramParametersList().add(supportSerializationProtocol(getConfiguration()) ? RemoteArgs.PORT : CommandLineArgs.PORT, String.valueOf(port));

    if (data.getOutputDirectory() != null && !data.getOutputDirectory().isEmpty()) {
      javaParameters.getProgramParametersList().add(CommandLineArgs.OUTPUT_DIRECTORY, data.getOutputDirectory());
    }

    javaParameters.getProgramParametersList().add(CommandLineArgs.USE_DEFAULT_LISTENERS, String.valueOf(data.USE_DEFAULT_REPORTERS));

    @NonNls final StringBuilder buf = new StringBuilder();
    if (data.TEST_LISTENERS != null && !data.TEST_LISTENERS.isEmpty()) {
      buf.append(StringUtil.join(data.TEST_LISTENERS, ";"));
    }
    collectListeners(javaParameters, buf, IDEATestNGListener.EP_NAME, ";");
    if (buf.length() > 0) javaParameters.getProgramParametersList().add(CommandLineArgs.LISTENER, buf.toString());

    createServerSocket(javaParameters);
    createTempFiles(javaParameters);
    return javaParameters;
  }

  @Override
  protected List<String> getNamedParams(String parameters) {
    try {
      Integer.parseInt(parameters);
      return super.getNamedParams(parameters);
    }
    catch (NumberFormatException e) {
      return Arrays.asList(parameters.split(" "));
    }
  }

  @NotNull
  @Override
  protected String getForkMode() {
    return "none";
  }

  public SearchingForTestsTask createSearchingForTestsTask() {
    return new SearchingForTestsTask(myServerSocket, config, myTempFile) {
      @Override
      protected void onFound() {
        super.onFound();
        writeClassesPerModule(myClasses);
      }
    };
  }

  protected void writeClassesPerModule(Map<PsiClass, Map<PsiMethod, List<String>>> classes) {
    if (forkPerModule()) {
      final Map<Module, List<String>> perModule = new TreeMap<>((o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true));

      for (final PsiClass psiClass : classes.keySet()) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
        if (module != null) {
          List<String> list = perModule.get(module);
          if (list == null) {
            list = new ArrayList<>();
            perModule.put(module, list);
          }
          list.add(psiClass.getQualifiedName());
        }
      }

      try {
        writeClassesPerModule(getConfiguration().getPackage(), getJavaParameters(), perModule);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public static boolean supportSerializationProtocol(TestNGConfiguration config) {
    final Project project = config.getProject();
    final GlobalSearchScope scopeToDetermineTestngIn;
    if (config.getPersistantData().getScope() == TestSearchScope.WHOLE_PROJECT) {
      scopeToDetermineTestngIn = GlobalSearchScope.allScope(project);
    }
    else {
      final Module module = config.getConfigurationModule().getModule();
      scopeToDetermineTestngIn = module != null ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) 
                                                : GlobalSearchScope.allScope(project);
    }

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass aClass = facade.findClass(SerializedMessageSender.class.getName(), scopeToDetermineTestngIn);
    if (aClass == null) return false;

    final PsiClass[] starters = facade.findClasses(RemoteTestNG.class.getName(), scopeToDetermineTestngIn);
    for (PsiClass starter : starters) {
      if (starter.findFieldByName("m_serPort", false) == null) {
        LOG.info("Multiple TestNG versions found");
        return false;
      }
    }
    return Registry.is("testng.serialized.protocol.enabled") && !TestNGVersionChecker.isVersionIncompatible(project, scopeToDetermineTestngIn);
  }

  @NotNull
  protected String getFrameworkId() {
    return "testng";
  }

  protected void passTempFile(ParametersList parametersList, String tempFilePath) {
    parametersList.add("-temp", tempFilePath);
  }

  @NotNull
  public TestNGConfiguration getConfiguration() {
    return config;
  }

  @Override
  protected TestSearchScope getScope() {
    return getConfiguration().getPersistantData().getScope();
  }

  protected void passForkMode(String forkMode, File tempFile, JavaParameters parameters) throws ExecutionException {
    final ParametersList parametersList = parameters.getProgramParametersList();
    final List<String> params = parametersList.getParameters();
    int paramIdx = params.size() - 1;
    for (int i = 0; i < params.size(); i++) {
      String param = params.get(i);
      if ("-temp".equals(param)) {
        paramIdx = i;
        break;
      }
    }
    parametersList.addAt(paramIdx, "@@@" + tempFile.getAbsolutePath());
    if (getForkSocket() != null) {
      parametersList.addAt(paramIdx, ForkedDebuggerHelper.DEBUG_SOCKET + getForkSocket().getLocalPort());
    }
  }
}
