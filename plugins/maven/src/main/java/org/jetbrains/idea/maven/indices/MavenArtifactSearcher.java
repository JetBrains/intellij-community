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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.*;

public class MavenArtifactSearcher extends MavenSearcher<MavenArtifactSearchResult> {

  private static final Pattern VERSION_PATTERN = Pattern.compile("[.\\d]+");

  @Override
  protected List<MavenArtifactSearchResult> searchImpl(Project project, String pattern, int maxResult) {
    List<String> parts = new ArrayList<>();
    for (String each : tokenize(pattern, " :")) {
      parts.add(trimStart(trimEnd(each, "*"), "*"));
    }

    List<MavenArtifactSearchResult> searchResults = ContainerUtil.newSmartList();
    MavenProjectIndicesManager m = MavenProjectIndicesManager.getInstance(project);
    int count = 0;
    List<MavenArtifactInfo> versions = new ArrayList<>();
    for (String groupId : m.getGroupIds()) {
      if (count >= maxResult) break;
      if (parts.size() < 1 || contains(groupId, parts.get(0))) {
        for (String artifactId : m.getArtifactIds(groupId)) {
          if (parts.size() < 2 || contains(artifactId, parts.get(1))) {
            for (String version : m.getVersions(groupId, artifactId)) {
              if (parts.size() < 3 || contains(version, parts.get(2))) {
                versions.add(new MavenArtifactInfo(groupId, artifactId, version, "jar", null));
                if (++count >= maxResult) break;
              }
            }
          }
          else if (parts.size() == 2 && VERSION_PATTERN.matcher(parts.get(1)).matches()) {
            for (String version : m.getVersions(groupId, artifactId)) {
              if (contains(version, parts.get(1))) {
                versions.add(new MavenArtifactInfo(groupId, artifactId, version, "jar", null));
                if (++count >= maxResult) break;
              }
            }
          }

          if (!versions.isEmpty()) {
            MavenArtifactSearchResult searchResult = new MavenArtifactSearchResult();
            searchResult.versions.addAll(versions);
            searchResults.add(searchResult);
            versions.clear();
          }

          if (count >= maxResult) break;
        }
      }
    }
    return searchResults;
  }
}
