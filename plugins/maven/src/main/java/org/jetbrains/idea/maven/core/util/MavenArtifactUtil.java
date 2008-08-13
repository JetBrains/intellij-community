package org.jetbrains.idea.maven.core.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.indices.IndicesBundle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MavenArtifactUtil extends DummyProjectComponent {
  public static final String[] DEFAULT_GROUPS = new String[]{"org.apache.maven.plugins", "org.codehaus.mojo"};
  public static final String MAVEN_PLUGIN_DESCRIPTOR = "META-INF/maven/plugin.xml";

  private static final Map<File, MavenPluginInfo> ourPluginInfoCache = Collections.synchronizedMap(new HashMap<File, MavenPluginInfo>());

  @Nullable
  public static MavenPluginInfo readPluginInfo(File localRepository, MavenId mavenId) {
    File file = getArtifactFile(localRepository, mavenId.groupId, mavenId.artifactId, mavenId.version, "jar");

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
    return getArtifactFile(localRepository, id.groupId, id.artifactId, id.version, type).exists();
  }

  public static File getArtifactFile(File localRepostiory, String groupId, String artifactId, String version, String type) {
    File dir = null;
    if (StringUtil.isEmpty(groupId)) {
      for (String each : DEFAULT_GROUPS) {
        dir = getArtifactDirectory(localRepostiory, each, artifactId);
        if (dir.exists()) break;
      }
    }
    else {
      dir = getArtifactDirectory(localRepostiory, groupId, artifactId);
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
    List<String> versions = new ArrayList<String>();

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
      ZipEntry entry = jar.getEntry(MAVEN_PLUGIN_DESCRIPTOR);

      if (entry == null) {
        MavenLog.LOG.info(IndicesBundle.message("repository.plugin.corrupt", file));
        return null;
      }

      InputStream is = jar.getInputStream(entry);
      try {
        return new MavenPluginInfo(is);
      }
      finally {
        is.close();
        jar.close();
      }
    }
    catch (IOException e) {
      MavenLog.LOG.info(e);
      return null;
    }
  }
}