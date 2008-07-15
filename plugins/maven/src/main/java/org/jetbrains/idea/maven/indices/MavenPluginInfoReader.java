package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MavenPluginInfoReader extends DummyProjectComponent {
  public static final String[] DEFAULT_GROUPS = new String[]
      {"org.apache.maven.plugins", "org.codehaus.mojo"};

  public static final String MAVEN_PLUGIN_DESCRIPTOR = "META-INF/maven/plugin.xml";

  @Nullable
  public static MavenPluginInfo loadPluginInfo(File localRepository, MavenId mavenId) {
    File file = getPluginFile(localRepository, mavenId.groupId, mavenId.artifactId, mavenId.version, "jar");
    return createPluginDocument(file);
  }

  public static boolean hasPlugin(File localRepository, MavenId id) {
    return getPluginFile(localRepository, id.groupId, id.artifactId, id.version, "jar").exists();
  }

  public static File getPluginFile(File localRepostiory, String groupId, String artifactId, String version, String ext) {
    File dir = null;
    if (StringUtil.isEmpty(groupId)) {
      for (String each : DEFAULT_GROUPS) {
        dir = getPluginDirectory(localRepostiory, each, artifactId);
        if (dir.exists()) break;
      }
    }
    else {
      dir = getPluginDirectory(localRepostiory, groupId, artifactId);
    }

    if (StringUtil.isEmpty(version)) version = resolveVersion(dir);
    return new File(dir, version + File.separator + artifactId + "-" + version + "." + ext);
  }

  private static File getPluginDirectory(File localRepository,
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
      MavenLog.warn(e);
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
        MavenLog.info(IndicesBundle.message("repository.plugin.corrupt", file));
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
      MavenLog.info(e);
      return null;
    }
  }
}