// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.jarRepository.services.bintray.BintrayEndpoint;
import com.intellij.jarRepository.services.bintray.BintrayModel;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.IndexedMavenId;
import org.jetbrains.idea.maven.server.MavenIndicesProcessor;
import org.jetbrains.idea.maven.server.MavenServerIndexerException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.jarRepository.services.bintray.BintrayEndpoint.BINTRAY_API_URL;
import static com.intellij.openapi.util.text.StringUtil.split;

/**
 * @author ibessonov
 */
class BintrayIndexer implements NotNexusIndexer {

  private final String myUrlTemplate;

  public BintrayIndexer(@NotNull String subject, @NotNull String repo) {
    myUrlTemplate = BINTRAY_API_URL + "search/packages/maven?q=*&subject=" + subject + "&repo=" + repo;
  }

  @Override
  public void processArtifacts(MavenProgressIndicator progress, MavenIndicesProcessor processor)
      throws IOException, MavenServerIndexerException {
    try {
      progress.pushState();
      progress.setIndeterminate(false);

      new BintrayEndpoint().executeRequest(myUrlTemplate, BintrayModel.Package[].class, packages -> {
        if (progress.isCanceled()) {
          throw new ProcessCanceledException();
        }
        List<IndexedMavenId> mavenIds = new ArrayList<>();
        for (BintrayModel.Package p : packages) {
          for (String groupAndArtifactId : p.system_ids) {
            List<String> list = split(groupAndArtifactId, ":");
            if (list.size() != 2) continue;

            String groupId = list.get(0);
            String artifactId = list.get(1);
            for (String version : p.versions) {
              mavenIds.add(new IndexedMavenId(groupId, artifactId, version, null, p.desc));
            }
          }
        }
        synchronized (processor) {
          processor.processArtifacts(mavenIds);
        }
      }, throwable -> {
        if (throwable instanceof ProcessCanceledException) {
          return;
        }
        if (throwable instanceof IOException) {
          throw (IOException)throwable;
        }
        if (throwable instanceof MavenServerIndexerException) {
          throw (MavenServerIndexerException)throwable;
        }
        throw new MavenServerIndexerException(throwable);
      }, progress::setFraction);
    }
    finally {
      progress.popState();
    }
  }
}
