package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.Pair;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.sonatype.nexus.index.ArtifactInfo;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MavenClassSearcher extends MavenSearcher<MavenClassSearchResult> {
  protected Pair<String, Query> preparePatternAndQuery(String pattern) {
    pattern = pattern.toLowerCase();
    if (pattern.trim().length() == 0) {
      return new Pair<String, Query>(pattern, new MatchAllDocsQuery());
    }

    boolean exactSearch = pattern.endsWith(" ");
    pattern = pattern.trim();
    if (!exactSearch) pattern += "*";
    Term term = new Term(ArtifactInfo.NAMES, pattern);

    return new Pair<String, Query>(pattern, exactSearch ? new TermQuery(term) : new WildcardQuery(term));
  }

  protected Collection<MavenClassSearchResult> processResults(Set<ArtifactInfo> infos, String pattern, int maxResult) {
    if (pattern.length() == 0 || pattern.equals("*")) {
      pattern = "^(.*)$";
    }
    else {
      pattern = pattern.replace(".", "/");
      pattern = pattern.replaceAll("\\*", "[^/]*?");
      pattern = "^(.*?/" + pattern + ")$";
    }
    Pattern p;
    try {
      p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    }
    catch (PatternSyntaxException e) {
      return Collections.emptyList();
    }

    Map<String, MavenClassSearchResult> result = new HashMap<String, MavenClassSearchResult>();
    for (ArtifactInfo each : infos) {
      if (each.classNames == null) continue;

      Matcher matcher = p.matcher(each.classNames);
      while (matcher.find()) {
        String classFQName = matcher.group();
        classFQName = classFQName.replace("/", ".");
        String key = makeKey(classFQName, each);

        MavenClassSearchResult classResult = result.get(key);
        if (classResult == null) {
          classResult = new MavenClassSearchResult();
          int pos = classFQName.lastIndexOf(".");
          if (pos == -1) {
            classResult.packageName = "default package";
            classResult.className = classFQName;
          }
          else {
            classResult.packageName = classFQName.substring(0, pos);
            classResult.className = classFQName.substring(pos + 1);
          }
          result.put(key, classResult);
        }

        classResult.versions.add(each);

        if (result.size() > maxResult) break;
      }
    }

    return result.values();
  }

  @Override
  protected String makeSortKey(MavenClassSearchResult result) {
    return makeKey(result.className, result.versions.get(0));
  }

  private String makeKey(String className, ArtifactInfo info) {
    return className + " " + super.makeKey(info);
  }
}