/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.JDOMUtil;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenId;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MavenPlugin {
  private final String myGroupId;
  private final String myArtifactId;
  private final String myVersion;

  private final String myConfiguration;
  private final List<Execution> myExecutions = new ArrayList<Execution>();

  public MavenPlugin(Plugin plugin) {
    myGroupId = plugin.getGroupId();
    myArtifactId = plugin.getArtifactId();
    myVersion = plugin.getVersion();

    Object config = plugin.getConfiguration();
    myConfiguration = config == null ? null : config.toString();

    for (PluginExecution each : (Iterable<PluginExecution>)plugin.getExecutions()) {
      myExecutions.add(new Execution(each));
    }
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

  @Nullable
  public Element getConfigurationElement() {
    return getConfigurationElement(myConfiguration);
  }

  @Nullable
  private static Element getConfigurationElement(String text) {
    try {
      if (text == null) return null;
      return JDOMUtil.loadDocument(text).getRootElement();
    }
    catch (IOException e) {
      MavenLog.LOG.info(e);
      return null;
    }
    catch (JDOMException e) {
      MavenLog.LOG.info(e);
      return null;
    }
  }

  public List<Execution> getExecutions() {
    return myExecutions;
  }

  @Override
  public String toString() {
    return myGroupId + ":" + myArtifactId + ":" + myVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenPlugin that = (MavenPlugin)o;

    if (myArtifactId != null ? !myArtifactId.equals(that.myArtifactId) : that.myArtifactId != null) return false;
    if (myGroupId != null ? !myGroupId.equals(that.myGroupId) : that.myGroupId != null) return false;
    if (myVersion != null ? !myVersion.equals(that.myVersion) : that.myVersion != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myGroupId != null ? myGroupId.hashCode() : 0;
    result = 31 * result + (myArtifactId != null ? myArtifactId.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    return result;
  }

  public static class Execution {
    private final List<String> myGoals;
    private final String myConfiguration;

    public Execution(PluginExecution execution) {
      myGoals = execution.getGoals();

      Object config = execution.getConfiguration();
      myConfiguration = config == null ? null : config.toString();
    }

    public List<String> getGoals() {
      return myGoals;
    }

    @Nullable
    public Element getConfigurationElement() {
      return MavenPlugin.getConfigurationElement(myConfiguration);
    }
  }
}
