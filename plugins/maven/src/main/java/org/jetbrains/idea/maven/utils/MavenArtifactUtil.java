/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.IndicesBundle;
import org.jetbrains.idea.maven.model.MavenId;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class MavenArtifactUtil {
  public static final String[] DEFAULT_GROUPS = new String[]{"org.apache.maven.plugins", "org.codehaus.mojo"};
  public static final String MAVEN_PLUGIN_DESCRIPTOR = "META-INF/maven/plugin.xml";

  private static final Map<File, MavenPluginInfo> ourPluginInfoCache = Collections.synchronizedMap(new THashMap<File, MavenPluginInfo>());

  @Nullable
  public static MavenPluginInfo readPluginInfo(File localRepository, MavenId mavenId) {
    File file = getArtifactFile(localRepository, mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion(), "jar");

    MavenPluginInfo result = ourPluginInfoCache.get(file);
    if (result == null) {
      result = createPluginDocument(file);
      ourPluginInfoCache.put(file, result);
    }
    return result;
  }

  public static boolean hasArtifactFile(File localRepository, MavenId id) {
    return hasArtifactFile(localRepository, id, "jar");
  }

  public static boolean hasArtifactFile(File localRepository, MavenId id, String type) {
    return getArtifactFile(localRepository, id, type).exists();
  }

  @NotNull
  public static File getArtifactFile(File localRepository, MavenId id, String type) {
    return getArtifactFile(localRepository, id.getGroupId(), id.getArtifactId(), id.getVersion(), type);
  }

  @NotNull
  public static File getArtifactFile(File localRepository, MavenId id) {
    return getArtifactFile(localRepository, id.getGroupId(), id.getArtifactId(), id.getVersion(), "pom");
  }

  public static boolean isPluginIdEquals(@Nullable String groupId1, @Nullable String artifactId1,
                                         @Nullable String groupId2, @Nullable String artifactId2) {
    if (artifactId1 == null) return false;

    if (!artifactId1.equals(artifactId2)) return false;

    if (groupId1 != null) {
      for (String group : DEFAULT_GROUPS) {
        if (groupId1.equals(group)) {
          groupId1 = null;
          break;
        }
      }
    }

    if (groupId2 != null) {
      for (String group : DEFAULT_GROUPS) {
        if (groupId2.equals(group)) {
          groupId2 = null;
          break;
        }
      }
    }

    return Comparing.equal(groupId1, groupId2);
  }

  @NotNull
  public static File getArtifactFile(File localRepository, String groupId, String artifactId, String version, String type) {
    File dir = null;
    if (StringUtil.isEmpty(groupId)) {
      for (String each : DEFAULT_GROUPS) {
        dir = getArtifactDirectory(localRepository, each, artifactId);
        if (dir.exists()) break;
      }
    }
    else {
      dir = getArtifactDirectory(localRepository, groupId, artifactId);
    }

    if (StringUtil.isEmpty(version)) version = resolveVersion(dir);
    return new File(dir, version + File.separator + artifactId + "-" + version + "." + type);
  }

  private static File getArtifactDirectory(File localRepository,
                                           String groupId,
                                           String artifactId) {
    String relativePath = StringUtil.replace(groupId, ".", File.separator) + File.separator + artifactId;
    return new File(localRepository, relativePath);
  }

  private static String resolveVersion(File pluginDir) {
    List<String> versions = new ArrayList<>();

    File[] children;
    try {
      children = pluginDir.listFiles();
      if (children == null) return "";
    }
    catch (Exception e) {
      MavenLog.LOG.warn(e);
      return "";
    }

    for (File version : children) {
      if (version.isDirectory()) {
        versions.add(version.getName());
      }
    }

    if (versions.isEmpty()) return "";

    Collections.sort(versions);
    return versions.get(versions.size() - 1);
  }

  @Nullable
  private static MavenPluginInfo createPluginDocument(File file) {
    try {
      if (!file.exists()) return null;

      ZipFile jar = new ZipFile(file);
      try {
        ZipEntry entry = jar.getEntry(MAVEN_PLUGIN_DESCRIPTOR);

        if (entry == null) {
          MavenLog.LOG.info(IndicesBundle.message("repository.plugin.corrupt", file));
          return null;
        }

        InputStream is = jar.getInputStream(entry);
        try {
          byte[] bytes = FileUtil.loadBytes(is);
          return new MavenPluginInfo(bytes);
        }
        finally {
          is.close();
        }
      }
      finally {
        jar.close();
      }
    }
    catch (IOException e) {
      MavenLog.LOG.info(e);
      return null;
    }
  }
}
