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

  private static String normalize(@Nullable String string) {
    if (StringUtil.isEmpty(string)) return null;
    StringBuilder builder = new StringBuilder();
    for (char c : string.toCharArray()) {
      if (isAcceptable(c)) {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  private static boolean isAcceptable(char c) {
    return (c >= 'a' && c <= 'z') ||
           (c >= 'A' && c <= 'Z') ||
           (c >= '0' && c <= '9') ||
           c == ':' || c == '-' || c == '.' || c == '_';
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
                         @Nullable String url) {

    if (StringUtil.isEmpty(url)) {
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
    if (StringUtil.isEmpty(url)) {
      return null;
    }
    if (StringUtil.isEmpty(coord)) {
      return url;
    }

    return url + "?query=" + encode(coord.trim());
  }

  private String createUrlSuggestPrefix(@Nullable String groupId, @Nullable String artifactId) {
    String url = myPackageServiceConfig.getSuggestUrl();
    if (StringUtil.isEmpty(url)) {
      return null;
    }

    String groupParam = StringUtil.isEmpty(groupId) ? "" : "groupId=" + encode(groupId.trim());
    String artifactParam = StringUtil.isEmpty(artifactId) ? "" : "artifactId=" + encode(artifactId.trim());

    final StringBuilder sb = new StringBuilder(url);
    if (StringUtil.isNotEmpty(groupParam)) {
      sb.append('?');
      sb.append(groupParam);
      if (StringUtil.isNotEmpty(artifactParam)) sb.append('&');
    }

    if (StringUtil.isNotEmpty(artifactParam)) {
      if (StringUtil.isEmpty(groupParam)) sb.append('?');
      sb.append(artifactParam);
    }

    return sb.toString();
  }

  private void readVariants(JsonReader reader,
                            Consumer<RepositoryArtifactData> consumer) throws IOException {
    reader.beginArray();
    while (reader.hasNext()) {
      PackageSearchResultModel resultModel = myGson.fromJson(reader, PackageSearchResultModel.class);
      ProgressManager.checkCanceled();
      if (resultModel.versions == null ||
          resultModel.versions.length < 1 ||
          StringUtil.isEmpty(resultModel.groupId) ||
          StringUtil.isEmpty(resultModel.artifactId)) {
        continue;
      }

      final Set<String> versions = new HashSet<>();
      final ArrayList<MavenDependencyCompletionItem> itemList = new ArrayList<>();
      for (int i = 0; i < resultModel.versions.length; i++) {
        if (versions.add(resultModel.versions[i])) {
          itemList.add(new MavenDependencyCompletionItem(resultModel.groupId, resultModel.artifactId, resultModel.versions[i],
                                                         MavenDependencyCompletionItem.Type.REMOTE));
        }
      }

      MavenDependencyCompletionItem[] items = itemList.toArray(new MavenDependencyCompletionItem[0]);
      final String groupId = items[0].getGroupId();
      final String artifactId = items[0].getArtifactId();
      if (groupId != null && artifactId != null) {
        consumer.accept(new MavenRepositoryArtifactInfo(groupId, artifactId, items));
      }
    }
  }

  @NotNull
  private static String encode(@NotNull String s) {
    return URLEncoder.encode(s.trim(), StandardCharsets.UTF_8);
  }
}
