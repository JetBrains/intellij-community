// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.model.RepositoryKind;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.text.StringUtil.split;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static org.jetbrains.idea.maven.indices.MavenIndices.LOCAL_REPOSITORY_ID;

public final class MavenIndexUtils {
  private static final String CURRENT_VERSION = "5";
  private static final String INDEX_INFO_FILE = "index.properties";

  private static final String INDEX_VERSION_KEY = "version";
  private static final String KIND_KEY = "kind";
  private static final String ID_KEY = "id";
  private static final String PATH_OR_URL_KEY = "pathOrUrl";
  private static final String TIMESTAMP_KEY = "lastUpdate";
  private static final String DATA_DIR_NAME_KEY = "dataDirName";
  private static final String FAILURE_MESSAGE_KEY = "failureMessage";

  private MavenIndexUtils() { }

  @Nullable
  public static IndexPropertyHolder readIndexProperty(Path dir) throws MavenIndexException {
    Properties props = new Properties();
    try (InputStream s = Files.newInputStream(dir.resolve(INDEX_INFO_FILE))) {
      props.load(s);
    }
    catch (FileNotFoundException e) {
      return null;
    }
    catch (IOException e) {
      throw new MavenIndexException("Cannot read " + INDEX_INFO_FILE + " file", e);
    }

    if (!CURRENT_VERSION.equals(props.getProperty(INDEX_VERSION_KEY))) {
      throw new MavenIndexException("Incompatible index version, needs to be updated: " + dir);
    }

    RepositoryKind kind = RepositoryKind.valueOf(props.getProperty(KIND_KEY));

    Set<String> repositoryIds = Collections.emptySet();
    String myRepositoryIdsStr = props.getProperty(ID_KEY);
    if (myRepositoryIdsStr != null) {
      repositoryIds = Set.copyOf(split(myRepositoryIdsStr, ","));
    }
    String repositoryPathOrUrl = normalizePathOrUrl(props.getProperty(PATH_OR_URL_KEY));
    if (kind != RepositoryKind.LOCAL) {
      repositoryPathOrUrl = repositoryPathOrUrl.toLowerCase(Locale.ROOT);
    }
    long updateTimestamp = -1L;
    try {
      String timestamp = props.getProperty(TIMESTAMP_KEY);
      if (timestamp != null) updateTimestamp = Long.parseLong(timestamp);
    }
    catch (Exception ignored) {
    }

    String dataDirName = props.getProperty(DATA_DIR_NAME_KEY);
    String failureMessage = props.getProperty(FAILURE_MESSAGE_KEY);
    return new IndexPropertyHolder(dir, kind, repositoryIds, repositoryPathOrUrl, updateTimestamp, dataDirName, failureMessage);
  }

  public static void saveIndexProperty(MavenIndexImpl index) {
    Properties props = new Properties();

    props.setProperty(KIND_KEY, index.getRepository().getKind().toString());
    props.setProperty(ID_KEY, index.getRepositoryId());
    props.setProperty(PATH_OR_URL_KEY, index.getRepositoryPathOrUrl());
    props.setProperty(INDEX_VERSION_KEY, CURRENT_VERSION);
    props.setProperty(TIMESTAMP_KEY, String.valueOf(index.getUpdateTimestamp()));
    if (index.getDataDirName() != null) props.setProperty(DATA_DIR_NAME_KEY, index.getDataDirName());
    if (index.getFailureMessage() != null) props.setProperty(FAILURE_MESSAGE_KEY, index.getFailureMessage());

    try (OutputStream s = Files.newOutputStream(index.getDir().resolve(INDEX_INFO_FILE))) {
      props.store(s, null);
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
    }
  }

  @Nullable
  public static MavenRepositoryInfo getLocalRepository(Project project) {
    if (project.isDisposed()) return null;
    Path repository = MavenProjectsManager.getInstance(project).getReposirotyPath();
    return repository == null
           ? null
           : new MavenRepositoryInfo(LOCAL_REPOSITORY_ID, LOCAL_REPOSITORY_ID, repository.toString(), RepositoryKind.LOCAL);
  }

  @NotNull
  public static List<MavenRemoteRepository> getRemoteRepositoriesNoResolve(Project project) {

    if (project.isDisposed()) {
      return Collections.emptyList();
    }
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    Set<MavenRemoteRepository> repositories = projectsManager.getRemoteRepositories();

    Set<MavenRemoteRepository> remoteRepositories = new HashSet<>(repositories);
    for (MavenRepositoryProvider repositoryProvider : MavenRepositoryProvider.EP_NAME.getExtensionList()) {
      remoteRepositories.addAll(repositoryProvider.getRemoteRepositories(project));
    }

    return remoteRepositories.stream().toList();
  }


  @VisibleForTesting
  static Map<String, Set<String>> groupRemoteRepositoriesByUrl(Collection<MavenRemoteRepository> remoteRepositories) {
    return remoteRepositories.stream()
      .map(r -> new MavenRepositoryInfo(r.getId(), normalizePathOrUrl(r.getUrl().toLowerCase(Locale.ROOT)), RepositoryKind.REMOTE))
      .collect(groupingBy(r -> r.getUrl(), mapping(r -> r.getId(), Collectors.toSet())));
  }

  @NotNull
  public static String normalizePathOrUrl(@NotNull String pathOrUrl) {
    pathOrUrl = pathOrUrl.trim();
    pathOrUrl = FileUtil.toSystemIndependentName(pathOrUrl);
    while (pathOrUrl.endsWith("/")) {
      pathOrUrl = pathOrUrl.substring(0, pathOrUrl.length() - 1);
    }
    return pathOrUrl;
  }

  static List<MavenRepositoryInfo> getAllRepositories(Project project) {
    List<MavenRepositoryInfo> all = new ArrayList<>();
    var local = getLocalRepository(project);
    if (local != null) {
      all.add(local);
    }
    all.addAll(ContainerUtil.map(getRemoteRepositoriesNoResolve(project), rr -> new MavenRepositoryInfo(rr.getId(), rr.getName(), rr.getUrl(), RepositoryKind.REMOTE)));
    return all;
  }

  public static class IndexPropertyHolder {
    final Path dir;
    final RepositoryKind kind;
    final Set<String> repositoryIds;
    final String repositoryPathOrUrl;
    final long updateTimestamp;
    final String dataDirName;
    final String failureMessage;

    IndexPropertyHolder(Path dir,
                        RepositoryKind kind,
                        Set<String> repositoryIds,
                        String url,
                        long timestamp,
                        String dataDirName,
                        String message) {
      this.dir = dir;
      this.kind = kind;
      this.repositoryIds = repositoryIds;
      this.repositoryPathOrUrl = url;
      this.updateTimestamp = timestamp;
      this.dataDirName = dataDirName;
      this.failureMessage = message;
    }

    IndexPropertyHolder(Path dir,
                        RepositoryKind kind,
                        Set<String> repositoryIds,
                        String url) {
      this(dir, kind, repositoryIds, url, -1, null, null);
    }
  }
}
