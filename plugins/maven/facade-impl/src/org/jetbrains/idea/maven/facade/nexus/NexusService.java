/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.facade.nexus;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facade.MavenManagementService;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class NexusService extends MavenManagementService {
  public static MavenRepositoryInfo convertRepositoryInfo(RepositoryType repo) {
    return new MavenRepositoryInfo(repo.getId(), repo.getName(), repo.getContentResourceURI());
  }

  public static MavenArtifactInfo convertArtifactInfo(ArtifactType t) {
    return new MavenArtifactInfo(t.getGroupId(),
                                 t.getArtifactId(),
                                 t.getVersion(),
                                 t.getPackaging(),
                                 t.getClassifier(),
                                 null,
                                 t.getRepoId());
  }

  @Override
  public String getDisplayName() {
    return "Nexus";
  }

  @Nullable
  @Override
  public List<MavenRepositoryInfo> getRepositories(String url) throws Exception {
    List<RepositoryType> repos = new Endpoint.Repositories(url).getRepolistAsRepositories().getData().getRepositoriesItem();
    List<MavenRepositoryInfo> result = new ArrayList<MavenRepositoryInfo>(repos.size());
    for (RepositoryType repo : repos) {
      if (!"maven2".equals(repo.getProvider())) continue;
      result.add(convertRepositoryInfo(repo));
    }
    return result;
  }

  @Override
  public List<MavenArtifactInfo> findArtifacts(MavenArtifactInfo template, String url) throws JAXBException, IOException {
    final String packaging = StringUtil.notNullize(template.getPackaging());
    final ArrayList<MavenArtifactInfo> result = new ArrayList<MavenArtifactInfo>();
    final SearchResults results = new Endpoint.DataIndex(url)
      .getArtifactlistAsSearchResults(null, template.getGroupId(), template.getArtifactId(), template.getVersion(),
                                      template.getClassifier(), template.getClassNames());
    final boolean canTrySwitchGAV = template.getArtifactId() == null && template.getGroupId() != null;
    boolean tooManyResults = results.isTooManyResults();
    final SearchResults.Data data = results.getData();
    if (data != null) {
      for (ArtifactType each : data.getArtifact()) {
        if (!Comparing.equal(each.packaging, packaging)) continue;
        result.add(convertArtifactInfo(each));
      }
    }

    if (canTrySwitchGAV) {
      final SearchResults results2 = new Endpoint.DataIndex(url)
        .getArtifactlistAsSearchResults(null, null, template.getGroupId(), template.getVersion(),
                                        template.getClassifier(), template.getClassNames());
      tooManyResults = tooManyResults || results2.isTooManyResults();
      final SearchResults.Data data2 = results2.getData();
      if (data2 != null) {
        for (ArtifactType each : data2.getArtifact()) {
          if (!Comparing.equal(each.packaging, packaging)) continue;
          result.add(convertArtifactInfo(each));
        }
      }
    }
    if (tooManyResults) result.add(null);
    return result;
  }
}
