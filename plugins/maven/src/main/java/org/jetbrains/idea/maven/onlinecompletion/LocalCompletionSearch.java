// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenCoordinate;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItemWithClass;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem.Type.CACHED_ERROR;
import static org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem.Type.LOCAL;

public class LocalCompletionSearch implements DependencyCompletionProvider {
  private final Path myLocalRepo;

  private static final PathMatcher groupMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*/*");
  private static final PathMatcher pomMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.pom");
  private static final PathMatcher errorUpdatedMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.lastUpdated");
  private static final PathMatcher versionMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.*");

  public LocalCompletionSearch(File localRepo) {
    myLocalRepo = localRepo.toPath();
  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItem> findGroupCandidates(MavenCoordinate template, SearchParameters parameters) throws IOException {
    Set<String> collected = new HashSet<>();
    Files.walkFileTree(myLocalRepo, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path file, BasicFileAttributes attrs) throws IOException {
        boolean isVersion = versionMatcher.matches(file);
        if (isVersion && attrs.isDirectory()) {
          String parentRelativePath = myLocalRepo.relativize(file.getParent().getParent()).toString();
          String groupName = parentRelativePath.replace(File.separatorChar, '.');
          if (!groupName.startsWith(".")) {
            collected.add(groupName);
          }
          return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
      }
    });
    if (parameters.getFlags().contains(SearchParameters.Flags.FULL_RESOLVE)) {
      return collected.stream()
        .flatMap(
        g -> {
          try {
            return findArtifactCandidates(new MavenDependencyCompletionItem(g, LOCAL), parameters).stream();
          }
          catch (IOException e) {
            return Stream.empty();
          }
        })
        .filter(r -> contains(r.getGroupId(), template.getGroupId()) ||
                     contains(r.getArtifactId(), template.getGroupId()) ||
                     contains(r.getArtifactId(), template.getArtifactId()))
        .filter(c-> c.getType() != CACHED_ERROR)
        .collect(Collectors.toList());
    }
    return ContainerUtil.map(collected, g -> new MavenDependencyCompletionItem(g, LOCAL));
  }

  private static boolean contains(@Nullable String str, @Nullable String infix) {
    if (str == null || infix == null) {
      return false;
    }
    return StringUtil.contains(str, infix);
  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItem> findArtifactCandidates(MavenCoordinate template, SearchParameters parameters)
    throws IOException {
    try {
      if (template.getGroupId() == null || template.getGroupId().isEmpty()) {
        return Collections.emptyList();
      }
      File[] files = myLocalRepo.resolve(template.getGroupId().replace('.', File.separatorChar)).toFile().listFiles();
      if (files == null || files.length == 0) {
        return Collections.emptyList();
      }
      if (parameters.getFlags().contains(SearchParameters.Flags.FULL_RESOLVE)) {
        return Arrays.stream(files).flatMap(
          f -> {
            try {
              return findAllVersions(new MavenDependencyCompletionItem(template.getGroupId(), f.getName(), null, LOCAL), parameters)
                .stream();
            }
            catch (IOException e) {
              return Stream.empty();
            }
          }).collect(Collectors.toList());
      }
      return ContainerUtil.map(files, f -> new MavenDependencyCompletionItem(template.getGroupId(), f.getName(), null, LOCAL));
    }
    catch (Exception e) {
      MavenLog.LOG.error(e);
      MavenLog.LOG.error(template);
      return Collections.emptyList();
    }

  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItem> findAllVersions(MavenCoordinate template, SearchParameters parameters) throws IOException {
    if (template.getGroupId() == null || template.getGroupId().isEmpty()
        || template.getArtifactId() == null || template.getArtifactId().isEmpty()) {
      return Collections.emptyList();
    }
    Path artifactDir = myLocalRepo.resolve(template.getGroupId().replace('.', File.separatorChar)).resolve(template.getArtifactId());
    if (!artifactDir.toFile().exists()) {
      return Collections.emptyList();
    }
    List<MavenDependencyCompletionItem> result = new ArrayList<>();
    Files.walkFileTree(artifactDir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path versionDir = file.getParent();
        String version = versionDir.toFile().getName();
        if (errorUpdatedMatcher.matches(file) && attrs.isRegularFile()) {
          result.add(new MavenDependencyCompletionItem(template.getGroupId(), template.getArtifactId(), version, CACHED_ERROR));
          return FileVisitResult.SKIP_SIBLINGS;
        }
        if (pomMatcher.matches(file) && attrs.isRegularFile()) {
          result.add(new MavenDependencyCompletionItem(template.getGroupId(), template.getArtifactId(), version, LOCAL));
          return FileVisitResult.SKIP_SIBLINGS;
        }
        return FileVisitResult.CONTINUE;
      }
    });
    return result;
  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItemWithClass> findClassesByString(String str, SearchParameters parameters) {
    return Collections.emptyList();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LocalCompletionSearch search = (LocalCompletionSearch)o;
    return FileUtil.filesEqual(myLocalRepo.toFile(), search.myLocalRepo.toFile());
  }

  @Override
  public int hashCode() {
    return FileUtil.fileHashCode(myLocalRepo.toFile());
  }
}