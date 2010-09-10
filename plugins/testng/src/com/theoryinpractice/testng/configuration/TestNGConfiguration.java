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

/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 2, 2005
 * Time: 12:16:02 AM
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.xml.Parser;

import java.io.FileInputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TestNGConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule>
  implements CommonJavaRunConfigurationParameters, RefactoringListenerProvider {
  //private TestNGResultsContainer resultsContainer;
  protected TestData data;
  protected transient Project project;
  public boolean ALTERNATIVE_JRE_PATH_ENABLED;
  public String ALTERNATIVE_JRE_PATH;

  public static final String DEFAULT_PACKAGE_NAME = ExecutionBundle.message("default.package.presentable.name");
  public static final String DEFAULT_PACKAGE_CONFIGURATION_NAME = ExecutionBundle.message("default.package.configuration.name");
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

  private TestNGConfiguration(String s, Project project, TestData data, ConfigurationFactory factory) {
    super(s, new JavaRunConfigurationModule(project, false), factory);
    this.data = data;
    this.project = project;
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new TestNGRunnableState(env, this);
  }

  public TestData getPersistantData() {
    return data;
  }

  @Override
  protected ModuleBasedConfiguration createInstance() {
    try {
      return new TestNGConfiguration(getName(), getProject(), (TestData)data.clone(),
                                     TestNGConfigurationType.getInstance().getConfigurationFactories()[0]);
    }
    catch (CloneNotSupportedException e) {
      //can't happen right?
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public Collection<Module> getValidModules() {
    //TODO add handling for package
    return JavaRunConfigurationModule.getModulesForClass(getProject(), data.getMainClassName());
  }

  @Override
  public boolean isGeneratedName() {
    return data.isGeneratedName(getName(), getConfigurationModule());
  }

  @Override
  public String suggestedName() {
    if (TestType.CLASS.getType().equals(data.TEST_OBJECT)) {
      String shortName = JavaExecutionUtil.getShortClassName(data.MAIN_CLASS_NAME);
      return ProgramRunnerUtil.shortenName(shortName, 0);
    }
    if (TestType.PACKAGE.getType().equals(data.TEST_OBJECT)) {
      String s = getName();
      if (!isGeneratedName()) return '\"' + s + '\"';
      if (data.getPackageName().trim().length() > 0) {
        return "Tests in \"" + data.getPackageName() + '\"';
      }
      else {
        return "All Tests";
      }
    }
    if (TestType.METHOD.getType().equals(data.TEST_OBJECT)) {
      return data.getMethodName() + "()";
    }
    if (TestType.SUITE.getType().equals(data.TEST_OBJECT)) {
      return data.getSuiteName();
    }
    return data.getGroupName();
  }

  public void setVMParameters(String value) {
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
    return data.getWorkingDirectory(project);
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

  public void setClassConfiguration(PsiClass psiclass) {
    setModule(data.setMainClass(psiclass));
    data.TEST_OBJECT = TestType.CLASS.getType();
    setGeneratedName();
  }

  public void setPackageConfiguration(Module module, PsiPackage pkg) {
    data.setPackage(pkg);
    setModule(module);
    data.TEST_OBJECT = TestType.PACKAGE.getType();
    setGeneratedName();
  }

  public void setMethodConfiguration(Location<PsiMethod> location) {
    setModule(data.setTestMethod(location));
    setGeneratedName();
  }

  public void setGeneratedName() {
    setName(getGeneratedName());
  }

  public String getGeneratedName() {
    return data.getGeneratedName(getConfigurationModule());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<TestNGConfiguration> group = new SettingsEditorGroup<TestNGConfiguration>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new TestNGConfigurationEditor(getProject()));
    RunConfigurationExtension.appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<TestNGConfiguration>());
    return group;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (data.TEST_OBJECT.equals(TestType.CLASS.getType()) || data.TEST_OBJECT.equals(TestType.METHOD.getType())) {
      final SourceScope scope = data.getScope().getSourceScope(this);
      if (scope == null) {
        throw new RuntimeConfigurationException("Invalid scope specified");
      }
      PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(data.getMainClassName(), scope.getGlobalSearchScope());
      if (psiClass == null) throw new RuntimeConfigurationException("Class '" + data.getMainClassName() + "' not found");
      if (data.TEST_OBJECT.equals(TestType.METHOD.getType())) {
        PsiMethod[] methods = psiClass.findMethodsByName(data.getMethodName(), true);
        if (methods.length == 0) {
          throw new RuntimeConfigurationException("Method '" + data.getMethodName() + "' not found");
        }
        for (PsiMethod method : methods) {
          if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            throw new RuntimeConfigurationException("Non public method '" + data.getMethodName() + "'specified");
          }
        }
      }
    }
    else if (data.TEST_OBJECT.equals(TestType.PACKAGE.getType())) {
      PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(data.getPackageName());
      if (psiPackage == null) throw new RuntimeConfigurationException("Package '" + data.getMainClassName() + "' not found");
    }
    else if (data.TEST_OBJECT.equals(TestType.SUITE.getType())) {
      try {
        FileInputStream in = new FileInputStream(data.getSuiteName());
        try {
          new Parser(in).parse();//try to parse suite.xml
        }
        finally {
          in.close();
        }
      }
      catch (Exception e) {
        throw new RuntimeConfigurationException("Unable to parse '" + data.getSuiteName() + "' specified");
      }
    }
    RunConfigurationExtension.checkConfigurationIsValid(this);
    //TODO add various checks here
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    PathMacroManager.getInstance(getProject()).expandPaths(element);
    super.readExternal(element);
    RunConfigurationExtension.readSettings(this, element);
    readModule(element);
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
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    RunConfigurationExtension.writeSettings(this, element);
    writeModule(element);
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

    PathMacroManager.getInstance(getProject()).collapsePathsRecursively(element);
  }

  @Nullable
  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    if (data.TEST_OBJECT.equals(TestType.PACKAGE.getType())) {
      if (!(element instanceof PsiPackage)) return null;
      return RefactoringListeners.getListener((PsiPackage)element, myPackage);
    }
    else if (data.TEST_OBJECT.equals(TestType.CLASS.getType())) {
      if (!(element instanceof PsiClass)) return null;
      return RefactoringListeners.getClassOrPackageListener(element, myClass);
    }
    else if (data.TEST_OBJECT.equals(TestType.METHOD.getType())) {
      if (!(element instanceof PsiMethod)) {
        return RefactoringListeners.getClassOrPackageListener(element, myClass);
      }
      final PsiMethod method = (PsiMethod)element;
      if (!method.getName().equals(data.getMethodName())) return null;
      if (!method.getContainingClass().equals(myClass.getPsiElement())) return null;
      return new RefactoringElementListener() {
        public void elementMoved(@NotNull final PsiElement newElement) {
          setMethod((PsiMethod)newElement);
        }

        public void elementRenamed(@NotNull final PsiElement newElement) {
          setMethod((PsiMethod)newElement);
        }

        private void setMethod(final PsiMethod psiMethod) {
          final boolean generatedName = isGeneratedName();
          data.setTestMethod(PsiLocation.fromPsiElement(psiMethod));
          if (generatedName) setGeneratedName();
        }
      };
    }
    return null;
  }
}
