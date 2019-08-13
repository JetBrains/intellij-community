// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.dom.MavenVersionComparable;
import org.jetbrains.idea.maven.model.MavenCoordinate;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItemWithClass;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collector;

@ApiStatus.Experimental
public class OfflineSearchService implements DependencyCompletionProvider {
  private static final DeduplicationCollector VERSION_COLLECTOR =
    new DeduplicationCollector<>(m -> m.getGroupId() + ":" + m.getArtifactId() + ":" + m.getVersion());
  private final List<DependencyCompletionProvider> myProviders;

  public OfflineSearchService(List<DependencyCompletionProvider> providers) {
    myProviders = providers;
  }


  @NotNull
  public List<MavenDependencyCompletionItem> findByTemplate(@NotNull String coord) {
    return findByTemplate(coord, SearchParameters.DEFAULT);
  }

  @NotNull
  public List<MavenDependencyCompletionItem> findByTemplate(@NotNull String coord, @NotNull SearchParameters parameters) {
    ProgressManager.checkCanceled();
    MavenDependencyCompletionItem template = new MavenDependencyCompletionItem(coord, null);
    if (StringUtil.isEmpty(template.getGroupId())) {
      return Collections.emptyList();
    }
    else if (StringUtil.isEmpty(template.getArtifactId())) {
      List<MavenDependencyCompletionItem> result = new ArrayList<>();
      result.addAll(findGroupCandidates(template, parameters));
      result.addAll(findArtifactCandidates(template, parameters));
      return result;
    }
    else if (StringUtil.isEmpty(template.getVersion())) {
      List<MavenDependencyCompletionItem> result = new ArrayList<>();
      result.addAll(findArtifactCandidates(template, parameters));
      result.addAll(findAllVersions(template, parameters));
      return result;
    }
    return findAllVersions(template, parameters);
  }

  @NotNull
  public List<MavenDependencyCompletionItem> findGroupCandidates(@NotNull MavenCoordinate template) {
    return findGroupCandidates(template, SearchParameters.DEFAULT);
  }

  @Override
  public List<MavenDependencyCompletionItem> findGroupCandidates(@NotNull MavenCoordinate template, @NotNull SearchParameters parameters) {
    return doQuery(parameters, template, (p, s) -> p.findGroupCandidates(s, parameters), VERSION_COLLECTOR, groupMatch(template.getGroupId()),
                   localFirstComparator());
  }


  @NotNull
  public List<MavenDependencyCompletionItem> findArtifactCandidates(@NotNull MavenCoordinate template) {
    return findArtifactCandidates(template, SearchParameters.DEFAULT);
  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItem> findArtifactCandidates(@NotNull MavenCoordinate template,
                                                                    @NotNull SearchParameters parameters) {
    return doQuery(parameters, template, (p, s) -> p.findArtifactCandidates(s, parameters), VERSION_COLLECTOR,
                   groupMatch(template.getGroupId()).and(artifactMatch(template.getArtifactId())), localFirstComparator());
  }

  @NotNull
  public List<MavenDependencyCompletionItem> findAllVersions(@NotNull MavenCoordinate template) {
    return findAllVersions(template, SearchParameters.DEFAULT);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  @Override
  public List<MavenDependencyCompletionItem> findAllVersions(@NotNull MavenCoordinate template, @NotNull SearchParameters parameters) {
    return doQuery(parameters, template, (p, s) -> p.findAllVersions(s, parameters),
                   VERSION_COLLECTOR,
                   groupMatch(template.getGroupId()).and(artifactMatch(template.getArtifactId())),
                   versionComparator());
  }

  @NotNull
  @SuppressWarnings("unchecked")
  @Override
  public List<MavenDependencyCompletionItemWithClass> findClassesByString(@NotNull String className, @NotNull SearchParameters parameters) {
    return doQuery(parameters, className, (p, s) -> p.findClassesByString(s, parameters), VERSION_COLLECTOR, l -> true,
                   localFirstComparator());
  }

  private <PARAM, RESULT extends MavenDependencyCompletionItem> List<RESULT> doQuery(@NotNull SearchParameters parameters,
                                                                                     PARAM template,
                                                                                     ThrowingSearch<PARAM, RESULT> search,
                                                                                     Collector<RESULT, ?, List<RESULT>> collector,
                                                                                     Predicate<RESULT> filter,
                                                                                     Comparator<? super RESULT> comparator) {
    final Application application = ApplicationManager.getApplication();

    List<RESULT> result = myProviders
      .stream().map(provider -> application.executeOnPooledThread(
        () -> search.search(provider, template)))
      .map(f -> {
        try {
          return f.get(parameters.getMillisToWait(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException e) {
          MavenLog.LOG.debug(e);
          return Collections.<RESULT>emptyList();
        }
      })
      .flatMap(l -> l.stream())
      .filter(filter)
      .collect(collector);

    if (comparator != null) {
      Collections.sort(result, comparator);
    }
    return result;
  }

  private static <T extends MavenDependencyCompletionItem> Predicate<T> groupMatch(String groupId) {
    return ci -> StringUtil.isEmpty(groupId) || (ci.getGroupId() != null && ci.getGroupId().contains(groupId));
  }

  private static <T extends MavenDependencyCompletionItem> Predicate<T> artifactMatch(String artifactId) {
    return ci -> StringUtil.isEmpty(artifactId) || (ci.getGroupId() != null &&  ci.getArtifactId()!=null && ci.getArtifactId().contains(artifactId));
  }

  private static <T extends MavenDependencyCompletionItem> Comparator<T> versionComparator() {
    return Comparator.comparing(o -> new MavenVersionComparable(o.getVersion()));
  }

  private static <T extends MavenDependencyCompletionItem> Comparator<T> localFirstComparator() {
    Comparator<T> result = Comparator.comparing(o -> {
      MavenDependencyCompletionItem.Type type = o.getType();
      return type == null ? Integer.MAX_VALUE : type.getWeight();
    });

    return result.reversed();
  }


  @FunctionalInterface
  private interface ThrowingSearch<PARAM, RESULT extends MavenDependencyCompletionItem> {
    List<RESULT> search(DependencyCompletionProvider p, PARAM t) throws IOException;
  }


  @TestOnly
  public List<DependencyCompletionProvider> getProviders() {
    return myProviders;
  }
}