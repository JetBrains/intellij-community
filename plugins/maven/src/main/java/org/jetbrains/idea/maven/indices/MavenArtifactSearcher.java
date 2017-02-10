/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.server.MavenServerIndexer;

import java.util.*;

public class MavenArtifactSearcher extends MavenSearcher<MavenArtifactSearchResult> {
  public static final String TERM = MavenServerIndexer.SEARCH_TERM_COORDINATES;

  private final boolean myUseLuceneIndexes;

  public MavenArtifactSearcher() {
    this(false);
  }

  public MavenArtifactSearcher(boolean useLuceneIndexes) {
    myUseLuceneIndexes = useLuceneIndexes;
  }

  @Override
  public List<MavenArtifactSearchResult> search(Project project, String pattern, int maxResult) {
    return myUseLuceneIndexes ? super.search(project, pattern, maxResult) : getResultsFromIdeaCache(project, pattern, maxResult);
  }

  @NotNull
  private static List<MavenArtifactSearchResult> getResultsFromIdeaCache(Project project, String pattern, int maxResult) {
    List<String> parts = new ArrayList<>();
    for (String each : StringUtil.tokenize(pattern, " :")) {
      parts.add(each);
    }

    List<MavenArtifactSearchResult> searchResults = ContainerUtil.newSmartList();
    MavenProjectIndicesManager m = MavenProjectIndicesManager.getInstance(project);
    int count = 0;
    for (String groupId : m.getGroupIds()) {
      if (count >= maxResult) break;
      if (parts.size() < 1 || StringUtil.contains(groupId, parts.get(0))) {
        for (String artifactId : m.getArtifactIds(groupId)) {
          if (parts.size() < 2 || StringUtil.contains(artifactId, parts.get(1))) {
            List<MavenArtifactInfo> versions = ContainerUtil.newSmartList();
            for (String version : m.getVersions(groupId, artifactId)) {
              if (parts.size() < 3 || StringUtil.contains(version, parts.get(2))) {
                versions.add(new MavenArtifactInfo(groupId, artifactId, version, "jar", null));
                if (++count >= maxResult) break;
              }
            }

            if (!versions.isEmpty()) {
              MavenArtifactSearchResult searchResult = new MavenArtifactSearchResult();
              searchResult.versions.addAll(versions);
              searchResults.add(searchResult);
            }
          }

          if (count >= maxResult) break;
        }
      }
    }
    return searchResults;
  }

  protected Pair<String, Query> preparePatternAndQuery(String pattern) {
    pattern = StringUtil.toLowerCase(pattern);
    if (pattern.trim().length() == 0) return Pair.create(pattern, (Query)new MatchAllDocsQuery());

    List<String> parts = new ArrayList<>();
    for (String each : StringUtil.tokenize(pattern, " :")) {
      parts.add(each);
    }

    BooleanQuery query = new BooleanQuery();

    if (parts.size() == 1) {
      query.add(new WildcardQuery(new Term(TERM, "*" + parts.get(0) + "*|*|*|*")), BooleanClause.Occur.SHOULD);
      query.add(new WildcardQuery(new Term(TERM, "*|*" + parts.get(0) + "*|*|*")), BooleanClause.Occur.SHOULD);
    }
    if (parts.size() == 2) {
      query.add(new WildcardQuery(new Term(TERM, "*" + parts.get(0) + "*|*" + parts.get(1) + "*|*|*")), BooleanClause.Occur.SHOULD);
      query.add(new WildcardQuery(new Term(TERM, "*" + parts.get(0) + "*|*|" + parts.get(1) + "*|*")), BooleanClause.Occur.SHOULD);
      query.add(new WildcardQuery(new Term(TERM, "*|*" + parts.get(0) + "*|" + parts.get(1) + "*|*")), BooleanClause.Occur.SHOULD);
    }
    if (parts.size() >= 3) {
      String s = "*" + parts.get(0) + "*|*" + parts.get(1) + "*|" + parts.get(2) + "*|*";
      query.add(new WildcardQuery(new Term(TERM, s)), BooleanClause.Occur.MUST);
    }

    return Pair.create(pattern, (Query)query);
  }

  protected Collection<MavenArtifactSearchResult> processResults(Set<MavenArtifactInfo> infos, String pattern, int maxResult) {
    Map<String, MavenArtifactSearchResult> result = new THashMap<>();

    for (MavenArtifactInfo each : infos) {
      if (!StringUtil.isEmptyOrSpaces(each.getClassifier())) {
        continue; // todo skip for now
      }

      String key = makeKey(each);
      MavenArtifactSearchResult searchResult = result.get(key);
      if (searchResult == null) {
        searchResult = new MavenArtifactSearchResult();
        result.put(key, searchResult);
      }
      searchResult.versions.add(each);
    }

    return result.values();
  }
}
