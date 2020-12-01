// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.kpmsearch;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.reposearch.DependencySearchProvider;
import org.jetbrains.idea.reposearch.RepositoryArtifactData;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class PackageSearchService implements DependencySearchProvider {

  private final Gson myGson;
  private final PackageSearchEndpointConfig myPackageServiceConfig;

  public PackageSearchService() {
    this(new DefaultPackageServiceConfig());
  }

  public PackageSearchService(PackageSearchEndpointConfig config) {
    myGson = new Gson();
    myPackageServiceConfig = config;
  }


  @Override
  public void fulltextSearch(@NotNull String searchString, @NotNull Consumer<RepositoryArtifactData> consumer) {
    searchString = normalize(searchString);
    ProgressManager.checkCanceled();
    String url = createUrlFullTextSearch(searchString);
    doRequest(consumer, url);
  }

  private String normalize(String string) {
    if (string == null) return null;
    StringBuilder builder = new StringBuilder();
    boolean isOK = true;
    for (char c : string.toCharArray()) {
      if ((c >= 'a' && c <= 'z') ||
          (c >= 'A' && c <= 'Z') ||
          c == ':' || c == '-' || c == '.') {
        builder.append(c);
      }
      else {
        isOK = false;
      }
    }
    return isOK ? string : builder.toString();
  }

  @Override
  public void suggestPrefix(@Nullable String groupId, @Nullable String artifactId, @NotNull Consumer<RepositoryArtifactData> consumer) {
    artifactId = normalize(artifactId);
    groupId = normalize(groupId);
    ProgressManager.checkCanceled();
    String url = createUrlSuggestPrefix(groupId, artifactId);
    doRequest(consumer, url);
  }

  @Override
  public boolean isLocal() {
    return false;
  }


  private void doRequest(@NotNull Consumer<RepositoryArtifactData> consumer,
                         String url) {

    if (url == null) {
      return;
    }

    try {
      HttpRequests.request(url)
        .userAgent(myPackageServiceConfig.getUserAgent())
        .forceHttps(myPackageServiceConfig.forceHttps())
        .connectTimeout(myPackageServiceConfig.getReadTimeout())
        .readTimeout(myPackageServiceConfig.getConnectTimeout())
        .connect(request -> process(consumer, request));
    }
    catch (IOException ignore) {
    }
  }

  private Object process(@NotNull Consumer<RepositoryArtifactData> consumer,
                         HttpRequests.Request request) {
    try {
      JsonReader reader = myGson.newJsonReader(request.getReader());
      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        if ("items".equals(name)) {
          readVariants(reader, consumer);
        }
        else {
          reader.nextString();
        }
      }
    }
    catch (Exception ignore) {

    }
    return null;
  }

  private String createUrlFullTextSearch(@NotNull String coord) {
    String url = myPackageServiceConfig.getFullTextUrl();
    if (url == null) {
      return null;
    }

    return url + "?query=" + encode(coord.trim());
  }

  private String createUrlSuggestPrefix(@Nullable String groupId, @Nullable String artifactId) {
    String url = myPackageServiceConfig.getSuggestUrl();
    if (url == null) {
      return null;
    }
    String groupParam = StringUtil.isEmpty(groupId) ? "" : "groupId=" + encode(groupId.trim());
    String artifactParam = StringUtil.isEmpty(artifactId) ? "" : "artifactId=" + encode(artifactId.trim());
    return url + "?" + groupParam + "&" + artifactParam;
  }

  private void readVariants(JsonReader reader,
                            Consumer<RepositoryArtifactData> consumer) throws IOException {
    reader.beginArray();
    int results = 0;
    while (reader.hasNext() && results++ < 20) {
      PackageSearchResultModel resultModel = myGson.fromJson(reader, PackageSearchResultModel.class);
      ProgressManager.checkCanceled();
      if (resultModel.versions == null ||
          resultModel.versions.length < 1 ||
          StringUtil.isEmpty(resultModel.groupId) ||
          StringUtil.isEmpty(resultModel.artifactId)) {
        continue;
      }

      Set<String> versions = new HashSet<>();
      ArrayList<MavenDependencyCompletionItem> itemList = new ArrayList<>();
      for (int i = 0; i < resultModel.versions.length; i++) {
        if (versions.add(resultModel.versions[i])) {
          itemList.add(new MavenDependencyCompletionItem(resultModel.groupId, resultModel.artifactId, resultModel.versions[i],
                                                         MavenDependencyCompletionItem.Type.REMOTE));
        }
      }
      MavenDependencyCompletionItem[] items = itemList.toArray(new MavenDependencyCompletionItem[0]);
      consumer.accept(new MavenRepositoryArtifactInfo(items[0].getGroupId(), items[0].getArtifactId(), items));
    }
  }

  @NotNull
  private static String encode(@NotNull String s) {
    return URLEncoder.encode(s.trim(), StandardCharsets.UTF_8);
  }
}
