package org.jetbrains.idea.maven.runner.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.runner.MavenRunnerSettings;
import org.jetbrains.idea.maven.runner.RunnerBundle;
import org.jetbrains.idea.maven.runner.executor.MavenExternalParameters;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;

public class MavenRunConfiguration extends RunConfigurationBase implements LocatableConfiguration{
  private MavenSettings mySettings;

  protected MavenRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
    mySettings = new MavenSettings(project);
  }

  public RunConfiguration clone() {
    MavenRunConfiguration clone = (MavenRunConfiguration)super.clone();
    clone.mySettings = mySettings.clone();
    return clone;
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new MavenRunSettingsEditor(getProject());
  }

  public JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider provider) {
    return null;
  }

  public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(JavaProgramRunner runner) {
    return null;
  }

  public RunProfileState getState(DataContext context,
                                  RunnerInfo runnerInfo,
                                  final RunnerSettings runnerSettings,
                                  final ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
    JavaCommandLineState state = new JavaCommandLineState(runnerSettings, configurationSettings) {
      protected JavaParameters createJavaParameters() throws ExecutionException {
        return MavenExternalParameters
          .createJavaParameters(mySettings.myRunnerParameters, mySettings.myCoreSettings, mySettings.myRunnerSettings);
      }
    };
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    state.setModulesToCompile(getModules());
    return state;
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    if (!mySettings.myRunnerParameters.getPomFile().isFile()) {
      throw new RuntimeConfigurationError(RunnerBundle.message("maven.run.configuration.error.file.not.found"));
    }
  }

  public Module[] getModules() {
    return Module.EMPTY_ARRAY;
  }

  public MavenCoreSettings getCoreSettings() {
    return mySettings.myCoreSettings;
  }

  public MavenRunnerSettings getRunnerSettings() {
    return mySettings.myRunnerSettings;
  }

  public MavenRunnerParameters getRunnerParameters() {
    return mySettings.myRunnerParameters;
  }

  public void setRunnerParameters(MavenRunnerParameters p){
    mySettings.myRunnerParameters = p;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    Element mavenSettingsElement = element.getChild(MavenSettings.TAG);
    if(mavenSettingsElement!=null){
      mySettings = XmlSerializer.deserialize(mavenSettingsElement, MavenSettings.class);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.addContent(XmlSerializer.serialize(mySettings));
  }

  public boolean isGeneratedName() {
    return Comparing.equal(getName(), getGeneratedName());
  }

  public String suggestedName() {
    return getGeneratedName();
  }

  private String getGeneratedName() {
    return MavenRunConfigurationType.generateName(getProject(), mySettings.myRunnerParameters);
  }

  public static class MavenSettings implements Cloneable {
    public static final String TAG = "MavenSettings";

    public MavenCoreSettings myCoreSettings;
    public MavenRunnerSettings myRunnerSettings;
    public MavenRunnerParameters myRunnerParameters;

    public MavenSettings() {
      this(ProjectManager.getInstance().getDefaultProject());
    }

    public MavenSettings(Project project) {
      this(project.getComponent(MavenCore.class).getState(),
           project.getComponent(MavenRunner.class).getState(),
           new MavenRunnerParameters());
    }

    public MavenSettings(MavenSettings that) {
      this(that.myCoreSettings, that.myRunnerSettings, that.myRunnerParameters);
    }

    private MavenSettings(MavenCoreSettings cs, MavenRunnerSettings rs, MavenRunnerParameters rp) {
      myCoreSettings = cs.clone();
      myRunnerSettings = rs.clone();
      myRunnerSettings.useExternalMaven();
      myRunnerParameters = rp.clone();
    }

    protected MavenSettings clone() {
      return new MavenSettings(this);
    }
  }
}
