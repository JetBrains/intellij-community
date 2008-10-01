package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.sonatype.nexus.index.ArtifactInfo;

import java.util.*;

public class MavenArtifactSearcher extends MavenSearcher<MavenArtifactSearchResult> {
  protected Pair<String, Query> preparePatternAndQuery(String pattern) {
    pattern = pattern.toLowerCase();
    if (pattern.trim().length() == 0) return Pair.create(pattern, (Query)new MatchAllDocsQuery());

    List<String> parts = new ArrayList<String>();
    for (String each : StringUtil.tokenize(pattern, " :")) {
      parts.add(each);
    }

    BooleanQuery query = new BooleanQuery();

    if (parts.size() == 1) {
      query.add(new WildcardQuery(new Term(ArtifactInfo.UINFO, "*" + parts.get(0) + "*|*|*|*")), BooleanClause.Occur.SHOULD);
      query.add(new WildcardQuery(new Term(ArtifactInfo.UINFO, "*|*" + parts.get(0) + "*|*|*")), BooleanClause.Occur.SHOULD);
    }
    if (parts.size() == 2) {
      query.add(new WildcardQuery(new Term(ArtifactInfo.UINFO, "*" + parts.get(0) + "*|*" + parts.get(1) + "*|*|*")),
                 BooleanClause.Occur.SHOULD);
      query.add(new WildcardQuery(new Term(ArtifactInfo.UINFO, "*" + parts.get(0) + "*|*|" + parts.get(1) + "*|*")),
                 BooleanClause.Occur.SHOULD);
      query.add(new WildcardQuery(new Term(ArtifactInfo.UINFO, "*|*" + parts.get(0) + "*|" + parts.get(1) + "*|*")),
                 BooleanClause.Occur.SHOULD);
    }
    if (parts.size() >= 3) {
      String s = "*" + parts.get(0) + "*|*" + parts.get(1) + "*|" + parts.get(2) + "*|*";
      query.add(new WildcardQuery(new Term(ArtifactInfo.UINFO, s)), BooleanClause.Occur.MUST);
    }

    return Pair.create(pattern, (Query)query);
  }

  protected Collection<MavenArtifactSearchResult> processResults(Set<ArtifactInfo> infos, String pattern, int maxResult) {
    Map<String, MavenArtifactSearchResult> result = new HashMap<String, MavenArtifactSearchResult>();

    for (ArtifactInfo each : infos) {
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
