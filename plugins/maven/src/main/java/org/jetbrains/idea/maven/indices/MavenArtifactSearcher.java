// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;

import java.util.*;

import static org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters.Flags.ALL_VERSIONS;

public class MavenArtifactSearcher extends MavenSearcher<MavenArtifactSearchResult> {


  @Override
  protected List<MavenArtifactSearchResult> searchImpl(Project project, String pattern, int maxResult) {
    if (StringUtil.isEmpty(pattern)) {
      return Collections.emptyList();
    }
    DependencySearchService service = MavenProjectIndicesManager.getInstance(project).getSearchService();
    List<MavenDependencyCompletionItem> searchResults =
      service.findByTemplate(pattern, new SearchParameters(1000, 5000, EnumSet.of(ALL_VERSIONS)));
    return processResults(searchResults, pattern);
  }

  private static List<MavenArtifactSearchResult> processResults(List<MavenDependencyCompletionItem> searchResults, String pattern) {
    Map<String, List<MavenDependencyCompletionItem>> results = new HashMap<>();
    for (MavenDependencyCompletionItem item : searchResults) {
      if (item.getGroupId() == null ||
          item.getArtifactId() == null ||
          item.getVersion() == null ||
          item.getType() == MavenDependencyCompletionItem.Type.CACHED_ERROR) {
        continue;
      }
      String key = item.getGroupId() + ":" + item.getArtifactId();

      List<MavenDependencyCompletionItem> list = results.get(key);
      if (list == null) {
        list = new ArrayList<>();
        results.put(key, list);
      }
      list.add(item);
    }
    ;
    return ContainerUtil.map(results.values(), MavenArtifactSearchResult::new);
  }
}
