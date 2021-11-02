// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.maven.server.MavenServerIndexer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public final class MavenClassSearcher extends MavenSearcher<MavenClassSearchResult> {
  public static final String TERM = MavenServerIndexer.SEARCH_TERM_CLASS_NAMES;

  @Override
  protected List<MavenClassSearchResult> searchImpl(Project project, String pattern, int maxResult) {
    String patternForQuery = preparePattern(pattern);

    MavenIndicesManager m = MavenIndicesManager.getInstance(project);
    Set<MavenArtifactInfo> infos = m.getIndex()
      .getIndices().stream()
      .flatMap(i -> i.search(patternForQuery, 50).stream())
      .collect(Collectors.toSet());

    ArrayList<MavenClassSearchResult> results = new ArrayList<>(processResults(infos, patternForQuery, maxResult));
    results.sort(Comparator.comparing(MavenClassSearchResult::getClassName));
    return results;
  }

  private static String preparePattern(String pattern) {
    pattern = pattern.toLowerCase();
    if (pattern.trim().length() == 0) {
      return StringUtils.EMPTY;
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

  private static Collection<MavenClassSearchResult> processResults(Set<MavenArtifactInfo> infos, String pattern, int maxResult) {
    if (pattern.length() == 0 || pattern.equals("*")) {
      pattern = "^/(.*)$";
    }
    else {
      pattern = pattern.replace(".", "/");

      int lastDot = pattern.lastIndexOf("/");
      String packagePattern = lastDot == -1 ? "" : (pattern.substring(0, lastDot) + "/");
      String classNamePattern = lastDot == -1 ? pattern : pattern.substring(lastDot + 1);

      packagePattern = packagePattern.replaceAll("\\*", ".*?");
      classNamePattern = classNamePattern.replaceAll("\\*", "[^/]*?");

      pattern = packagePattern + classNamePattern;

      pattern = ".*?/" + pattern;
      pattern = "^(" + pattern + ")$";
    }
    Pattern p;
    try {
      p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    }
    catch (PatternSyntaxException e) {
      return Collections.emptyList();
    }

    Map<String, MavenClassSearchResult> result = new HashMap<>();
    for (MavenArtifactInfo each : infos) {
      if (each.getClassNames() == null) continue;

      Matcher matcher = p.matcher(each.getClassNames());
      while (matcher.find()) {
        String classFQName = matcher.group(1);
        classFQName = classFQName.replace("/", ".");
        classFQName = StringUtil.trimStart(classFQName, ".");

        String key = classFQName;

        MavenClassSearchResult classResult = result.get(key);
        if (classResult == null) {

          int pos = classFQName.lastIndexOf(".");
          MavenRepositoryArtifactInfo artifactInfo = new MavenRepositoryArtifactInfo(
            each.getGroupId(), each.getArtifactId(),
            Collections.singletonList(each.getVersion()));
          if (pos == -1) {
            result.put(key, new MavenClassSearchResult(artifactInfo, classFQName, "default package"));
          }
          else {
            result.put(key, new MavenClassSearchResult(artifactInfo, classFQName.substring(pos + 1), classFQName.substring(0, pos)));
          }
        }
        else {
          List<String> versions = ContainerUtil.map(classResult.getSearchResults().getItems(), i -> i.getVersion());
          versions.add(each.getVersion());
          MavenRepositoryArtifactInfo artifactInfo = new MavenRepositoryArtifactInfo(
            each.getGroupId(), each.getArtifactId(),
            versions);
          result.put(key, new MavenClassSearchResult(artifactInfo, classResult.getClassName(), classResult.getPackageName()));
        }


        if (result.size() > maxResult) break;
      }
    }

    result.values().forEach(a ->
                              Arrays.sort(a.getSearchResults().getItems(),
                                          Comparator.comparing(MavenDependencyCompletionItem::getVersion, VersionComparatorUtil.COMPARATOR)
                                            .reversed())
    );
    return result.values();
  }
}