// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.maven.model;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.jetbrains.idea.maven.model.MavenId.append;

public final class MavenPlugin implements Serializable {
  static final long serialVersionUID = -6113607480882347420L;

  private final String myGroupId;
  private final String myArtifactId;
  private final String myVersion;

  private final boolean myDefault;
  private final boolean myExtensions;

  private final Element myConfiguration;
  private final List<Execution> myExecutions;

  private final List<MavenId> myDependencies;

  public MavenPlugin(String groupId,
                     String artifactId,
                     String version,
                     boolean aDefault,
                     boolean extensions,
                     Element configuration,
                     List<Execution> executions,
                     List<MavenId> dependencies) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
    myDefault = aDefault;
    myExtensions = extensions;
    myConfiguration = configuration;
    myExecutions = executions;
    myDependencies = dependencies;
  }

  public String getGroupId() {
    return myGroupId;
  }

  public String getArtifactId() {
    return myArtifactId;
  }

  public String getVersion() {
    return myVersion;
  }

  public MavenId getMavenId() {
    return new MavenId(myGroupId, myArtifactId, myVersion);
  }

  public boolean isDefault() {
    return myDefault;
  }

  public boolean isExtensions() {
    return myExtensions;
  }

  @Nullable
  public Element getConfigurationElement() {
    return myConfiguration;
  }

  public List<Execution> getExecutions() {
    return myExecutions;
  }

  public List<MavenId> getDependencies() {
    return myDependencies;
  }

  @Nullable
  public Element getGoalConfiguration(@NotNull String goal) {
    for (MavenPlugin.Execution each : getExecutions()) {
      if (each.getGoals().contains(goal)) {
        return each.getConfigurationElement();
      }
    }

    return null;
  }

  public Element getExecutionConfiguration(@NotNull String executionId) {
    for (MavenPlugin.Execution each : getExecutions()) {
      if (executionId.equals(each.getExecutionId())) {
        return each.getConfigurationElement();
      }
    }

    return null;
  }

  public List<Element> getCompileExecutionConfigurations() {
    List<Element> result = new ArrayList<Element>();
    for (MavenPlugin.Execution each : getExecutions()) {
      if (isCompileExecution(each) && each.getConfigurationElement() != null) {
        result.add(each.getConfigurationElement());
      }
    }
    return result;
  }

  private static boolean isCompileExecution(Execution each) {
    return !Objects.equals(each.getPhase(), "none") && ("default-compile".equals(each.getExecutionId()) ||
           (each.getGoals() != null && each.getGoals().contains("compile")));
  }

  public String getDisplayString() {
    StringBuilder builder = new StringBuilder();

    append(builder, myGroupId);
    append(builder, myArtifactId);
    append(builder, myVersion);

    return builder.toString();
  }

  @Override
  public String toString() {
    return getDisplayString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenPlugin that = (MavenPlugin)o;

    if (myDefault != that.myDefault) return false;
    if (myExtensions != that.myExtensions) return false;
    if (!Objects.equals(myGroupId, that.myGroupId)) return false;
    if (!Objects.equals(myArtifactId, that.myArtifactId)) return false;
    if (!Objects.equals(myVersion, that.myVersion)) return false;
    if (!MavenJDOMUtil.areElementsEqual(myConfiguration, that.myConfiguration)) return false;
    if (!Objects.equals(myExecutions, that.myExecutions)) return false;
    if (!Objects.equals(myDependencies, that.myDependencies)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myDefault ? 1 : 0;
    result = 31 * result + (myExtensions ? 1 : 0);
    result = 31 * result + (myGroupId != null ? myGroupId.hashCode() : 0);
    result = 31 * result + (myArtifactId != null ? myArtifactId.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    result = 31 * result + (myConfiguration != null ? MavenJDOMUtil.getTreeHash(myConfiguration) : 0);
    result = 31 * result + (myExecutions != null ? myExecutions.hashCode() : 0);
    result = 31 * result + (myDependencies != null ? myDependencies.hashCode() : 0);
    return result;
  }

  public static final class Execution implements Serializable {
    private final List<String> myGoals;
    private final Element myConfiguration;
    private final String myExecutionId;
    private final String myPhase;

    public Execution(String executionId, List<String> goals, Element configuration) {
      this(executionId, null, goals, configuration);
    }

    public Execution(String executionId, String phase, List<String> goals, Element configuration) {
      myGoals = goals == null ? Collections.emptyList() : new ArrayList<>(goals);
      myConfiguration = configuration;
      myExecutionId = executionId;
      myPhase = phase;
    }

    public String getExecutionId() {
      return myExecutionId;
    }

    public String getPhase() {
      return myPhase;
    }

    public List<String> getGoals() {
      return myGoals;
    }

    @Nullable
    public Element getConfigurationElement() {
      return myConfiguration;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Execution that = (Execution)o;

      if (!myGoals.equals(that.myGoals)) return false;
      if (!Objects.equals(myExecutionId, that.myExecutionId)) return false;
      if (!Objects.equals(myPhase, that.myPhase)) return false;
      if (!MavenJDOMUtil.areElementsEqual(myConfiguration, that.myConfiguration)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myGoals.hashCode();
      if (myExecutionId != null) {
        result = 31 * result + myExecutionId.hashCode();
      }
      if (myPhase != null) {
        result = 31 * result + myPhase.hashCode();
      }

      return result;
    }
  }
}
