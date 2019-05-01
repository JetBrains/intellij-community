// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.stream.Collector;

public class DependencySearchService {

  private static final DeduplicationCollector<MavenDependencyCompletionItem> GROUP_COLLECTOR =
    new DeduplicationCollector<>(m -> m.getGroupId());
  private static final DeduplicationCollector<MavenDependencyCompletionItem> ARTIFACT_COLLECTOR =
    new DeduplicationCollector<>(m -> m.getGroupId() + ":" + m.getArtifactId());
  private static final DeduplicationCollector VERSION_COLLECTOR =
    new DeduplicationCollector<>(m -> m.getGroupId() + ":" + m.getArtifactId() + ":" + m.getVersion());
  private final Project myProject;
  private final List<DependencyCompletionProvider> myProviders;

  public DependencySearchService(Project project, List<DependencyCompletionProvider> providers) {
    myProject = project;
    myProviders = providers;
  }

  public Project getProject() {
    return myProject;
  }

  @NotNull
  public List<MavenDependencyCompletionItem> findByTemplate(@NotNull String coord) {
    return findByTemplate(coord, SearchParameters.DEFAULT);
  }

  @NotNull
  public List<MavenDependencyCompletionItem> findByTemplate(@NotNull String coord, @NotNull SearchParameters parameters) {
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

  public List<MavenDependencyCompletionItem> findGroupCandidates(@NotNull MavenCoordinate template, @NotNull SearchParameters parameters) {
    return doQuery(parameters, template, (p, s) -> p.findGroupCandidates(s, parameters), GROUP_COLLECTOR, localFirstComparator());
  }


  @NotNull
  public List<MavenDependencyCompletionItem> findArtifactCandidates(@NotNull MavenCoordinate template) {
    return findArtifactCandidates(template, SearchParameters.DEFAULT);
  }

  @NotNull
  public List<MavenDependencyCompletionItem> findArtifactCandidates(@NotNull MavenCoordinate template,
                                                                    @NotNull SearchParameters parameters) {
    return doQuery(parameters, template, (p, s) -> p.findArtifactCandidates(s, parameters), ARTIFACT_COLLECTOR, localFirstComparator());
  }

  @NotNull
  public List<MavenDependencyCompletionItem> findAllVersions(@NotNull MavenCoordinate template) {
    return findAllVersions(template, SearchParameters.DEFAULT);
  }

  @NotNull
  public List<MavenDependencyCompletionItem> findAllVersions(@NotNull MavenCoordinate template, @NotNull SearchParameters parameters) {
    return doQuery(parameters, template, (p, s) -> p.findAllVersions(s, parameters), VERSION_COLLECTOR, versionComparator());
  }

  @NotNull
  public List<MavenDependencyCompletionItemWithClass> findClasses(@NotNull String className) {
    return findClasses(className, SearchParameters.DEFAULT);
  }

  @NotNull
  public List<MavenDependencyCompletionItemWithClass> findClasses(@NotNull String className, @NotNull SearchParameters parameters) {
    return doQuery(parameters, className, (p, s) -> p.findClassesByString(s, parameters), VERSION_COLLECTOR, localFirstComparator());
  }


  private <PARAM, RESULT extends MavenDependencyCompletionItem> List<RESULT> doQuery(@NotNull SearchParameters parameters,
                                                                                     PARAM template,
                                                                                     ThrowingSearch<PARAM, RESULT> search,
                                                                                     Collector<? super List<RESULT>, ?, List<RESULT>> collector,
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
      }).collect(collector);

    if (comparator != null) {
      Collections.sort(result, comparator);
    }
    return result;
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