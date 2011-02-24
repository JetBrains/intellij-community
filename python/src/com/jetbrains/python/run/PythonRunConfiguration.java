package com.jetbrains.python.run;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.jetbrains.python.PyBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author yole
 */
public class PythonRunConfiguration extends AbstractPythonRunConfiguration 
  implements AbstractPythonRunConfigurationParams, PythonRunConfigurationParams, RefactoringListenerProvider
{
  private String myScriptName;
  private String myScriptParameters;

  protected PythonRunConfiguration(RunConfigurationModule module, ConfigurationFactory configurationFactory, String name) {
    super(name, module, configurationFactory);
    setUnbufferedEnv();
  }

  protected ModuleBasedConfiguration createInstance() {
    return new PythonRunConfiguration(getConfigurationModule(), getFactory(), getName());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new PythonRunConfigurationEditor(this);
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    List<Filter> filters = Lists.newArrayList();
    filters.add(new PythonTracebackFilter(getProject(), getWorkingDirectory()));

    return new PythonScriptCommandLineState(this, env, filters);
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    if (StringUtil.isEmptyOrSpaces(myScriptName)) {
      throw new RuntimeConfigurationException(PyBundle.message("runcfg.unittest.no_script_name"));
    }
  }

  public boolean isGeneratedName() {
    return Comparing.equal(getName(), suggestedName());
  }

  public String suggestedName() {
    String name = new File(getScriptName()).getName();
    if (name.endsWith(".py")) {
      return name.substring(0, name.length() - 3);
    }
    return name;
  }

  public String getScriptName() {
    return myScriptName;
  }

  public void setScriptName(String scriptName) {
    myScriptName = scriptName;
  }

  public String getScriptParameters() {
    return myScriptParameters;
  }

  public void setScriptParameters(String scriptParameters) {
    myScriptParameters = scriptParameters;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myScriptName = JDOMExternalizerUtil.readField(element, "SCRIPT_NAME");
    myScriptParameters = JDOMExternalizerUtil.readField(element, "PARAMETERS");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "SCRIPT_NAME", myScriptName);
    JDOMExternalizerUtil.writeField(element, "PARAMETERS", myScriptParameters);
  }

  public AbstractPythonRunConfigurationParams getBaseParams() {
    return this;
  }

  public static void copyParams(PythonRunConfigurationParams source, PythonRunConfigurationParams target) {
    AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
    target.setScriptName(source.getScriptName());
    target.setScriptParameters(source.getScriptParameters());
  }

  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (element instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (virtualFile != null && Comparing.equal(new File(virtualFile.getPath()).getAbsolutePath(),
                                                 new File(myScriptName).getAbsolutePath())) {
        return new RefactoringElementAdapter() {
          @Override
          public void elementRenamedOrMoved(@NotNull PsiElement newElement) {
            VirtualFile virtualFile = ((PsiFile)newElement).getVirtualFile();
            if (virtualFile != null) {
              myScriptName = FileUtil.toSystemDependentName(virtualFile.getPath());
            }
          }
        };
      }
    }
    return null;
  }
}
