// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.text.StringUtil.split;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static org.jetbrains.idea.maven.indices.MavenIndices.LOCAL_REPOSITORY_ID;

public class MavenIndexUtils {
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

  public static IndexPropertyHolder readIndexProperty(File dir) throws MavenIndexException {
    Properties props = new Properties();
    try (FileInputStream s = new FileInputStream(new File(dir, INDEX_INFO_FILE))) {
      props.load(s);
    }
    catch (IOException e) {
      throw new MavenIndexException("Cannot read " + INDEX_INFO_FILE + " file", e);
    }

    if (!CURRENT_VERSION.equals(props.getProperty(INDEX_VERSION_KEY))) {
      throw new MavenIndexException("Incompatible index version, needs to be updated: " + dir);
    }

    MavenSearchIndex.Kind kind = MavenSearchIndex.Kind.valueOf(props.getProperty(KIND_KEY));

    Set<String> repositoryIds = Collections.emptySet();
    String myRepositoryIdsStr = props.getProperty(ID_KEY);
    if (myRepositoryIdsStr != null) {
      repositoryIds = Set.copyOf(split(myRepositoryIdsStr, ","));
    }
    String repositoryPathOrUrl = normalizePathOrUrl(props.getProperty(PATH_OR_URL_KEY));
    if (kind != MavenSearchIndex.Kind.LOCAL) {
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

  public static void saveIndexProperty(MavenIndex index) {
    Properties props = new Properties();

    props.setProperty(KIND_KEY, index.getKind().toString());
    props.setProperty(ID_KEY, index.getRepositoryId());
    props.setProperty(PATH_OR_URL_KEY, index.getRepositoryPathOrUrl());
    props.setProperty(INDEX_VERSION_KEY, CURRENT_VERSION);
    props.setProperty(TIMESTAMP_KEY, String.valueOf(index.getUpdateTimestamp()));
    if (index.getDataDirName() != null) props.setProperty(DATA_DIR_NAME_KEY, index.getDataDirName());
    if (index.getFailureMessage() != null) props.setProperty(FAILURE_MESSAGE_KEY, index.getFailureMessage());

    try (FileOutputStream s = new FileOutputStream(new File(index.getDir(), INDEX_INFO_FILE))) {
      props.store(s, null);
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
    }
  }

  @NotNull
  public static Map<String, Set<String>> getRemoteRepositoryIdsByUrl(Project project) {
    if (project.isDisposed()) return Collections.emptyMap();
    return getRemoteRepositoriesMap(project);
  }

  @Nullable
  public static RepositoryInfo getLocalRepository(Project project) {
    if (project.isDisposed()) return null;
    File repository = MavenProjectsManager.getInstance(project).getLocalRepository();
    return repository == null ? null : new RepositoryInfo(LOCAL_REPOSITORY_ID, repository.getPath());
  }

  private static Map<String, Set<String>> getRemoteRepositoriesMap(Project project) {
    if (project.isDisposed()) return Collections.emptyMap();
    Set<MavenRemoteRepository> remoteRepositories = new HashSet<>(MavenUtil.getRemoteResolvedRepositories(project));
    for (MavenRepositoryProvider repositoryProvider : MavenRepositoryProvider.EP_NAME.getExtensions()) {
      remoteRepositories.addAll(repositoryProvider.getRemoteRepositories(project));
    }

    return groupRemoteRepositoriesByUrl(remoteRepositories);
  }


  @VisibleForTesting
  static Map<String, Set<String>> groupRemoteRepositoriesByUrl(Collection<MavenRemoteRepository> remoteRepositories) {
    return remoteRepositories.stream()
      .map(r -> new RepositoryInfo(r.getId(), r.getUrl().toLowerCase(Locale.ROOT)))
      .collect(groupingBy(r -> r.url, mapping(r -> r.id, Collectors.toSet())));
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

  static class IndexPropertyHolder {
    final File dir;
    final MavenSearchIndex.Kind kind;
    final Set<String> repositoryIds;
    final String repositoryPathOrUrl;
    final long updateTimestamp;
    final String dataDirName;
    final String failureMessage;

    IndexPropertyHolder(File dir,
                        MavenSearchIndex.Kind kind,
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

    IndexPropertyHolder(File dir,
                        MavenSearchIndex.Kind kind,
                        Set<String> repositoryIds,
                        String url) {
      this(dir, kind, repositoryIds, url, -1, null, null);
    }
  }

  static class RepositoryInfo {
    @NotNull final String id;
    @NotNull final String url;

    RepositoryInfo(@NotNull String id, @NotNull String url) {
      this.id = id;
      this.url = normalizePathOrUrl(url);
    }
  }
}
