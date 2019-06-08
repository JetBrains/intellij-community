// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItemWithClass;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;
import org.jetbrains.idea.maven.server.MavenServerIndexer;

import java.util.*;

import static org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters.Flags.ALL_VERSIONS;

public class MavenClassSearcher extends MavenSearcher<MavenClassSearchResult> {
  public static final String TERM = MavenServerIndexer.SEARCH_TERM_CLASS_NAMES;

  @Override
  protected List<MavenClassSearchResult> searchImpl(Project project, String pattern, int maxResult) {
    DependencySearchService service = MavenProjectIndicesManager.getInstance(project).getSearchService();
    List<MavenDependencyCompletionItemWithClass> items = service.findClasses(pattern, new SearchParameters(1000, 10000, EnumSet
      .of(ALL_VERSIONS)));
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

      MavenClassSearchResult classResult = new MavenClassSearchResult(entry.getValue(), className, packageName);
      results.add(classResult);
    }
    return results;
  }


  @Override
  protected String makeSortKey(MavenClassSearchResult result) {
    return makeKey(result.getClassName(), result.getSearchResults().get(0));
  }

  private String makeKey(String className, MavenDependencyCompletionItem info) {
    return className + " " + super.makeKey(info);
  }
}