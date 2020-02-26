// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.indices.MavenSearchIndex;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.reposearch.DependencySearchProvider;
import org.jetbrains.idea.reposearch.RepositoryArtifactData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;


/**
 * This class is used as a solution to support completion from repositories, which do not support online completion
 */
public class IndexBasedCompletionProvider implements DependencySearchProvider {

  private final MavenIndex myIndex;
  private final MavenDependencyCompletionItem.Type resultingType;

  public IndexBasedCompletionProvider(@NotNull MavenIndex index) {
    myIndex = index;
    resultingType = myIndex.getKind() == MavenSearchIndex.Kind.LOCAL
                    ? MavenDependencyCompletionItem.Type.LOCAL
                    : MavenDependencyCompletionItem.Type.REMOTE;
  }


  @Override
  public void fulltextSearch(@NotNull String searchString, @NotNull Consumer<RepositoryArtifactData> consumer) {
    MavenId mavenId = new MavenId(searchString);
    search(consumer, mavenId);
  }

  @Override
  public void suggestPrefix(@Nullable String groupId, @Nullable String artifactId, @NotNull Consumer<RepositoryArtifactData> consumer) {
    search(consumer, new MavenId(groupId, artifactId, null));
  }

  private void search(@NotNull Consumer<RepositoryArtifactData> consumer, MavenId mavenId) {
    for (String groupId : myIndex.getGroupIds()) {
      if (mavenId.getGroupId() != null && !mavenId.getGroupId().isEmpty() && !nonExactMatches(groupId, mavenId.getGroupId())) {
        continue;
      }
      for (String artifactId : myIndex.getArtifactIds(groupId)) {
        if (mavenId.getArtifactId() != null &&
            !mavenId.getArtifactId().isEmpty() &&
            !nonExactMatches(artifactId, mavenId.getArtifactId())) {
          continue;
        }
        MavenRepositoryArtifactInfo info = new MavenRepositoryArtifactInfo(groupId, artifactId, myIndex.getVersions(groupId, artifactId));
        consumer.accept(info);
      }
    }
  }

  private boolean nonExactMatches(String template, String real) {
    List<CharSequence> splittedTemplate = split(template);
    List<CharSequence> splittedReal = split(real);
    if (splittedTemplate.size() == 1 || splittedReal.size() == 1) {
      return StringUtil.startsWith(template, real) || StringUtil.startsWith(real, template);
    }
    int matches = 0;
    for (int i = 0; i < Math.min(splittedReal.size(), splittedTemplate.size()); i++) {
      if (StringUtil.startsWith(splittedTemplate.get(i), splittedReal.get(i)) ||
          StringUtil.startsWith(splittedReal.get(i), splittedTemplate.get(i))) {
        matches += 1;
      }
      if (matches >= 2) return true;
    }
    return false;
  }


  private static List<CharSequence> split(@NotNull String s) {
    int indexDot = s.indexOf('.');
    int indexHyph = s.indexOf('-');
    int index = getNextIndex(indexDot, indexHyph);
    if (index < 0) {
      return Collections.singletonList(s);
    }
    List<CharSequence> result = new ArrayList<>();
    int pos = 0;
    while (true) {
      indexDot = StringUtil.indexOf(s, '.', pos);
      indexHyph = StringUtil.indexOf(s, '-', pos);
      index = getNextIndex(indexDot, indexHyph);
      if (index == -1) break;
      final int nextPos = index + 1;
      CharSequence token = s.subSequence(pos, index);
      if (token.length() != 0) {
        result.add(token);
      }
      pos = nextPos;
    }
    if (pos < s.length()) {
      result.add(s.subSequence(pos, s.length()));
    }
    return result;
  }

  private static int getNextIndex(int index1, int index2) {
    if (index1 > 0 && index2 > 0) return Math.min(index1, index2);
    return Math.max(index1, index2);
  }

  @Override
  public boolean isLocal() {
    return true;
  }

  public MavenSearchIndex getIndex() {
    return myIndex;
  }
}
