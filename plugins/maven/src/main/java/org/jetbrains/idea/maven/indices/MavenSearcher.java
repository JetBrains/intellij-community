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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.apache.lucene.search.Query;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;

import java.util.*;

public abstract class MavenSearcher<RESULT_TYPE extends MavenArtifactSearchResult> {
  public static final VersionComparator COMPARATOR = new VersionComparator();

  public List<RESULT_TYPE> search(Project project, String pattern, int maxResult) {
    Pair<String, Query> patternAndQuery = preparePatternAndQuery(pattern);

    MavenProjectIndicesManager m = MavenProjectIndicesManager.getInstance(project);
    Set<MavenArtifactInfo> infos = new THashSet<MavenArtifactInfo>(m.search(patternAndQuery.second, maxResult), new HashingStrategy());

    List<RESULT_TYPE> result = new ArrayList<RESULT_TYPE>(processResults(infos, patternAndQuery.first, maxResult));
    sort(result);
    return result;
  }

  protected abstract Pair<String, Query> preparePatternAndQuery(String pattern);

  protected abstract Collection<RESULT_TYPE> processResults(Set<MavenArtifactInfo> infos, String pattern, int maxResult);

  private void sort(List<RESULT_TYPE> result) {
    for (RESULT_TYPE each : result) {
      Collections.sort(each.versions, COMPARATOR);
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

  protected String makeKey(MavenArtifactInfo result) {
    return result.getGroupId() + ":" + result.getArtifactId();
  }

  private static class VersionComparator implements Comparator<MavenArtifactInfo> {
    public int compare(MavenArtifactInfo f1, MavenArtifactInfo f2) {
      int result = Comparing.compare(f1.getGroupId(), f2.getGroupId());
      if (result != 0) return result;

      result = Comparing.compare(f1.getArtifactId(), f2.getArtifactId());
      if (result != 0) return result;

      result = Comparing.compare(f2.getVersion(), f1.getVersion());
      if (result != 0) return result;

      result = Comparing.compare(f1.getPackaging(), f2.getPackaging());
      if (result != 0) return result;

      return Comparing.compare(f1.getClassifier(), f2.getClassifier());
    }
  }

  private static class HashingStrategy implements TObjectHashingStrategy<MavenArtifactInfo> {
    @Override
    public int computeHashCode(MavenArtifactInfo o) {
      return Comparing.hashcode(o.getGroupId()) +
             31 * Comparing.hashcode(o.getArtifactId()) +
             31 * Comparing.hashcode(o.getVersion()) +
             31 * Comparing.hashcode(o.getPackaging()) +
             31 * Comparing.hashcode(o.getClassifier());
    }

    @Override
    public boolean equals(MavenArtifactInfo o1, MavenArtifactInfo o2) {
      return Comparing.equal(o1.getGroupId(), o2.getGroupId()) &&
             Comparing.equal(o1.getArtifactId(), o2.getArtifactId()) &&
             Comparing.equal(o1.getVersion(), o2.getVersion()) &&
             Comparing.equal(o1.getPackaging(), o2.getPackaging()) &&
             Comparing.equal(o1.getClassifier(), o2.getClassifier());
    }
  }
}