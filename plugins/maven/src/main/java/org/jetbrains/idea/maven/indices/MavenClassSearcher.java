// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.idea.maven.onlinecompletion.OfflineSearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItemWithClass;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;
import org.jetbrains.idea.maven.server.MavenServerIndexer;

import java.util.*;

public class MavenClassSearcher extends MavenSearcher<MavenClassSearchResult> {
  public static final String TERM = MavenServerIndexer.SEARCH_TERM_CLASS_NAMES;

  @Override
  protected List<MavenClassSearchResult> searchImpl(Project project, String pattern, int maxResult) {
    OfflineSearchService service = MavenProjectIndicesManager.getInstance(project).getOfflineSearchService();
    List<MavenDependencyCompletionItemWithClass> items =
      service.findClassesByString(preparePattern(pattern), new SearchParameters(1000, 10000, true, 300));
    return processResults(items, maxResult);
  }

  protected String preparePattern(String pattern) {
    pattern = StringUtil.toLowerCase(pattern);
    if (pattern.trim().length() == 0) {
      return pattern;
    }

    List<String> parts = StringUtil.split(pattern, ".");

    StringBuilder newPattern = new StringBuilder();
    for (int i = 0; i < parts.size() - 1; i++) {
      String each = parts.get(i);
      newPattern.append(each.trim());
      newPattern.append("*.");
    }

    String className = parts.get(parts.size() - 1);
    boolean exactSearch = className.endsWith(" ");
    newPattern.append(className.trim());
    if (!exactSearch) newPattern.append("*");

    return newPattern.toString();
  }

  protected List<MavenClassSearchResult> processResults(List<MavenDependencyCompletionItemWithClass> searchResults,
                                                        int maxResult) {

    Map<String, List<MavenDependencyCompletionItem>> classes = new HashMap<>();

    for (MavenDependencyCompletionItemWithClass item : searchResults) {
      for (String className : item.getNames()) {
        List<MavenDependencyCompletionItem> list = classes.get(className);
        if (list == null) {
          list = new ArrayList<>();
          classes.put(className, list);
        }
        list.add(item);
      }
    }

    List<MavenClassSearchResult> results = new ArrayList<>();
    for (Map.Entry<String, List<MavenDependencyCompletionItem>> entry : classes.entrySet()) {
      String className;
      String packageName;
      int pos = entry.getKey().lastIndexOf(".");
      if (pos == -1) {
        packageName = "default package";
        className = entry.getKey();
      }
      else {
        packageName = entry.getKey().substring(0, pos);
        className = entry.getKey().substring(pos + 1);
      }

      MavenDependencyCompletionItem firstOfBunch = entry.getValue().get(0);
      MavenDependencyCompletionItem[] items = entry.getValue().toArray(new MavenDependencyCompletionItem[0]);
      Arrays.sort(items, Comparator.comparing(c -> c.getVersion(), VersionComparatorUtil.COMPARATOR.reversed()));
      MavenClassSearchResult classResult = new MavenClassSearchResult(
        new MavenRepositoryArtifactInfo(firstOfBunch.getGroupId(), firstOfBunch.getArtifactId(), items), className, packageName);
      results.add(classResult);
    }
    return results;
  }
}