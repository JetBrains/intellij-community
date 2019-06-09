// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.indices.MavenSearchIndex;
import org.jetbrains.idea.maven.model.MavenCoordinate;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItemWithClass;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.IOException;
import java.util.Collections;
import java.util.List;


/**
 * This class is used as a solution to support completion from repositories, which do not support online completion
 */
public class IndexBasedCompletionProvider implements DependencyCompletionProvider {

  private final MavenIndex myIndex;
  private final MavenDependencyCompletionItem.Type resultingType;

  public IndexBasedCompletionProvider(@NotNull MavenIndex index) {
    myIndex = index;
    resultingType = myIndex.getKind() == MavenSearchIndex.Kind.LOCAL
                    ? MavenDependencyCompletionItem.Type.LOCAL
                    : MavenDependencyCompletionItem.Type.REMOTE;
  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItem> findGroupCandidates(MavenCoordinate template, SearchParameters parameters) {
    return ContainerUtil.map(myIndex.getGroupIds(), g -> new MavenDependencyCompletionItem(g, resultingType));
  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItem> findArtifactCandidates(MavenCoordinate template, SearchParameters parameters)
    throws IOException {
    return ContainerUtil.map(myIndex.getArtifactIds(template.getGroupId()), a ->
      new MavenDependencyCompletionItem(template.getGroupId(), a, null, resultingType));
  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItem> findAllVersions(MavenCoordinate template, SearchParameters parameters) {
    return ContainerUtil.map(myIndex.getVersions(template.getGroupId(), template.getArtifactId()), v ->
      new MavenDependencyCompletionItem(template.getGroupId(), template.getArtifactId(), v, resultingType));
  }

  @NotNull
  @Override
  public List<MavenDependencyCompletionItemWithClass> findClassesByString(@NotNull String str, SearchParameters parameters) {
    if (StringUtil.isEmpty(str)) {
      return Collections.emptyList();
    }
    Query searchQuery;
    try {
      searchQuery = createSearchQuery(str);
    }
    catch (ParseException e) {
      MavenLog.LOG.debug(e);
      return Collections.emptyList();
    }
    return ContainerUtil.map(myIndex.search(searchQuery, parameters.getMaxResults()),
                             r -> new MavenDependencyCompletionItemWithClass(r.getGroupId(), r.getArtifactId(), r.getVersion(),
                                                                             resultingType,
                                                                             Collections.singletonList(r.getClassNames())));
  }

  private static Query createSearchQuery(@NotNull String str) throws ParseException {
    String[] patterns = str.split("\\.");
    StringBuilder builder = new StringBuilder();
    for (String pattern : patterns) {
      builder.append("c:").append(pattern).append(" OR ");
    }

    builder.append("fc:").append(str);
    return new QueryParser("c", new StandardAnalyzer()).parse(builder.toString());
  }

  public MavenSearchIndex getIndex() {
    return myIndex;
  }
}
