package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MavenPluginsRepository extends DummyProjectComponent {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.repo.ModelUtils");

  @NonNls private static final String MAVEN_PLUGIN_DESCRIPTOR = "META-INF/maven/plugin.xml";

  private final Project myProject;
  private final MavenCore myMavenCore;

  private final Map<MavenId, MavenPluginInfo> pluginCache = new HashMap<MavenId, MavenPluginInfo>();

  public static MavenPluginsRepository getInstance(Project p) {
    return p.getComponent(MavenPluginsRepository.class);
  }

  public MavenPluginsRepository(Project project, MavenCore mavenCore) {
    myProject = project;
    myMavenCore = mavenCore;
  }

  @Nullable
  private File getLocalRepository() {
    return myMavenCore.getState().getEffectiveLocalRepository();
  }

  @Nullable
  public MavenPluginInfo loadPluginInfo(MavenId mavenId) {
    MavenPluginInfo p = pluginCache.get(mavenId);
    if (p != null) return p;

    p = doLoadPluginInfo(mavenId);
    if (p == null) return null;

    pluginCache.put(mavenId, p);
    return p;
  }

  @Nullable
  private MavenPluginInfo doLoadPluginInfo(MavenId mavenId) {
    String path = findPluginPath(mavenId.groupId, mavenId.artifactId, mavenId.version);
    if (path == null) return null;

    return createPluginDocument(path, false);
  }

  @Nullable
  @NonNls
  private String findPluginPath(String groupId, String artifactId, String version) {
    File repository = getLocalRepository();
    if (repository == null) return null;

    String repositoryPath = repository.getPath();

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
  private VirtualFile findPluginDirectory(String mavenRepository,
                                          String groupId,
                                          String artifactId) {
    String relativePath = StringUtil.replace(groupId, ".", File.separator) + File.separator + artifactId;
    return LocalFileSystem.getInstance().findFileByPath(mavenRepository + File.separator + relativePath);
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
  private MavenPluginInfo createPluginDocument(String path, boolean loud) {
    try {
      ZipFile jar = new ZipFile(path);
      ZipEntry entry = jar.getEntry(MAVEN_PLUGIN_DESCRIPTOR);

      if (entry == null) {
        deliverMessage(RepositoryBundle.message("repository.plugin.corrupt", path), loud);
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
      deliverMessage(e.getMessage(), loud);
      return null;
    }
  }

  private void deliverMessage(String message, boolean loud) {
    if (loud) {
      Messages.showErrorDialog(myProject, message, RepositoryBundle.message("repository.plugin.title"));
    }
    else {
      LOG.info(message);
    }
  }

  @NotNull
  public Collection<MavenPluginInfo> choosePlugins() {
    final File localRepository = getLocalRepository();
    if (localRepository == null) {
      return new ArrayList<MavenPluginInfo>();
    }

    final VirtualFile repoDir = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        //noinspection ConstantConditions
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(localRepository.getPath());
      }
    });

    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, true, false, true);
    VirtualFile[] pluginFiles = FileChooser.chooseFiles(myProject, descriptor, repoDir);

    Collection<MavenPluginInfo> pluginDocuments = new ArrayList<MavenPluginInfo>();
    for (VirtualFile pluginFile : pluginFiles) {
      MavenPluginInfo mavenPluginDocument = createPluginDocument(pluginFile.getPath(), true);
      if (mavenPluginDocument != null) {
        pluginDocuments.add(mavenPluginDocument);
      }
    }
    return pluginDocuments;
  }
}
