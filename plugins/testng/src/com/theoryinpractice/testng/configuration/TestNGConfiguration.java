/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.theoryinpractice.testng.configuration;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.UndoRefactoringElementListener;
import com.theoryinpractice.testng.configuration.testDiscovery.TestNGTestDiscoveryRunnableState;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;
import com.theoryinpractice.testng.model.TestNGTestObject;
import com.theoryinpractice.testng.model.TestType;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class TestNGConfiguration extends JavaTestConfigurationBase {
  @NonNls private static final String PATTERNS_EL_NAME = "patterns";
  @NonNls private static final String PATTERN_EL_NAME = "pattern";
  @NonNls private static final String TEST_CLASS_ATT_NAME = "testClass";

  //private TestNGResultsContainer resultsContainer;
  protected TestData data;
  protected transient Project project;
  public boolean ALTERNATIVE_JRE_PATH_ENABLED;
  public String ALTERNATIVE_JRE_PATH;

  private final RefactoringListeners.Accessor<PsiPackage> myPackage = new RefactoringListeners.Accessor<PsiPackage>() {
    public void setName(final String qualifiedName) {
      final boolean generatedName = isGeneratedName();
      data.PACKAGE_NAME = qualifiedName;
      if (generatedName) setGeneratedName();
    }

    @Nullable
    public PsiPackage getPsiElement() {
      final String qualifiedName = data.getPackageName();
      return qualifiedName != null ? JavaPsiFacade.getInstance(getProject()).findPackage(qualifiedName) : null;
    }

    public void setPsiElement(final PsiPackage psiPackage) {
      setName(psiPackage.getQualifiedName());
    }
  };

  private final RefactoringListeners.Accessor<PsiClass> myClass = new RefactoringListeners.Accessor<PsiClass>() {
    public void setName(final String qualifiedName) {
      final boolean generatedName = isGeneratedName();
      data.MAIN_CLASS_NAME = qualifiedName;
      if (generatedName) setGeneratedName();
    }

    @Nullable
    public PsiClass getPsiElement() {
      final String qualifiedName = data.getMainClassName();
      return qualifiedName != null
             ? JavaPsiFacade.getInstance(getProject()).findClass(qualifiedName, GlobalSearchScope.allScope(project))
             : null;
    }

    public void setPsiElement(final PsiClass psiClass) {
      setName(psiClass.getQualifiedName());
    }
  };

  public TestNGConfiguration(String s, Project project, ConfigurationFactory factory) {
    this(s, project, new TestData(), factory);
  }

  protected TestNGConfiguration(String s, Project project, TestData data, ConfigurationFactory factory) {
    super(s, new JavaRunConfigurationModule(project, false), factory);
    this.data = data;
    this.project = project;
  }

  @Nullable
  public RemoteConnectionCreator getRemoteConnectionCreator() {
    return null;
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) {
    final TestData data = getPersistantData();
    if (data.TEST_OBJECT.equals(TestType.SOURCE.getType()) || data.getChangeList() != null) {
      return new TestNGTestDiscoveryRunnableState(env, this);
    }
    return new TestNGRunnableState(env, this);
  }

  public TestData getPersistantData() {
    return data;
  }

  @Override
  public Collection<Module> getValidModules() {
    //TODO add handling for package
    return JavaRunConfigurationModule.getModulesForClass(getProject(), data.getMainClassName());
  }

  @Override
  public String suggestedName() {
    final TestNGTestObject testObject = TestNGTestObject.fromConfig(this);
    return testObject != null ? testObject.getGeneratedName() : null;
  }

  @Override
  public String getActionName() {
    final TestNGTestObject testObject = TestNGTestObject.fromConfig(this);
    return testObject != null ? testObject.getActionName() : null;
  }

  public void setVMParameters(@Nullable String value) {
    data.setVMParameters(value);
  }

  public String getVMParameters() {
    return data.getVMParameters();
  }

  public void setProgramParameters(String value) {
    data.setProgramParameters(value);
  }

  public String getProgramParameters() {
    return data.getProgramParameters();
  }

  public void setWorkingDirectory(String value) {
    data.setWorkingDirectory(value);
  }

  public String getWorkingDirectory() {
    return data.getWorkingDirectory();
  }

  public void setEnvs(@NotNull Map<String, String> envs) {
    data.setEnvs(envs);
  }

  @NotNull
  public Map<String, String> getEnvs() {
    return data.getEnvs();
  }

  public void setPassParentEnvs(boolean passParentEnvs) {
    data.PASS_PARENT_ENVS = passParentEnvs;
  }

  public boolean isPassParentEnvs() {
    return data.PASS_PARENT_ENVS;
  }

  public boolean isAlternativeJrePathEnabled() {
     return ALTERNATIVE_JRE_PATH_ENABLED;
   }

   public void setAlternativeJrePathEnabled(boolean enabled) {
     this.ALTERNATIVE_JRE_PATH_ENABLED = enabled;
   }

   @Nullable
   public String getAlternativeJrePath() {
     return ALTERNATIVE_JRE_PATH;
   }

   public void setAlternativeJrePath(String path) {
     this.ALTERNATIVE_JRE_PATH = path;
   }

  public String getRunClass() {
    return !data.TEST_OBJECT.equals(TestType.CLASS.getType()) && !data.TEST_OBJECT.equals(TestType.METHOD.getType()) ? null : data.getMainClassName();
  }

  public String getPackage() {
    return !data.TEST_OBJECT.equals(TestType.PACKAGE.getType()) ? null : data.getPackageName();
  }

  public void beClassConfiguration(PsiClass psiclass) {
    setModule(data.setMainClass(psiclass));
    data.TEST_OBJECT = TestType.CLASS.getType();
    setGeneratedName();
  }

  @Override
  public boolean isConfiguredByElement(PsiElement element) {
    return TestNGTestObject.fromConfig(this).isConfiguredByElement(element);
  }

  @Override
  public String prepareParameterizedParameter(String paramSetName) {
    return TestNGConfigurationProducer.getInvocationNumber(paramSetName);
  }

  @Override
  public TestSearchScope getTestSearchScope() {
    return getPersistantData().getScope();
  }

  public void setPackageConfiguration(Module module, PsiPackage pkg) {
    data.setPackage(pkg);
    setModule(module);
    data.TEST_OBJECT = TestType.PACKAGE.getType();
    setGeneratedName();
  }

  public void beMethodConfiguration(Location<PsiMethod> location) {
    setModule(data.setTestMethod(location));
    setGeneratedName();
  }

  @Deprecated
  public void setClassConfiguration(PsiClass psiclass) {
    setModule(data.setMainClass(psiclass));
    data.TEST_OBJECT = TestType.CLASS.getType();
    setGeneratedName();
  }

  @Deprecated
  public void setMethodConfiguration(Location<PsiMethod> location) {
    setModule(data.setTestMethod(location));
    setGeneratedName();
  }

  public void bePatternConfiguration(List<PsiClass> classes, PsiMethod method) {
    data.TEST_OBJECT = TestType.PATTERN.getType();
    final String suffix;
    if (method != null) {
      data.METHOD_NAME = method.getName();
      suffix = "," + data.METHOD_NAME;
    } else {
      suffix = "";
    }
    LinkedHashSet<String> patterns = new LinkedHashSet<>();
    for (PsiClass pattern : classes) {
      patterns.add(JavaExecutionUtil.getRuntimeQualifiedName(pattern) + suffix);
    }
    data.setPatterns(patterns);
    final Module module = RunConfigurationProducer.getInstance(AbstractTestNGPatternConfigurationProducer.class)
      .findModule(this, getConfigurationModule().getModule(), patterns);
    if (module == null) {
      data.setScope(TestSearchScope.WHOLE_PROJECT);
      setModule(null);
    }
    else {
      setModule(module);
    }
    setGeneratedName();
  }

  @NotNull
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<TestNGConfiguration> group = new SettingsEditorGroup<>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"),
                    new TestNGConfigurationEditor<>(getProject()));
    JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
    return group;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    final TestNGTestObject testObject = TestNGTestObject.fromConfig(this);
    testObject.checkConfiguration();
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
    ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), getConfigurationModule().getModule());
    JavaParametersUtil.checkAlternativeJRE(this);
    //TODO add various checks here
  }

  @Override
  public void readExternal(@NotNull Element element) {
    super.readExternal(element);
    JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);
    DefaultJDOMExternalizer.readExternal(this, element);
    DefaultJDOMExternalizer.readExternal(getPersistantData(), element);
    EnvironmentVariablesComponent.readExternal(element, getPersistantData().getEnvs());

    Map<String, String> properties = getPersistantData().TEST_PROPERTIES;
    properties.clear();
    Element propertiesElement = element.getChild("properties");
    if (propertiesElement != null) {
      List<Element> children = propertiesElement.getChildren("property");
      for (Element property : children) {
        properties.put(property.getAttributeValue("name"), property.getAttributeValue("value"));
      }
    }

    List<String> listeners = getPersistantData().TEST_LISTENERS;
    listeners.clear();
    Element listenersElement = element.getChild("listeners");
    if (listenersElement != null) {
      List<Element> children = listenersElement.getChildren("listener");
      for (Element listenerClassName : children) {
        listeners.add(listenerClassName.getAttributeValue("class"));
      }
    }
    final Element patternsElement = element.getChild(PATTERNS_EL_NAME);
    if (patternsElement != null) {
      final LinkedHashSet<String> tests = new LinkedHashSet<>();
      for (Element patternElement : patternsElement.getChildren(PATTERN_EL_NAME)) {
        tests.add(patternElement.getAttributeValue(TEST_CLASS_ATT_NAME));
      }
      getPersistantData().setPatterns(tests);
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    DefaultJDOMExternalizer.writeExternal(getPersistantData(), element);
    EnvironmentVariablesComponent.writeExternal(element, getPersistantData().getEnvs());

    Element propertiesElement = element.getChild("properties");
    if (propertiesElement == null) {
      propertiesElement = new Element("properties");
      element.addContent(propertiesElement);
    }

    Map<String, String> properties = getPersistantData().TEST_PROPERTIES;
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      Element property = new Element("property");
      property.setAttribute("name", entry.getKey());
      property.setAttribute("value", entry.getValue());
      propertiesElement.addContent(property);
    }

    Element listenersElement = element.getChild("listeners");
    if (listenersElement == null) {
      listenersElement = new Element("listeners");
      element.addContent(listenersElement);
    }

    List<String> listeners = getPersistantData().TEST_LISTENERS;
    for (String listener : listeners) {
      Element listenerElement = new Element("listener");
      listenerElement.setAttribute("class", listener);
      listenersElement.addContent(listenerElement);
    }
    final Set<String> patterns = getPersistantData().getPatterns();
    if (!patterns.isEmpty()) {
      final Element patternsElement = new Element(PATTERNS_EL_NAME);
      for (String o : patterns) {
        final Element patternElement = new Element(PATTERN_EL_NAME);
        patternElement.setAttribute(TEST_CLASS_ATT_NAME, o);
        patternsElement.addContent(patternElement);
      }
      element.addContent(patternsElement);
    }
  }

  @Nullable
  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    if (data.TEST_OBJECT.equals(TestType.PACKAGE.getType())) {
      if (!(element instanceof PsiPackage)) return null;
      final RefactoringElementListener listener = RefactoringListeners.getListener((PsiPackage)element, myPackage);
      return RunConfigurationExtension.wrapRefactoringElementListener(element, this, listener);
    }
    else if (data.TEST_OBJECT.equals(TestType.CLASS.getType())) {
      if (!(element instanceof PsiClass) && !(element instanceof PsiPackage)) return null;
      final RefactoringElementListener listener = RefactoringListeners.getClassOrPackageListener(element, myClass);
      return RunConfigurationExtension.wrapRefactoringElementListener(element, this, listener);
    }
    else if (data.TEST_OBJECT.equals(TestType.METHOD.getType())) {
      if (!(element instanceof PsiMethod)) {
        final RefactoringElementListener listener = RefactoringListeners.getClassOrPackageListener(element, myClass);
        return RunConfigurationExtension.wrapRefactoringElementListener(element, this, listener);
      }
      final PsiMethod method = (PsiMethod)element;
      if (!method.getName().equals(data.getMethodName())) return null;
      if (!method.getContainingClass().equals(myClass.getPsiElement())) return null;
      class Listener extends RefactoringElementAdapter implements UndoRefactoringElementListener {
        public void elementRenamedOrMoved(@NotNull final PsiElement newElement) {
          data.setTestMethod(PsiLocation.fromPsiElement((PsiMethod)newElement));
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          final int methodIdx = oldQualifiedName.indexOf("#") + 1;
          if (methodIdx <= 0 || methodIdx >= oldQualifiedName.length()) return;
          data.METHOD_NAME = oldQualifiedName.substring(methodIdx);
        }
      }
      return RunConfigurationExtension.wrapRefactoringElementListener(element, this, new Listener());
    }
    return null;
  }

  @Override
  public boolean collectOutputFromProcessHandler() {
    return false;
  }

  @Override
  public SMTRunnerConsoleProperties createTestConsoleProperties(Executor executor) {
    return new TestNGConsoleProperties(this, executor);
  }

  @NotNull
  @Override
  public String getFrameworkPrefix() {
    return "g";
  }

  @Nullable
  public Set<String> calculateGroupNames() {
    if (!TestType.GROUP.getType().equals(data.TEST_OBJECT)) {
      return null;
    }

    Set<String> groups = StringUtil.split(data.getGroupName(), ",").stream()
      .map(String::trim)
      .filter(StringUtil::isNotEmpty)
      .collect(Collectors.toSet());
    return groups.isEmpty() ? null : groups;
  }

  public void beFromSourcePosition(PsiLocation<PsiMethod> position) {
    beMethodConfiguration(position);
    getPersistantData().TEST_OBJECT = TestType.SOURCE.getType();
  }
}
