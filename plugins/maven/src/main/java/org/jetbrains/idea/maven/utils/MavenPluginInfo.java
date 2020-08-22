// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import org.jdom.Element;
import org.jetbrains.idea.maven.model.MavenId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.jetbrains.idea.maven.model.MavenId.append;

public class MavenPluginInfo {
  private final String myGroupId;
  private final String myArtifactId;
  private final String myVersion;
  private final String myGoalPrefix;
  private final Map<String, Mojo> myMojos;

  public MavenPluginInfo(byte[] text) {
    Element plugin = MavenJDOMUtil.read(text, null);

    myGroupId = MavenJDOMUtil.findChildValueByPath(plugin, "groupId", MavenId.UNKNOWN_VALUE);
    myArtifactId = MavenJDOMUtil.findChildValueByPath(plugin, "artifactId", MavenId.UNKNOWN_VALUE);
    myVersion = MavenJDOMUtil.findChildValueByPath(plugin, "version", MavenId.UNKNOWN_VALUE);

    myGoalPrefix = MavenJDOMUtil.findChildValueByPath(plugin, "goalPrefix", "unknown");

    myMojos = readMojos(plugin);
  }

  private Map<String, Mojo> readMojos(Element plugin) {
    Map<String, Mojo> result = new LinkedHashMap<>();
    for (Element each : MavenJDOMUtil.findChildrenByPath(plugin, "mojos", "mojo")) {
      String goal = MavenJDOMUtil.findChildValueByPath(each, "goal", "unknown");
      result.put(goal, new Mojo(goal));
    }
    return result;
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

  public String getGoalPrefix() {
    return myGoalPrefix;
  }

  public Collection<Mojo> getMojos() {
    return myMojos.values();
  }

  public Mojo findMojo(String name) {
    return myMojos.get(name);
  }

  public final class Mojo {
    private final String myGoal;

    private Mojo(String goal) {
      myGoal = goal;
    }

    public String getGoal() {
      return myGoal;
    }

    public String getDisplayName() {
      return myGoalPrefix + ":" + myGoal;
    }

    public String getQualifiedGoal() {
      StringBuilder builder = new StringBuilder();

      append(builder, myGroupId);
      append(builder, myArtifactId);
      append(builder, myVersion);
      append(builder, myGoal);

      return builder.toString();
    }
  }
}
