/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.idea.maven.model;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.idea.maven.model.MavenId.append;

public class MavenPlugin implements Serializable {

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
    if (myGroupId != null ? !myGroupId.equals(that.myGroupId) : that.myGroupId != null) return false;
    if (myArtifactId != null ? !myArtifactId.equals(that.myArtifactId) : that.myArtifactId != null) return false;
    if (myVersion != null ? !myVersion.equals(that.myVersion) : that.myVersion != null) return false;
    if (!JDOMUtil.areElementsEqual(myConfiguration, that.myConfiguration)) return false;
    if (myExecutions != null ? !myExecutions.equals(that.myExecutions) : that.myExecutions != null) return false;
    if (myDependencies != null ? !myDependencies.equals(that.myDependencies) : that.myDependencies != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myDefault ? 1 : 0;
    result = 31 * result + (myExtensions ? 1 : 0);
    result = 31 * result + (myGroupId != null ? myGroupId.hashCode() : 0);
    result = 31 * result + (myArtifactId != null ? myArtifactId.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    result = 31 * result + (myConfiguration != null ? JDOMUtil.getTreeHash(myConfiguration) : 0);
    result = 31 * result + (myExecutions != null ? myExecutions.hashCode() : 0);
    result = 31 * result + (myDependencies != null ? myDependencies.hashCode() : 0);
    return result;
  }

  public static class Execution implements Serializable {
    private final List<String> myGoals;
    private final Element myConfiguration;
    private final String myExecutionId;
    private final String myPhase;

    public Execution(String executionId, List<String> goals, Element configuration) {
      this(executionId, null, goals, configuration);
    }

    public Execution(String executionId, String phase, List<String> goals, Element configuration) {
      myGoals = goals == null ? Collections.<String>emptyList() : new ArrayList<String>(goals);
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
      if (myExecutionId != null ? !myExecutionId.equals(that.myExecutionId) : that.myExecutionId != null) return false;
      if (myPhase != null ? !myPhase.equals(that.myPhase) : that.myPhase != null) return false;
      if (!JDOMUtil.areElementsEqual(myConfiguration, that.myConfiguration)) return false;

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
