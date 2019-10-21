// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.WaitFor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.apache.commons.lang.ArrayUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.idea.maven.model.MavenCoordinate;
import org.jetbrains.idea.maven.onlinecompletion.intellij.PackageSearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApiStatus.Experimental
public class DependencySearchService {
  private final Project myProject;

  private final PackageSearchService myPackageSearchService = new PackageSearchService();
  private volatile OfflineSearchService myOfflineSearchService = new OfflineSearchService(Collections.emptyList());
  private volatile long myLastRequestedTime = -1;

  public DependencySearchService(Project project) {
    myProject = project;
    reload();
  }

  public OfflineSearchService getOfflineSearchService() {
    return myOfflineSearchService;
  }


  public final void reload() {
    List<DependencyCompletionProvider> providers = new ArrayList<>();
    List<DependencyCompletionProviderFactory> factoryList = DependencyCompletionProviderFactory.EP_NAME.getExtensionList();
    for (DependencyCompletionProviderFactory factory : factoryList) {
      if (factory.isApplicable(myProject)) {
        providers.addAll(factory.getProviders(myProject));
      }
    }

    myOfflineSearchService = new OfflineSearchService(providers);
  }

  public Promise<Void> fulltextSearch(@NotNull String template,
                                      @NotNull SearchParameters parameters,
                                      @NotNull Consumer<MavenRepositoryArtifactInfo> consumer) {

    if (skipRequest(parameters)) {
      return Promises.resolvedPromise(null);
    }
    myLastRequestedTime = System.currentTimeMillis();
    MavenDependencyCompletionItem localSearchItem = new MavenDependencyCompletionItem(template);
    CollectingConsumer collectingConsumer = new CollectingConsumer(consumer, parameters);
    final Promise<Void> returnPromise = createPromiseWithStatisticHandlers(parameters, "fulltext");
    searchLocal(parameters, localSearchItem, collectingConsumer);


    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ProgressManager.checkCanceled();
      Promise<Void> promise = myPackageSearchService.fullTextSearch(template, parameters, d ->
        mergeWithVersionsInLocalCache(d, collectingConsumer));//rewriting Type for data, which are present on local drive

      completeProcess(collectingConsumer, returnPromise, promise);
    });
    return returnPromise;
  }

  private boolean skipRequest(SearchParameters parameters) {
    return System.currentTimeMillis() - myLastRequestedTime < parameters.getThrottleTime();
  }

  public Promise<Void> suggestPrefix(@NotNull String groupId,
                                     @NotNull String artifactId,
                                     @NotNull SearchParameters parameters,
                                     @NotNull Consumer<MavenRepositoryArtifactInfo> consumer) {

    if (skipRequest(parameters)) {
      return Promises.resolvedPromise(null);
    }
    myLastRequestedTime = System.currentTimeMillis();

    MavenDependencyCompletionItem localSearchItem = new MavenDependencyCompletionItem(groupId, artifactId, null);

    CollectingConsumer collectingConsumer = new CollectingConsumer(consumer, parameters);

    final Promise<Void> returnPromise = createPromiseWithStatisticHandlers(parameters, "suggestPrefix");

    searchLocal(parameters, localSearchItem, collectingConsumer);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ProgressManager.checkCanceled();
      Promise<Void> promise = myPackageSearchService
        .suggestPrefix(groupId, artifactId, parameters, d -> mergeWithVersionsInLocalCache(d, collectingConsumer));

      completeProcess(collectingConsumer, returnPromise, promise);
    });
    return returnPromise;
  }

  private static void completeProcess(CollectingConsumer collectingConsumer,
                                      Promise<Void> remotePromise,
                                      Promise<Void> promise) {
    promise.onProcessed(v -> collectingConsumer.consumeLocalOnly())
      .processed(remotePromise);
  }

  private void searchLocal(SearchParameters parameters,
                           MavenDependencyCompletionItem localSearchItem,
                           CollectingConsumer collectingConsumer) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<MavenDependencyCompletionItem> versions = getOfflineData(localSearchItem, parameters);
      collectingConsumer.setLocalData(convertLocalItemsToArtifactInfo(versions));
    }); // need to request, if there are local dependencies, which are missing on remote side
  }

  @NotNull
  private static Promise<Void> createPromiseWithStatisticHandlers(SearchParameters parameters, String prefix) {
    final Promise<Void> remotePromise = new AsyncPromise<>();
    final long timeStart = System.currentTimeMillis();
    remotePromise
      .onError(e ->
                 MavenDependencySearchStatisticsCollector
                   .notifyError(prefix, parameters, System.currentTimeMillis() - timeStart, e)
      ).onSuccess(v ->
                    MavenDependencySearchStatisticsCollector
                      .notifySuccess(prefix, parameters, System.currentTimeMillis() - timeStart)
    );
    return remotePromise;
  }


  private List<MavenDependencyCompletionItem> getOfflineData(MavenCoordinate coordinate, SearchParameters params) {
    if (StringUtil.isNotEmpty(coordinate.getVersion())) {
      return myOfflineSearchService.findAllVersions(coordinate, params);
    }
    if (StringUtil.isNotEmpty(coordinate.getArtifactId())) {
      return myOfflineSearchService.findArtifactCandidates(coordinate, params).stream()
        .flatMap(md -> myOfflineSearchService.findAllVersions(md, params).stream())
        .collect(Collectors.toList());
    }
    return myOfflineSearchService.findGroupCandidates(coordinate, params)
      .stream()
      .flatMap(md -> myOfflineSearchService.findArtifactCandidates(md, params).stream())
      .flatMap(md -> myOfflineSearchService.findAllVersions(md, params).stream())
      .collect(Collectors.toList());
  }

  private void mergeWithVersionsInLocalCache(MavenRepositoryArtifactInfo artifactInfo, Consumer<MavenRepositoryArtifactInfo> consumer) {
    Map<MavenDependencyCompletionItem, MavenDependencyCompletionItem> offlineResults =
      myOfflineSearchService.findAllVersions(artifactInfo).stream().filter(v -> v.getVersion() != null)
        .collect(Collectors.toMap(Function.identity(), Function.identity()));

    for (int i = 0; i < artifactInfo.getItems().length; i++) {

      MavenDependencyCompletionItem replacement = offlineResults.remove(artifactInfo.getItems()[i]);
      if (replacement != null) {
        MavenDependencyCompletionItem old = artifactInfo.getItems()[i];
        artifactInfo.getItems()[i] =
          new MavenDependencyCompletionItem(old.getGroupId(), old.getArtifactId(), old.getVersion(), old.getPackaging(),
                                            old.getClassifier(),
                                            replacement.getType());
      }
    }
    if (offlineResults.isEmpty()) {
      consumer.accept(artifactInfo);
    }
    else {
      MavenDependencyCompletionItem[] newArray =
        (MavenDependencyCompletionItem[])ArrayUtils
          .addAll(artifactInfo.getItems(), offlineResults.values().toArray(new MavenDependencyCompletionItem[0]));
      Arrays.sort(newArray, Comparator.comparing(d -> d.getVersion(), VersionComparatorUtil.COMPARATOR.reversed()));
      consumer.accept(new MavenRepositoryArtifactInfo(artifactInfo.getGroupId(), artifactInfo.getArtifactId(), newArray));
    }
  }

  private static List<MavenRepositoryArtifactInfo> convertLocalItemsToArtifactInfo(List<MavenDependencyCompletionItem> items) {
    Map<Pair<String, String>, List<MavenDependencyCompletionItem>> collect =
      items.stream().collect(Collectors.groupingBy(i -> new Pair<>(i.getGroupId(), i.getArtifactId())));
    List<MavenRepositoryArtifactInfo> map =
      ContainerUtil.map(collect.entrySet(), e -> new MavenRepositoryArtifactInfo(true, e.getKey().first, e.getKey().second, e.getValue()
        .toArray(new MavenDependencyCompletionItem[0])));
    Collections.sort(map, Comparator.comparing(d -> d.getVersion(), VersionComparatorUtil.COMPARATOR.reversed()));
    return map;
  }

  private static class CollectingConsumer implements Consumer<MavenRepositoryArtifactInfo> {
    private final Consumer<MavenRepositoryArtifactInfo> myConsumer;
    private final SearchParameters myParameters;
    private volatile List<MavenRepositoryArtifactInfo> myLocalData;
    private final Set<String> acceptedRemotely = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> remoteGroups = Collections.newSetFromMap(new ConcurrentHashMap<>());

    CollectingConsumer(Consumer<MavenRepositoryArtifactInfo> consumer,
                       SearchParameters parameters) {
      myConsumer = consumer;
      myParameters = parameters;
    }

    public void consumeLocalOnly() {
      new WaitFor((int)myParameters.getMillisToWait()) {
        @Override
        protected boolean condition() {
          ProgressManager.checkCanceled();
          return myLocalData != null;
        }
      };
      if (myLocalData == null) {
        return;
      }
      for (MavenRepositoryArtifactInfo info : myLocalData) {
        if (StringUtil.isEmpty(info.getArtifactId())) {
          if (!remoteGroups.contains(info.getGroupId())) {
            myConsumer.accept(info);
          }
          continue;
        }
        String key = getKey(info);
        if (!acceptedRemotely.contains(key)) {
          myConsumer.accept(info);
        }
      }
    }

    public void setLocalData(List<MavenRepositoryArtifactInfo> localData) {
      myLocalData = localData == null ? Collections.emptyList() : localData;
    }

    @Override
    public void accept(MavenRepositoryArtifactInfo info) {
      String key = getKey(info);
      acceptedRemotely.add(key);
      remoteGroups.add(info.getGroupId());
      myConsumer.accept(info);
    }

    @NotNull
    private static String getKey(MavenRepositoryArtifactInfo info) {
      return info.getGroupId() + ":" + info.getArtifactId();
    }
  }
}