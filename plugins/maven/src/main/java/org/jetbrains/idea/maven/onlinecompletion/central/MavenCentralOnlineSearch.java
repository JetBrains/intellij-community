// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.central;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenCoordinate;
import org.jetbrains.idea.maven.onlinecompletion.DependencyCompletionProvider;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItemWithClass;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters.Flags.ALL_VERSIONS;

public class MavenCentralOnlineSearch implements DependencyCompletionProvider {
  private static final String DEFAULT_SEARCH_URI = "https://search.maven.org/solrsearch/select?q=";
  private final static int MIN_LENGTH = 2;

  private final Gson myGson;
  private final String mySearchUrl;
  public static final String UTF8 = StandardCharsets.UTF_8.toString();

  public MavenCentralOnlineSearch() {
    myGson = new GsonBuilder()
      .registerTypeAdapter(MavenCentralModel.Highlighting.class, new MavenCentralModel.HighlightingDeserializer())
      .create();
    mySearchUrl = DEFAULT_SEARCH_URI;
  }

  @NotNull
  public String getDisplayName() {
    return "Maven Central";
  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItem> findGroupCandidates(MavenCoordinate template, SearchParameters parameters) throws IOException {
    if (StringUtil.isEmpty(template.getGroupId())) {
      return Collections.emptyList();
    }
    String param = join(StringUtil.split(template.getGroupId(), "."));
    if (param.isEmpty()) {
      return Collections.emptyList();
    }
    String uri = createSearchUrl(param, parameters);
    return convert(doRequest(uri));
  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItem> findArtifactCandidates(@NotNull MavenCoordinate template, SearchParameters parameters)
    throws IOException {
    if (template.getGroupId() == null) {
      return findGroupCandidates(new MavenDependencyCompletionItem(template.getArtifactId()), parameters);
    }

    String param = template.getArtifactId() == null
                   ? template.getGroupId()
                   : template.getGroupId() + join(StringUtil.split(template.getArtifactId(), "-"));
    // cause 400 :(
    String uri = createSearchUrl(param, parameters.withoutFlag(ALL_VERSIONS));
    return convert(doRequest(uri));
  }


  @NotNull
  @Override
  public List<MavenDependencyCompletionItem> findAllVersions(MavenCoordinate template, SearchParameters parameters) throws IOException {
    if (template.getArtifactId() == null) {
      return findArtifactCandidates(template, parameters);
    }
    String uri = createSearchUrl("g:\"" + template.getGroupId() + "\" AND a:\"" + template.getArtifactId() + "\"", parameters);
    return convert(doRequest(uri));
  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItemWithClass> findClassesByString(String str, SearchParameters parameters) throws IOException {
    String uri = createSearchUrl(createSearchQuery(str), parameters);
    MavenCentralModel model = doRequest(uri);
    if (model == null || model.highlighting == null || model.highlighting.results == null) {
      return Collections.emptyList();
    }
    return model.highlighting.results;
  }

  private static String createSearchQuery(@NotNull String str) {
    String[] patterns = str.split("\\.");
    StringBuilder builder = new StringBuilder();
    for (String pattern : patterns) {
      builder.append("c:").append("\"").append(pattern).append("\"").append(" OR ");
    }

    builder.append("fc:").append(str);
    return builder.toString();
  }

  private MavenCentralModel doRequest(String uri) throws IOException {
    if (uri == null) {
      return null;
    }
    ProgressManager.checkCanceled();
    return HttpRequests.request(uri)
      .productNameAsUserAgent()
      .forceHttps(false)
      .connect(request -> {
        try {
          String s = request.readString(null);
          return myGson.fromJson(s, MavenCentralModel.class);
        }
        catch (HttpRequests.HttpStatusException ignored) {
          return null;
        }
      });
  }

  private static String join(List<String> splitted) {
    if(splitted.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();

    for (String value : splitted) {
      if (value.length() >= MIN_LENGTH) builder.append(value);
      builder.append(' ');
    }
    if (builder.charAt(builder.length() - 1) == ' ') builder.deleteCharAt(builder.length() - 1);
    return builder.toString();
  }


  @NotNull
  private static List<MavenDependencyCompletionItem> convert(MavenCentralModel model) {
    if (model == null ||
        model.responseHeader.status != 0 ||
        model.response == null ||
        model.response.docs == null ||
        model.response.docs.length == 0) {
      return Collections.emptyList();
    }

    List<MavenDependencyCompletionItem> result = new ArrayList<>();
    for (MavenCentralModel.Response.FoundDoc doc : model.response.docs) {
      MavenDependencyCompletionItem description = new MavenDependencyCompletionItem(
        doc.g,
        doc.a,
        doc.v == null ? doc.latestVersion : doc.v,
        doc.p,
        null,
        MavenDependencyCompletionItem.Type.REMOTE);
      result.add(description);
    }
    return result;
  }


  @Nullable
  private String createSearchUrl(String queryParam, SearchParameters parameters) {
    int rows = parameters.getMaxResults() < 20 ? 20 : parameters.getMaxResults();
    String gav = parameters.getFlags().contains(ALL_VERSIONS) ? "&core=gav" : "";
    try {
      return mySearchUrl + URLEncoder.encode(queryParam, UTF8) + "&rows=" + rows + "&wt=json" + gav;
    }
    catch (UnsupportedEncodingException neverHappens) {
      return null;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MavenCentralOnlineSearch search = (MavenCentralOnlineSearch)o;
    return Objects.equals(mySearchUrl, search.mySearchUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mySearchUrl);
  }
}
