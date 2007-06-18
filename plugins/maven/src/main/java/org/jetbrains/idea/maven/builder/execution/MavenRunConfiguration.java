package org.jetbrains.idea.maven.builder.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.builder.MavenBuilder;
import org.jetbrains.idea.maven.builder.MavenBuilderState;
import org.jetbrains.idea.maven.builder.executor.MavenBuildParameters;
import org.jetbrains.idea.maven.builder.executor.MavenExternalParameters;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreState;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenRunConfiguration extends RunConfigurationBase implements LocatableConfiguration{
  public static class MavenSettings implements Cloneable {
    @NonNls private static final String TAG = "MavenSettings";

    public MavenBuildParameters buildParameters;
    public MavenCoreState coreState;
    public MavenBuilderState builderState;

    public MavenSettings() {
      buildParameters = new MavenBuildParameters();
      coreState = new MavenCoreState();
      builderState = new MavenBuilderState();
      builderState.setUseMavenEmbedder(false);
    }

    public MavenSettings(final Project project) {
      buildParameters = new MavenBuildParameters();
      coreState = project.getComponent(MavenCore.class).getState().clone();
      builderState = project.getComponent(MavenBuilder.class).getState().clone();
      builderState.setUseMavenEmbedder(false);
    }

    public MavenSettings(final MavenSettings that) {
      buildParameters = that.buildParameters.clone();
      coreState = that.coreState.clone();
      builderState = that.builderState.clone();
    }

    protected MavenSettings clone() {
      return new MavenSettings(this);
    }
  }

  private MavenSettings myMavenSettings;

  protected MavenRunConfiguration(final Project project, final ConfigurationFactory factory, final String name) {
    super(project, factory, name);
    myMavenSettings = new MavenSettings(project);
  }

  public RunConfiguration clone() {
    MavenRunConfiguration clone = (MavenRunConfiguration)super.clone();
    clone.myMavenSettings = myMavenSettings.clone();
    return clone;
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new MavenRunSettingsEditor();
  }

  public JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider provider) {
    return null;
  }

  public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(JavaProgramRunner runner) {
    return null;
  }

  public RunProfileState getState(DataContext context,
                                  RunnerInfo runnerInfo,
                                  RunnerSettings runnerSettings,
                                  ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException {
    JavaCommandLineState state = new JavaCommandLineState(runnerSettings, configurationSettings) {
      protected JavaParameters createJavaParameters() throws ExecutionException {
        return MavenExternalParameters
          .createJavaParameters(myMavenSettings.buildParameters, myMavenSettings.coreState, myMavenSettings.builderState);
      }
    };
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    state.setModulesToCompile(getModules());
    return state;
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
  }

  public Module[] getModules() {
    return new Module[0];
  }

  public MavenBuildParameters getBuildParameters() {
    return myMavenSettings.buildParameters;
  }

  public void setBuildParameters (MavenBuildParameters parameters){
    myMavenSettings.buildParameters = parameters;    
  }

  public MavenCoreState getCoreState() {
    return myMavenSettings.coreState;
  }

  public MavenBuilderState getBuilderState() {
    return myMavenSettings.builderState;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    super.readExternal(element);
    Element mavenSettingsElement = element.getChild(MavenSettings.TAG);
    if(mavenSettingsElement!=null){
      myMavenSettings = XmlSerializer.deserialize(mavenSettingsElement, MavenSettings.class);
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.addContent(XmlSerializer.serialize(myMavenSettings));
  }

  public boolean isGeneratedName() {
    return Comparing.equal(getName(), getGeneratedName());
  }

  public String suggestedName() {
    return getGeneratedName();
  }

  private String getGeneratedName() {
    return MavenRunConfigurationType.generateName(myMavenSettings.buildParameters);
  }
}
