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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaTestFrameworkRunnableState;
import com.intellij.execution.configurations.CompositeParameterTargetedValue;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.rt.execution.testFrameworks.ForkedDebuggerHelper;
import com.intellij.rt.testng.IDEATestNGListener;
import com.intellij.rt.testng.RemoteTestNGStarter;
import com.intellij.util.PathUtil;
import com.intellij.util.net.NetUtils;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.CommandLineArgs;

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
  protected String getFrameworkName() {
    return TESTNG_TEST_FRAMEWORK_NAME;
  }

  @Override
  protected boolean configureByModule(Module module) {
    return module != null && getConfiguration().getPersistantData().getScope() != TestSearchScope.WHOLE_PROJECT;
  }

  @Override
  protected void configureRTClasspath(JavaParameters javaParameters, Module module) {
    javaParameters.getClassPath().addFirst(PathUtil.getJarPathForClass(RemoteTestNGStarter.class));
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createJavaParameters();
    javaParameters.setMainClass("com.intellij.rt.testng.RemoteTestNGStarter");

    try {
      port = NetUtils.findAvailableSocketPort();
    }
    catch (IOException e) {
      throw new ExecutionException(TestngBundle.message("dialog.message.unable.to.bind.to.port", port), e);
    }

    final TestData data = getConfiguration().getPersistantData();

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

  @Override
  public @Nullable SearchForTestsTask createSearchingForTestsTask(@NotNull TargetEnvironment targetEnvironment) {
    return new SearchingForTestsTask(getServerSocket(), config, myTempFile) {
      @Override
      protected void onFound() {
        super.onFound();
        writeClassesPerModule(myClasses);
      }
    };
  }

  @SuppressWarnings("deprecation")
  @Override
  public @Nullable SearchForTestsTask createSearchingForTestsTask() {
    return createSearchingForTestsTask(new LocalTargetEnvironment(new LocalTargetEnvironmentRequest()));
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
        writeClassesPerModule(getConfiguration().getPackage(), getJavaParameters(), perModule, "");
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @Override
  @NotNull
  protected String getFrameworkId() {
    return "testng";
  }

  @Override
  protected void passTempFile(ParametersList parametersList, String tempFilePath) {
    parametersList.add(new CompositeParameterTargetedValue("-temp"));
    parametersList.add(new CompositeParameterTargetedValue().addPathPart(tempFilePath));
  }

  @Override
  @NotNull
  public TestNGConfiguration getConfiguration() {
    return config;
  }

  @Override
  protected TestSearchScope getScope() {
    return getConfiguration().getPersistantData().getScope();
  }

  @Override
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

   @Override
  protected void collectPackagesToOpen(List<String> options) {
    TestData data = getConfiguration().getPersistantData();
    if (TestType.METHOD.getType().equals(data.TEST_OBJECT) || TestType.CLASS.getType().equals(data.TEST_OBJECT)) {
      options.add(StringUtil.getPackageName(data.MAIN_CLASS_NAME));
    }
    else if (TestType.PACKAGE.getType().equals(data.TEST_OBJECT)){
      PsiPackage aPackage = JavaPsiFacade.getInstance(getConfiguration().getProject()).findPackage(data.PACKAGE_NAME);
      if (aPackage != null) {
        SourceScope sourceScope = data.getScope().getSourceScope(getConfiguration());
        if (sourceScope != null) {
          collectSubPackages(options, aPackage, sourceScope.getGlobalSearchScope());
        }
      }
    }
  }

  @Override
  protected boolean useModulePath() {
    return getConfiguration().isUseModulePath();
  }
}
