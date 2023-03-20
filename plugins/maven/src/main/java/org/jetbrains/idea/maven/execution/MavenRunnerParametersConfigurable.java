package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MavenRunnerParametersConfigurable extends MavenRunnerParametersPanel implements Configurable {

  public MavenRunnerParametersConfigurable(@NotNull Project project) {
    super(project);
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  @Override
  public boolean isModified() {
    MavenRunnerParameters formParameters = new MavenRunnerParameters();
    setData(formParameters);
    return !formParameters.equals(getParameters());
  }

  @Override
  public void apply() throws ConfigurationException {
    setData(getParameters());
  }

  @Override
  public void reset() {
    getData(getParameters());
  }

  protected abstract MavenRunnerParameters getParameters();

}
