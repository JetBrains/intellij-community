// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.youtrack;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.tasks.impl.httpclient.TaskResponseUtil.GsonSingleObjectDeserializer;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.*;
import static com.intellij.openapi.editor.HighlighterColors.BAD_CHARACTER;
import static com.intellij.openapi.editor.HighlighterColors.TEXT;

/**
 * Auxiliary class for extracting data from YouTrack intellisense responses.
 * See https://confluence.jetbrains.com/display/YTD5/Intellisense+for+issue+search for format details.
 * <p/>
 * It also provides two additional classes to represent tokens highlighting and
 * available completion items from response: {@link YouTrackIntellisense.HighlightRange}
 * and {@link YouTrackIntellisense.CompletionItem}.
 *
 * @author Mikhail Golubev
 */
public final class YouTrackIntellisense {

  /**
   * Key used to bind YouTrackIntellisense instance to specific PsiFile
   */
  public static final Key<YouTrackIntellisense> INTELLISENSE_KEY = Key.create("youtrack.intellisense");

  private static final Logger LOG = Logger.getInstance(YouTrackIntellisense.class);

  private static final Map<String, TextAttributes> TEXT_ATTRIBUTES = Map.of("field-value", CONSTANT.getDefaultAttributes(), "field-name", KEYWORD.getDefaultAttributes(), "text", STRING.getDefaultAttributes(), "error", BAD_CHARACTER.getDefaultAttributes());
  private static final int CACHE_SIZE = 30;

  private static final class SizeLimitedCache<K, V> extends LinkedHashMap<K, V> {
    private final int myMaxSize;

    private SizeLimitedCache(int max) {
      super((int)(max / 0.75) + 1, true);
      myMaxSize = max;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest, K key, V value) {
      return size() > myMaxSize;
    }
  }

  private static final Map<Pair<String, Integer>, Response> ourCache =
    Collections.synchronizedMap(new SizeLimitedCache<>(CACHE_SIZE));

  @NotNull
  private static TextAttributes getAttributeByStyleClass(@NotNull String styleClass) {
    final TextAttributes attr = TEXT_ATTRIBUTES.get(styleClass);
    return attr == null ? TEXT.getDefaultAttributes() : attr;
  }

  @NotNull
  public List<HighlightRange> fetchHighlighting(@NotNull String query, int caret) throws Exception {
    LOG.debug("Requesting highlighting");
    return fetch(query, caret, true).getHighlightRanges();
  }

  @NotNull
  public List<CompletionItem> fetchCompletion(@NotNull String query, int caret) throws Exception {
    LOG.debug("Requesting completion");
    return fetch(query, caret, false).getCompletionItems();
  }

  private final YouTrackRepository myRepository;

  public YouTrackIntellisense(@NotNull YouTrackRepository repository) {
    myRepository = repository;
  }

  @NotNull
  private Response fetch(@NotNull String query, int caret, boolean ignoreCaret) throws Exception {
    LOG.debug("Query: '" + query + "' caret at: " + caret);
    final Pair<String, Integer> lookup = Pair.create(query, caret);
    Response response = null;
    if (ignoreCaret) {
      for (Pair<String, Integer> pair : ourCache.keySet()) {
        if (pair.getFirst().equals(query)) {
          response = ourCache.get(pair);
          break;
        }
      }
    }
    else {
      response = ourCache.get(lookup);
    }
    LOG.debug("Cache " + (response != null? "hit" : "miss"));
    if (response == null) {
      final long startTime = System.currentTimeMillis();
      URI endpoint = new URIBuilder(myRepository.getRestApiUrl("api", "search", "assist"))
        .addParameter("fields", Response.DEFAULT_FIELDS)
        .build();
      HttpPost request = new HttpPost(endpoint);
      Gson gson = TaskGsonUtil.createDefaultBuilder().create();
      Map<String, Object> payload = Map.of(
        "query", query,
        "caret", caret
      );
      request.setEntity(new StringEntity(gson.toJson(payload), ContentType.APPLICATION_JSON));
      response = myRepository.getHttpClient().execute(request, new GsonSingleObjectDeserializer<>(gson, Response.class));
      LOG.debug(String.format("Intellisense request to YouTrack took %d ms to complete", System.currentTimeMillis() - startTime));
      ourCache.put(lookup, response);
    }
    return response;
  }

  public YouTrackRepository getRepository() {
    return myRepository;
  }

  /**
   * @noinspection unused
   */
  public static class Response {
    private static final String DEFAULT_FIELDS = "styleRanges(length,start,style)," +
                                                 "suggestions(completionEnd,completionStart,description,matchingEnd,matchingStart,option,prefix,suffix)";

    private List<HighlightRange> styleRanges;
    private List<CompletionItem> suggestions;

    @NotNull
    public List<HighlightRange> getHighlightRanges() {
      return styleRanges;
    }

    @NotNull
    public List<CompletionItem> getCompletionItems() {
      return suggestions;
    }
  }

  /**
   * @noinspection unused
   */
  public static class HighlightRange {
    private int start;
    private int length;
    private String style;

    @NotNull
    public String getStyleClass() {
      return StringUtil.notNullize(style);
    }

    @NotNull
    public TextRange getTextRange() {
      return TextRange.from(start, length);
    }

    @NotNull
    public TextAttributes getTextAttributes() {
      return getAttributeByStyleClass(style);
    }
  }

  /**
   * @noinspection unused
   */
  public static class CompletionItem {
    private int completionStart;
    private int completionEnd;
    private int matchingStart;
    private int matchingEnd;
    private String prefix;
    private String suffix;
    private String description;
    private String option;

    @NotNull
    public TextRange getMatchRange() {
      return TextRange.create(matchingStart, matchingEnd);
    }

    @NotNull
    public TextRange getCompletionRange() {
      return TextRange.create(completionStart, completionEnd);
    }

    @NotNull
    public String getDescription() {
      return description;
    }

    @NotNull
    public String getSuffix() {
      return StringUtil.notNullize(suffix);
    }

    @NotNull
    public String getPrefix() {
      return StringUtil.notNullize(prefix);
    }

    @NotNull
    public String getOption() {
      return option;
    }
  }
}
