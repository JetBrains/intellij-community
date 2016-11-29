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
import org.apache.lucene.search.Query;
import org.jetbrains.idea.maven.dom.MavenVersionComparable;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;

import java.util.*;

public abstract class MavenSearcher<RESULT_TYPE extends MavenArtifactSearchResult> {

  public List<RESULT_TYPE> search(Project project, String pattern, int maxResult) {
    Pair<String, Query> patternAndQuery = preparePatternAndQuery(pattern);

    MavenProjectIndicesManager m = MavenProjectIndicesManager.getInstance(project);
    Set<MavenArtifactInfo> infos = m.search(patternAndQuery.second, maxResult);

    List<RESULT_TYPE> result = new ArrayList<>(processResults(infos, patternAndQuery.first, maxResult));
    sort(result);
    return result;
  }

  protected abstract Pair<String, Query> preparePatternAndQuery(String pattern);

  protected abstract Collection<RESULT_TYPE> processResults(Set<MavenArtifactInfo> infos, String pattern, int maxResult);

  private void sort(List<RESULT_TYPE> result) {
    for (RESULT_TYPE each : result) {
      if (each.versions.size() > 1) {
        TreeMap<MavenVersionComparable, MavenArtifactInfo> tree = new TreeMap<>(Collections.reverseOrder());

        for (MavenArtifactInfo artifactInfo : each.versions) {
          tree.put(new MavenVersionComparable(artifactInfo.getVersion()), artifactInfo);
        }

        each.versions.clear();
        each.versions.addAll(tree.values());
      }
    }

    Collections.sort(result, (o1, o2) -> makeSortKey(o1).compareTo(makeSortKey(o2)));
  }

  protected String makeSortKey(RESULT_TYPE result) {
    return makeKey(result.versions.get(0));
  }

  protected String makeKey(MavenArtifactInfo result) {
    return result.getGroupId() + ":" + result.getArtifactId();
  }
}