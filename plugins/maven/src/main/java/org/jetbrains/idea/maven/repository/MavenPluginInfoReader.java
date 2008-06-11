package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
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
  public static final String MAVEN_PLUGIN_DESCRIPTOR = "META-INF/maven/plugin.xml";

  @Nullable
  public MavenPluginInfo loadPluginInfo(String repositoryPath, MavenId mavenId) {
    String path = findPluginPath(repositoryPath, mavenId.groupId, mavenId.artifactId, mavenId.version);
    if (path == null) return null;

    return createPluginDocument(path);
  }

  public boolean hasPlugin(String repositoryPath, MavenId id) {
    return findPluginPath(repositoryPath, id.groupId, id.artifactId, id.version) != null;
  }

  @Nullable
  @NonNls
  public String findPluginPath(String repositoryPath, String groupId, String artifactId, String version) {
    VirtualFile dir;
    if (StringUtil.isEmpty(groupId)) {
      dir = findPluginDirectory(repositoryPath, "org.apache.maven.plugins", artifactId);
      if (dir == null) dir = findPluginDirectory(repositoryPath, "org.codehaus.mojo", artifactId);
    } else {
      dir = findPluginDirectory(repositoryPath, groupId, artifactId);
    }

    if (dir == null || !dir.isDirectory()) return null;

    if (StringUtil.isEmpty(version)) version = resolveVersion(dir);
    return dir.getPath() + File.separator + version + File.separator + artifactId + "-" + version + ".jar";
  }

  @Nullable
  private VirtualFile findPluginDirectory(String repositoryPath,
                                          String groupId,
                                          String artifactId) {
    String relativePath = StringUtil.replace(groupId, ".", File.separator) + File.separator + artifactId;
    return LocalFileSystem.getInstance().findFileByPath(repositoryPath + File.separator + relativePath);
  }

  private String resolveVersion(VirtualFile pluginDir) {
    List<String> versions = new ArrayList<String>();

    for (VirtualFile availableVersion : pluginDir.getChildren()) {
      if (availableVersion.isDirectory()) {
        versions.add(availableVersion.getName());
      }
    }

    if (versions.isEmpty()) return "";

    Collections.sort(versions);
    return versions.get(versions.size() - 1);
  }

  @Nullable
  private MavenPluginInfo createPluginDocument(String path) {
    try {
      ZipFile jar = new ZipFile(path);
      ZipEntry entry = jar.getEntry(MAVEN_PLUGIN_DESCRIPTOR);

      if (entry == null) {
        MavenLog.info(RepositoryBundle.message("repository.plugin.corrupt", path));
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