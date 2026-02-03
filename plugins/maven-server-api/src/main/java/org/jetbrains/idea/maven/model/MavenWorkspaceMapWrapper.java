// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenWorkspaceMapWrapper {
  private final MavenWorkspaceMap myWorkspaceMap;
  private final Properties mySystemProperties;

  private final Map<String, Set<MavenId>> myArtifactToIdToMavenIdMapping = new HashMap<>();

  public MavenWorkspaceMapWrapper(MavenWorkspaceMap workspaceMap, Properties systemProperties) {
    myWorkspaceMap = workspaceMap;
    mySystemProperties = systemProperties;

    if (null != myWorkspaceMap) {
      // concurrent modification
      Set<MavenId> ids = new HashSet<>(myWorkspaceMap.getAvailableIds());
      for (MavenId mavenId : ids) {
        MavenId interpolated = interpolate(mavenId);
        if (interpolated != mavenId) {
          MavenWorkspaceMap.Data data = myWorkspaceMap.findFileAndOriginalId(mavenId);
          workspaceMap.register(interpolated, data.getFile(MavenConstants.POM_EXTENSION));
        }
      }

      for (MavenId mavenId : myWorkspaceMap.getAvailableIds()) {
        if (!myArtifactToIdToMavenIdMapping.containsKey(mavenId.getArtifactId())) {
          myArtifactToIdToMavenIdMapping.put(mavenId.getArtifactId(), new HashSet<>());
        }
        myArtifactToIdToMavenIdMapping.get(mavenId.getArtifactId()).add(mavenId);
      }
    }
  }

  public MavenWorkspaceMap.Data findFileAndOriginalId(MavenId mavenId) {
    return myWorkspaceMap.findFileAndOriginalId(mavenId);
  }

  public @NotNull Set<MavenId> getAvailableIdsForArtifactId(String artifactId) {
    Set<MavenId> ids = myArtifactToIdToMavenIdMapping.get(artifactId);
    return null == ids ? Collections.emptySet() : ids;
  }

  private MavenId interpolate(MavenId raw) {
    String groupId = maybeInterpolate(raw.getGroupId());
    String artifactId = maybeInterpolate(raw.getArtifactId());
    String version = maybeInterpolate(raw.getVersion());

    // If no field changed, return the original instance.
    if (Objects.equals(groupId, raw.getGroupId())
        && Objects.equals(artifactId, raw.getArtifactId())
        && Objects.equals(version, raw.getVersion())) {
      return raw;
    }

    return new MavenId(groupId, artifactId, version);
  }

  private String maybeInterpolate(String field) {
    return (field != null && field.contains("${")) ? interpolateField(field) : field;
  }

  private static final Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");

  private String interpolateField(String value) {
    Matcher matcher = pattern.matcher(value);
    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String propertyKey = matcher.group(1);
      String replacement = mySystemProperties.getProperty(propertyKey, "");
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  public void register(MavenId id, File file) {
    myWorkspaceMap.register(id, file);
    Set<MavenId> mavenIds = myArtifactToIdToMavenIdMapping.get(id.getArtifactId());
    if (null != mavenIds) {
      mavenIds.add(id);
    }
  }
}
