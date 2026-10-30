// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenWorkspaceMapWrapper {
  private final MavenWorkspaceMap myWorkspaceMap;
  private final Properties mySystemProperties;

  private final Map<String, Set<MavenId>> myArtifactToIdToMavenIdMapping = new HashMap<>();
  private final Map<File, Properties> myPomPropertiesCache = new ConcurrentHashMap<>();

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

  /**
   * Same as {@link #findFileAndOriginalId(MavenId)}, but on miss looks for a same
   * {@code groupId+artifactId} workspace entry whose version is an unresolved
   * CI-friendly placeholder (e.g. {@code ${revision}}), and accepts it if and only
   * if the placeholder, resolved against the candidate workspace pom's effective
   * {@code <properties>}, equals the requested concrete version (or vice versa).
   * <p>
   * Required when the {@code <revision>} property is defined in a pom
   * {@code <properties>} block rather than passed via {@code -Drevision=...}
   * — {@link #interpolate(MavenId)} only consults system properties and can
   * leave workspace map keys as literal {@code ${revision}}, causing concrete-version
   * lookups from dependency resolution to miss (IDEA-388560).
   */
  public @Nullable MavenWorkspaceMap.Data findFileAndOriginalIdWithRevisionFallback(@NotNull MavenId requested) {
    MavenWorkspaceMap.Data direct = myWorkspaceMap.findFileAndOriginalId(requested);
    if (direct != null) return direct;

    String requestedVersion = requested.getVersion();
    if (requestedVersion == null) return null;
    boolean requestIsPlaceholder = isPlaceholder(requestedVersion);

    for (MavenId candidate : getAvailableIdsForArtifactId(requested.getArtifactId())) {
      if (!Objects.equals(candidate.getGroupId(), requested.getGroupId())) continue;
      String candidateVersion = candidate.getVersion();
      boolean candidateIsPlaceholder = isPlaceholder(candidateVersion);
      if (!candidateIsPlaceholder && !requestIsPlaceholder) continue;
      if (isVersionMarker(candidateVersion)) continue;

      MavenWorkspaceMap.Data data = myWorkspaceMap.findFileAndOriginalId(candidate);
      if (data == null) continue;
      File pomFile = data.getFile(MavenConstants.POM_EXTENSION);
      if (pomFile == null || !pomFile.isFile()) continue;

      if (candidateIsPlaceholder && !requestIsPlaceholder) {
        if (!requestedVersion.equals(expandPlaceholder(candidateVersion, pomFile))) continue;
      }
      else if (requestIsPlaceholder && !candidateIsPlaceholder) {
        if (!candidateVersion.equals(expandPlaceholder(requestedVersion, pomFile))) continue;
      }
      else if (!Objects.equals(candidateVersion, requestedVersion)) {
        continue;
      }
      return data;
    }
    return null;
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

  private static boolean isPlaceholder(@Nullable String s) {
    return s != null && s.length() > 3 && s.startsWith("${") && s.endsWith("}");
  }

  private static boolean isVersionMarker(@Nullable String version) {
    return version == null || version.isEmpty()
           || "LATEST".equals(version) || "RELEASE".equals(version);
  }

  private @Nullable String expandPlaceholder(@NotNull String placeholder, @NotNull File pomFile) {
    if (!isPlaceholder(placeholder)) return placeholder;
    String key = placeholder.substring(2, placeholder.length() - 1).trim();
    Properties props = myPomPropertiesCache.computeIfAbsent(pomFile, MavenWorkspaceMapWrapper::collectPropertiesChain);
    String value = props.getProperty(key);
    if (value == null) return null;
    return isPlaceholder(value) ? expandPlaceholder(value, pomFile) : value;
  }

  /**
   * Reads {@code <properties>} entries from {@code startPom} and best-effort walks the
   * {@code <parent>/<relativePath>} chain (capped at {@link #PARENT_WALK_LIMIT} levels).
   * Used solely by {@link #expandPlaceholder} to resolve {@code ${revision}}-like
   * CI-friendly placeholders. Intentionally regex-based to avoid pulling an XML parser
   * into the maven-server-api module.
   */
  private static @NotNull Properties collectPropertiesChain(@NotNull File startPom) {
    Properties result = new Properties();
    File current = startPom;
    for (int depth = 0; current != null && current.isFile() && depth < PARENT_WALK_LIMIT; depth++) {
      String xml;
      try {
        xml = Files.readString(current.toPath());
      }
      catch (IOException e) {
        break;
      }
      Matcher propsMatcher = PROPS_BLOCK.matcher(xml);
      if (propsMatcher.find()) {
        Matcher entryMatcher = PROP_ENTRY.matcher(propsMatcher.group(1));
        while (entryMatcher.find()) {
          result.putIfAbsent(entryMatcher.group(1), entryMatcher.group(2).trim());
        }
      }
      current = locateParentPom(current, xml);
    }
    return result;
  }

  private static @Nullable File locateParentPom(@NotNull File pomFile, @NotNull String xml) {
    Matcher parentMatcher = PARENT_BLOCK.matcher(xml);
    if (!parentMatcher.find()) return null;
    File dir = pomFile.getParentFile();
    if (dir == null) return null;
    Matcher relPath = RELATIVE_PATH.matcher(parentMatcher.group(1));
    File next;
    if (relPath.find()) {
      String relative = relPath.group(1).trim();
      if (relative.isEmpty()) return null;
      File candidate = new File(dir, relative);
      next = candidate.isDirectory() ? new File(candidate, MavenConstants.POM_XML) : candidate;
    }
    else {
      File grand = dir.getParentFile();
      if (grand == null) return null;
      next = new File(grand, MavenConstants.POM_XML);
    }
    return (next.equals(pomFile) || !next.isFile()) ? null : next;
  }

  private static final Pattern PROPS_BLOCK = Pattern.compile("<properties>(.*?)</properties>", Pattern.DOTALL);
  private static final Pattern PROP_ENTRY = Pattern.compile("<([A-Za-z0-9_.\\-]+)>([^<]+)</\\1>");
  private static final Pattern PARENT_BLOCK = Pattern.compile("<parent>(.*?)</parent>", Pattern.DOTALL);
  private static final Pattern RELATIVE_PATH = Pattern.compile("<relativePath>([^<]*)</relativePath>");
  private static final int PARENT_WALK_LIMIT = 10;
}
