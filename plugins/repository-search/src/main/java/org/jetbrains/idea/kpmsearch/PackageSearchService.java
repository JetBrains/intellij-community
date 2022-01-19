// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.kpmsearch;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
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

public final class PackageSearchService implements DependencySearchProvider {
  private final PackageSearchEndpointConfig packageServiceConfig;

  public PackageSearchService() {
    packageServiceConfig = ApplicationManager.getApplication().getService(DefaultPackageServiceConfig.class);
  }

  public PackageSearchService(PackageSearchEndpointConfig config) {
    packageServiceConfig = config;
  }

  @Override
  public void fulltextSearch(@NotNull String searchString, @NotNull Consumer<RepositoryArtifactData> consumer) {
    searchString = normalize(searchString);
    ProgressManager.checkCanceled();
    if (searchString != null) {
      doRequest(consumer, createUrlFullTextSearch(searchString));
    }
  }

  private static @Nullable String normalize(@Nullable String string) {
    if (Strings.isEmpty(string)) {
      return null;
    }
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
    doRequest(consumer, createUrlSuggestPrefix(groupId, artifactId));
  }

  @Override
  public boolean isLocal() {
    return false;
  }

  private void doRequest(@NotNull Consumer<RepositoryArtifactData> consumer, @Nullable String url) {
    if (Strings.isEmpty(url)) {
      return;
    }

    try {
      HttpRequests.request(url)
        .userAgent(packageServiceConfig.getUserAgent())
        .forceHttps(packageServiceConfig.forceHttps())
        .connectTimeout(packageServiceConfig.getReadTimeout())
        .readTimeout(packageServiceConfig.getConnectTimeout())
        .connect(request -> process(consumer, request));
    }
    catch (IOException ignore) {
    }
  }

  private static Object process(@NotNull Consumer<RepositoryArtifactData> consumer, HttpRequests.Request request) {
    try {
      JsonReader reader = new Gson().newJsonReader(request.getReader());
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
    String url = packageServiceConfig.getFullTextUrl();
    if (Strings.isEmpty(url)) {
      return null;
    }
    else if (Strings.isEmpty(coord)) {
      return url;
    }
    else {
      return url + "?query=" + encode(coord.trim());
    }
  }

  private String createUrlSuggestPrefix(@Nullable String groupId, @Nullable String artifactId) {
    String url = packageServiceConfig.getSuggestUrl();
    if (Strings.isEmpty(url)) {
      return null;
    }

    String groupParam = Strings.isEmpty(groupId) ? "" : "groupId=" + encode(groupId.trim());
    String artifactParam = Strings.isEmpty(artifactId) ? "" : "artifactId=" + encode(artifactId.trim());

    final StringBuilder sb = new StringBuilder(url);
    if (Strings.isNotEmpty(groupParam)) {
      sb.append('?');
      sb.append(groupParam);
      if (Strings.isNotEmpty(artifactParam)) sb.append('&');
    }

    if (Strings.isNotEmpty(artifactParam)) {
      if (Strings.isEmpty(groupParam)) {
        sb.append('?');
      }
      sb.append(artifactParam);
    }

    return sb.toString();
  }

  private static void readVariants(JsonReader reader, Consumer<RepositoryArtifactData> consumer) throws IOException {
    reader.beginArray();
    while (reader.hasNext()) {
      PackageSearchResultModel resultModel = new Gson().fromJson(reader, PackageSearchResultModel.class);
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

  private static @NotNull String encode(@NotNull String s) {
    return URLEncoder.encode(s.trim(), StandardCharsets.UTF_8);
  }
}
