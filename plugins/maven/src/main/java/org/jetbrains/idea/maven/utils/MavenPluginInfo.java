// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.plugins.compatibility.MavenLifecycleMetadataReader;
import org.jetbrains.idea.maven.plugins.compatibility.MavenPluginM2ELifecycles;

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
  private final @Nullable MavenPluginM2ELifecycles myLifecycles;

  public MavenPluginInfo(@NotNull byte[] text, @Nullable byte[] lifecycle) {
    Element plugin = MavenJDOMUtil.read(text, null);

    myGroupId = MavenJDOMUtil.findChildValueByPath(plugin, "groupId", MavenId.UNKNOWN_VALUE);
    myArtifactId = MavenJDOMUtil.findChildValueByPath(plugin, "artifactId", MavenId.UNKNOWN_VALUE);
    myVersion = MavenJDOMUtil.findChildValueByPath(plugin, "version", MavenId.UNKNOWN_VALUE);

    myGoalPrefix = MavenJDOMUtil.findChildValueByPath(plugin, "goalPrefix", "unknown");

    myMojos = readMojos(plugin);
    myLifecycles = readLifecycles(lifecycle);
  }

  private @Nullable MavenPluginM2ELifecycles readLifecycles(byte[] lifecycle) {
    if (lifecycle == null) return null;
    return MavenLifecycleMetadataReader.read(this.getGroupId() + ":" + this.getArtifactId() + ":" + this.getVersion(), lifecycle);
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

  public @Nullable MavenPluginM2ELifecycles getLifecycles() {
    return myLifecycles;
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
