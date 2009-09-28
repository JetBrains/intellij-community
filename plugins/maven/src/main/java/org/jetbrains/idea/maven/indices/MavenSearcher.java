package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.apache.lucene.search.Query;
import org.sonatype.nexus.index.ArtifactInfo;

import java.util.*;

public abstract class MavenSearcher<RESULT_TYPE extends MavenArtifactSearchResult> {
  public List<RESULT_TYPE> search(Project project, String pattern, int maxResult) {
    Pair<String, Query> patternAndQuery = preparePatternAndQuery(pattern);

    MavenProjectIndicesManager m = MavenProjectIndicesManager.getInstance(project);
    Set<ArtifactInfo> infos = m.search(patternAndQuery.second, maxResult);

    List<RESULT_TYPE> result = new ArrayList<RESULT_TYPE>(processResults(infos, patternAndQuery.first, maxResult));
    sort(result);
    return result;
  }

  protected abstract Pair<String, Query> preparePatternAndQuery(String pattern);
  protected abstract Collection<RESULT_TYPE> processResults(Set<ArtifactInfo> infos, String pattern, int maxResult);

  private void sort(List<RESULT_TYPE> result) {
    for (RESULT_TYPE  each : result) {
      Collections.sort(each.versions, ArtifactInfo.VERSION_COMPARATOR);
    }

    Collections.sort(result, new Comparator<RESULT_TYPE>() {
      public int compare(RESULT_TYPE o1, RESULT_TYPE o2) {
        return makeSortKey(o1).compareTo(makeSortKey(o2));
      }
    });
  }

  protected String makeSortKey(RESULT_TYPE result) {
    return makeKey(result.versions.get(0));
  }

  protected String makeKey(ArtifactInfo result) {
    return result.groupId + ":" + result.artifactId;
  }
}