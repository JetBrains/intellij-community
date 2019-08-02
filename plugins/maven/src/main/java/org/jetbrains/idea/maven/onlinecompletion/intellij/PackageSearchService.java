// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.intellij;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.function.Consumer;

public class PackageSearchService {
  private static final MyErrorHandler<Throwable> myErrorHandler = new MyErrorHandler<>();

  private final Gson myGson;
  private final PackageServiceConfig myPackageServiceConfig;

  public PackageSearchService() {
    myGson = new Gson();
    myPackageServiceConfig = new PackageServiceConfig();
  }

  public Promise<Void> fullTextSearch(@NotNull String text,
                                      @NotNull SearchParameters parameters,
                                      @NotNull Consumer<MavenRepositoryArtifactInfo> consumer) {
    ProgressManager.checkCanceled();
    String url = createUrlFullTextSearch(text);
    return doRequest(parameters, consumer, url);
  }


  public Promise<Void> suggestPrefix(@NotNull String groupId,
                                     @NotNull String artifactId,
                                     @NotNull SearchParameters parameters,
                                     @NotNull Consumer<MavenRepositoryArtifactInfo> consumer) {
    ProgressManager.checkCanceled();
    String url = createUrlSuggestPrefix(groupId, artifactId);
    return doRequest(parameters, consumer, url);
  }

  private Promise<Void> doRequest(@NotNull SearchParameters parameters,
                                  @NotNull Consumer<MavenRepositoryArtifactInfo> consumer,
                                  String url) {

    if (url == null) {
      AsyncPromise<Void> result = new AsyncPromise<>();
      result.setResult(null);
      return result;
    }

    Promise<Void> promise = myErrorHandler.errorResult();
    if (promise != null) {
      return promise;
    }


    try {
      return HttpRequests.request(url)
        .userAgent(myPackageServiceConfig.getUserAgent())
        .forceHttps(true)
        .connectTimeout((int)parameters.getMillisToWait())
        .readTimeout((int)parameters.getMillisToWait())
        .connect(request -> process(parameters, consumer, request))
        .onSuccess(v -> myErrorHandler.markSuccess());
    }
    catch (IOException e) {
      AsyncPromise<Void> error = new AsyncPromise<>();
      error.onError(myErrorHandler);
      error.setError(e);
      return error;
    }
  }

  @NotNull
  private Promise<Void> process(@NotNull SearchParameters parameters,
                                @NotNull Consumer<MavenRepositoryArtifactInfo> consumer,
                                HttpRequests.Request request) {
    AsyncPromise<Void> result = new AsyncPromise<>();
    result.onError(myErrorHandler);
    try {
      JsonReader reader = myGson.newJsonReader(request.getReader());
      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        if ("items".equals(name)) {
          readVariants(reader, parameters, consumer);
        }
        else {
          reader.nextString();
        }
      }
      result.setResult(null);
    }
    catch (Exception e) {
      result.setError(e);
    }
    return result;
  }

  private String createUrlFullTextSearch(@NotNull String coord) {
    String url = myPackageServiceConfig.getFullTextUrl();
    if (url == null) {
      return null;
    }

    return url + "?query=" + encode(coord.trim());
  }

  private String createUrlSuggestPrefix(@NotNull String groupId, @NotNull String artifactId) {
    String url = myPackageServiceConfig.getSuggestUrl();
    if (url == null) {
      return null;
    }
    String groupParam = StringUtil.isEmpty(groupId) ? "" : "groupId=" + encode(groupId.trim());
    String artifactParam = StringUtil.isEmpty(artifactId) ? "" : "artifactId=" + encode(artifactId.trim());
    return url + "?" + groupParam + "&" + artifactParam;
  }

  private void readVariants(JsonReader reader,
                            SearchParameters parameters,
                            Consumer<MavenRepositoryArtifactInfo> consumer) throws IOException {
    reader.beginArray();
    int results = 0;
    while (reader.hasNext() && results++ < parameters.getMaxResults()) {
      PackageSearchResultModel resultModel = myGson.fromJson(reader, PackageSearchResultModel.class);
      ProgressManager.checkCanceled();
      if (resultModel.versions == null ||
          resultModel.versions.length < 1 ||
          StringUtil.isEmpty(resultModel.groupId) ||
          StringUtil.isEmpty(resultModel.artifactId)) {
        continue;
      }

      MavenDependencyCompletionItem[] items = new MavenDependencyCompletionItem[resultModel.versions.length];
      for (int i = 0; i < resultModel.versions.length; i++) {
        items[i] = new MavenDependencyCompletionItem(resultModel.groupId, resultModel.artifactId, resultModel.versions[i],
                                                     MavenDependencyCompletionItem.Type.REMOTE);
      }
      consumer.accept(new MavenRepositoryArtifactInfo(items[0].getGroupId(), items[0].getArtifactId(), items));
    }
  }

  @NotNull
  private static String encode(@NotNull String s) {
    try {
      return URLEncoder.encode(s.trim(), "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}
