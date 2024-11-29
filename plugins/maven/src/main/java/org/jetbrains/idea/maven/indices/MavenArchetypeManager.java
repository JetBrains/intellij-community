// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.archetype.MavenCatalog;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static org.jetbrains.idea.maven.project.MavenEmbeddersManager.FOR_POST_PROCESSING;

public class MavenArchetypeManager {
  private static final String ELEMENT_ARCHETYPES = "archetypes";
  private static final String ELEMENT_ARCHETYPE = "archetype";
  private static final String ELEMENT_GROUP_ID = "groupId";
  private static final String ELEMENT_ARTIFACT_ID = "artifactId";
  private static final String ELEMENT_VERSION = "version";
  private static final String ELEMENT_REPOSITORY = "repository";
  private static final String ELEMENT_DESCRIPTION = "description";

  @NotNull private final Project myProject;

  public static MavenArchetypeManager getInstance(@NotNull Project project) {
    return project.getService(MavenArchetypeManager.class);
  }

  public MavenArchetypeManager(@NotNull Project project) {
    myProject = project;
  }

  public Collection<MavenArchetype> getArchetypes(@NotNull MavenCatalog catalog) {
    if (catalog instanceof MavenCatalog.System.Internal) {
      return getInnerArchetypes();
    }
    if (catalog instanceof MavenCatalog.System.DefaultLocal) {
      return getLocalArchetypes();
    }
    if (catalog instanceof MavenCatalog.System.MavenCentral) {
      return getRemoteArchetypes(((MavenCatalog.System.MavenCentral)catalog).getUrl());
    }
    if (catalog instanceof MavenCatalog.Local) {
      return getInnerArchetypes(((MavenCatalog.Local)catalog).getPath());
    }
    if (catalog instanceof MavenCatalog.Remote) {
      return getRemoteArchetypes(((MavenCatalog.Remote)catalog).getUrl());
    }
    return Collections.emptyList();
  }

  public Set<MavenArchetype> getArchetypes() {
    MavenIndicesManager indicesManager = MavenIndicesManager.getInstance(myProject);
    Set<MavenArchetype> result = new HashSet<>(getInnerArchetypes());
    result.addAll(loadUserArchetypes(getUserArchetypesFile()));
    if (!indicesManager.isInit()) {
      indicesManager.updateIndicesListSync();
    }

    for (MavenArchetypesProvider each : MavenArchetypesProvider.EP_NAME.getExtensionList()) {
      result.addAll(each.getArchetypes());
    }
    return result;
  }

  public Collection<MavenArchetype> getLocalArchetypes() {
    MavenIndicesManager indicesManager = MavenIndicesManager.getInstance(myProject);
    return Collections.emptyList();
  }

  public Collection<MavenArchetype> getInnerArchetypes() {
    return List.of(
      new MavenArchetype("org.apache.maven.archetypes",
                         "maven-archetype-archetype",
                         "1.0", null,
                         "An archetype which contains a sample archetype."),

      new MavenArchetype("org.apache.maven.archetypes",
                         "maven-archetype-j2ee-simple",
                         "1.0", null,
                         "An archetype which contains a simplifed sample J2EE application."),

      new MavenArchetype("org.apache.maven.archetypes",
                         "maven-archetype-plugin",
                         "1.2", null,
                         "An archetype which contains a sample Maven plugin."),

      new MavenArchetype("org.apache.maven.archetypes",
                         "maven-archetype-plugin-site",
                         "1.1", null,
                         "An archetype which contains a sample Maven plugin site. " +
                         "This archetype can be layered upon an existing Maven plugin project."),

      new MavenArchetype("org.apache.maven.archetypes",
                         "maven-archetype-portlet",
                         "1.0.1", null,
                         "An archetype which contains a sample JSR-268 Portlet."),

      new MavenArchetype("org.apache.maven.archetypes",
                         "maven-archetype-profiles",
                         "1.0-alpha-4", null, ""),

      new MavenArchetype("org.apache.maven.archetypes",
                         "maven-archetype-quickstart",
                         "1.1", null,
                         "An archetype which contains a sample Maven project."),

      new MavenArchetype("org.apache.maven.archetypes",
                         "maven-archetype-site",
                         "1.1", null,
                         "An archetype which contains a sample Maven site which demonstrates some of the supported document types" +
                         " like APT, XDoc, and FML and demonstrates how to i18n your site. " +
                         "This archetype can be layered upon an existing Maven project."),

      new MavenArchetype("org.apache.maven.archetypes",
                         "maven-archetype-site-simple",
                         "1.1", null,
                         "An archetype which contains a sample Maven site."),

      new MavenArchetype("org.apache.maven.archetypes",
                         "maven-archetype-webapp",
                         "1.0", null,
                         "An archetype which contains a sample Maven Webapp project.")
    );
  }

  public Collection<MavenArchetype> getInnerArchetypes(Path path) {
    return executeWithMavenEmbedderWrapper(wrapper -> wrapper.getInnerArchetypes(path));
  }

  public Collection<MavenArchetype> getRemoteArchetypes(URL url) {
    return getRemoteArchetypes(url.toExternalForm());
  }

  public Collection<MavenArchetype> getRemoteArchetypes(String url) {
    return executeWithMavenEmbedderWrapper(wrapper -> wrapper.getRemoteArchetypes(url));
  }

  /**
   * Get archetype descriptor.
   *
   * @return null if archetype not resolved, else descriptor map.
   */
  @Nullable
  public Map<String, String> resolveAndGetArchetypeDescriptor(@NotNull String groupId, @NotNull String artifactId,
                                                              @NotNull String version, @Nullable String url) {
    Map<String, String> map = executeWithMavenEmbedderWrapperNullable(
      wrapper -> wrapper.resolveAndGetArchetypeDescriptor(groupId, artifactId, version, Collections.emptyList(), url)
    );
    if (map != null) addToLocalIndex(groupId, artifactId, version);
    return map;
  }

  private void addToLocalIndex(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
    MavenId mavenId = new MavenId(groupId, artifactId, version);
    MavenRepositoryInfo localRepo = MavenIndexUtils.getLocalRepository(myProject);
    if (localRepo == null) return;
    Path artifactPath = MavenUtil.getArtifactPath(Path.of(localRepo.getUrl()), mavenId, "jar", null);
    if (artifactPath != null && Files.exists(artifactPath)) {
      MavenIndicesManager.getInstance(myProject).scheduleArtifactIndexing(mavenId, artifactPath, localRepo.getUrl());
    }
  }

  @NotNull
  private <R> R executeWithMavenEmbedderWrapper(Function<MavenEmbedderWrapper, R> function) {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
    MavenEmbeddersManager manager = projectsManager.getEmbeddersManager();
    String baseDir = "";
    List<MavenProject> projects = projectsManager.getRootProjects();
    if (!projects.isEmpty()) {
      baseDir = MavenUtil.getBaseDir(projects.get(0).getDirectoryFile()).toString();
    }
    MavenEmbedderWrapper mavenEmbedderWrapper = manager.getEmbedder(FOR_POST_PROCESSING, baseDir);
    try {
      return function.apply(mavenEmbedderWrapper);
    }
    finally {
      manager.release(mavenEmbedderWrapper);
    }
  }

  @Nullable
  private <R> R executeWithMavenEmbedderWrapperNullable(Function<MavenEmbedderWrapper, R> function) {
    MavenEmbeddersManager manager = MavenProjectsManager.getInstance(myProject).getEmbeddersManager();
    MavenEmbedderWrapper mavenEmbedderWrapper = manager.getEmbedder(FOR_POST_PROCESSING, "");
    try {
      return function.apply(mavenEmbedderWrapper);
    }
    finally {
      manager.release(mavenEmbedderWrapper);
    }
  }

  @NotNull
  static List<MavenArchetype> loadUserArchetypes(@NotNull Path userArchetypesPath) {
    try {
      if (!Files.exists(userArchetypesPath)) {
        return Collections.emptyList();
      }

      // Store artifact to set to remove duplicate created by old IDEA (https://youtrack.jetbrains.com/issue/IDEA-72105)
      Collection<MavenArchetype> result = new LinkedHashSet<>();

      List<Element> children = JDOMUtil.load(userArchetypesPath).getChildren(ELEMENT_ARCHETYPE);
      for (int i = children.size() - 1; i >= 0; i--) {
        Element each = children.get(i);

        String groupId = each.getAttributeValue(ELEMENT_GROUP_ID);
        String artifactId = each.getAttributeValue(ELEMENT_ARTIFACT_ID);
        String version = each.getAttributeValue(ELEMENT_VERSION);
        String repository = each.getAttributeValue(ELEMENT_REPOSITORY);
        String description = each.getAttributeValue(ELEMENT_DESCRIPTION);

        if (StringUtil.isEmptyOrSpaces(groupId)
            || StringUtil.isEmptyOrSpaces(artifactId)
            || StringUtil.isEmptyOrSpaces(version)) {
          continue;
        }

        result.add(new MavenArchetype(groupId, artifactId, version, repository, description));
      }

      ArrayList<MavenArchetype> listResult = new ArrayList<>(result);
      Collections.reverse(listResult);

      return listResult;
    }
    catch (IOException | JDOMException e) {
      MavenLog.LOG.warn(e);
      return Collections.emptyList();
    }
  }

  public static void addArchetype(@NotNull MavenArchetype archetype, @NotNull Path userArchetypesPath) {
    List<MavenArchetype> archetypes = new ArrayList<>(loadUserArchetypes(userArchetypesPath));
    int idx = archetypes.indexOf(archetype);
    if (idx >= 0) {
      archetypes.set(idx, archetype);
    }
    else {
      archetypes.add(archetype);
    }

    saveUserArchetypes(archetypes, userArchetypesPath);
  }

  @NotNull
  private static Path getUserArchetypesFile() {
    return MavenSystemIndicesManager.getInstance().getIndicesDir().resolve("UserArchetypes.xml");
  }

  private static void saveUserArchetypes(List<MavenArchetype> userArchetypes, @NotNull Path userArchetypesPath) {
    Element root = new Element(ELEMENT_ARCHETYPES);
    for (MavenArchetype each : userArchetypes) {
      Element childElement = new Element(ELEMENT_ARCHETYPE);
      childElement.setAttribute(ELEMENT_GROUP_ID, each.groupId);
      childElement.setAttribute(ELEMENT_ARTIFACT_ID, each.artifactId);
      childElement.setAttribute(ELEMENT_VERSION, each.version);
      if (each.repository != null) {
        childElement.setAttribute(ELEMENT_REPOSITORY, each.repository);
      }
      if (each.description != null) {
        childElement.setAttribute(ELEMENT_DESCRIPTION, each.description);
      }
      root.addContent(childElement);
    }
    try {
      JDOMUtil.write(root, userArchetypesPath);
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
    }
  }
}
