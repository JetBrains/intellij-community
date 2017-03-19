package com.jetbrains.edu.coursecreator.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PyCCRunTestConfiguration extends AbstractPythonRunConfiguration<PyCCRunTestConfiguration> {

  public static final String PATH_ATTR = "studyTest";
  private Project myProject;
  private String myPathToTest;

  public PyCCRunTestConfiguration(Project project, ConfigurationFactory factory) {
    super(project, factory);
    myProject = project;
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    return new PyCCCommandLineState(this, environment);
  }

  @Override
  protected SettingsEditor<PyCCRunTestConfiguration> createConfigurationEditor() {
    return new PyCCSettingsEditor(myProject);
  }

  public String getPathToTest() {
    return myPathToTest;
  }

  public void setPathToTest(String pathToTest) {
    myPathToTest = pathToTest;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myPathToTest = JDOMExternalizerUtil.readField(element, PATH_ATTR);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, PATH_ATTR, myPathToTest);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    String message = "Select valid path to the file with tests";
    VirtualFile testsFile = LocalFileSystem.getInstance().findFileByPath(myPathToTest);
    if (testsFile == null) {
      throw new RuntimeConfigurationException(message);
    }
    VirtualFile taskDir = StudyUtils.getTaskDir(testsFile);
    if (taskDir == null) {
      throw new RuntimeConfigurationException(message);
    }
    if (StudyUtils.getTask(myProject, taskDir) == null) {
      throw new RuntimeConfigurationException(message);
    }
  }
}
