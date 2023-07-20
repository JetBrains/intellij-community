/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom;

import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture;
import org.jetbrains.idea.maven.onlinecompletion.MavenCompletionProviderFactory;
import org.jetbrains.idea.maven.server.MavenServerConnector;
import org.jetbrains.idea.maven.server.MavenServerDownloadListener;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class MavenDomWithIndicesTestCase extends MavenDomTestCase {
  protected MavenIndicesTestFixture myIndicesFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionTestUtil.maskExtensions(DependencySearchService.EP_NAME,
                                     Collections.singletonList(new MavenCompletionProviderFactory()),
                                     getTestRootDisposable(), false, null);
    myIndicesFixture = createIndicesFixture();
    myIndicesFixture.setUp();
  }

  protected MavenIndicesTestFixture createIndicesFixture() {
    return new MavenIndicesTestFixture(myDir.toPath(), myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myIndicesFixture != null) {
        myIndicesFixture.tearDown();
        myIndicesFixture = null;
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void runAndExpectPluginIndexEvents(Set<String> expectedArtifactIds, Runnable action) {
    var latch = new CountDownLatch(1);
    Set<String> artifactIdsToIndex = ConcurrentHashMap.newKeySet();
    artifactIdsToIndex.addAll(expectedArtifactIds);

    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
      .subscribe(MavenIndicesManager.INDEXER_TOPIC, new MavenIndicesManager.MavenIndexerListener() {
        @Override
        public void indexUpdated(Set<File> added, Set<File> failedToAdd) {
          artifactIdsToIndex.removeIf(artifactId -> ContainerUtil.exists(added, file -> file.getPath().contains(artifactId)));
          if (artifactIdsToIndex.isEmpty()) {
            latch.countDown();
          }
        }
      });

    action.run();

    try {
      latch.await(1, TimeUnit.MINUTES);
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    assertTrue("Maven plugins are not indexed in time: " + String.join(", ", artifactIdsToIndex), artifactIdsToIndex.isEmpty());
  }

  protected void runAndExpectArtifactDownloadEvents(String groupId, Set<String> artifactIds, Runnable action) {
    var groupFolder = groupId.replace('.', '/');
    var latch = new CountDownLatch(1);
    Set<String> actualEvents = ConcurrentHashMap.newKeySet();
    var downloadListener = new MavenServerDownloadListener() {
      @Override
      public void artifactDownloaded(File file, String relativePath) {
        if (relativePath.startsWith(groupFolder)) {
          var artifactId = relativePath.substring(groupFolder.length()).split("/")[1];
          if (artifactIds.contains(artifactId)) {
            actualEvents.add(artifactId);
            if (actualEvents.size() == artifactIds.size()) {
              latch.countDown();
            }
          }
        }
      }
    };

    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
      .subscribe(MavenServerConnector.DOWNLOAD_LISTENER_TOPIC, downloadListener);

    action.run();

    try {
      latch.await(1, TimeUnit.MINUTES);
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    assertUnorderedElementsAreEqual(artifactIds, actualEvents);
  }

}