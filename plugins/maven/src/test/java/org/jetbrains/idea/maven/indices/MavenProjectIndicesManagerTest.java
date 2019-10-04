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
package org.jetbrains.idea.maven.indices;

import org.jetbrains.idea.maven.onlinecompletion.OfflineSearchService;
import org.jetbrains.idea.maven.onlinecompletion.IndexBasedCompletionProvider;
import org.jetbrains.idea.maven.onlinecompletion.ProjectModulesCompletionProvider;

public class MavenProjectIndicesManagerTest extends MavenIndicesTestCase {
  private MavenIndicesTestFixture myIndicesFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIndicesFixture = new MavenIndicesTestFixture(myDir.toPath(), myProject);
    myIndicesFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIndicesFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testAutomaticallyAddSearchService() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    OfflineSearchService service = myIndicesFixture.getProjectIndicesManager().getOfflineSearchService();
    assertEquals(2, service.getProviders().size());

    assertTrue(service.getProviders().stream().anyMatch(it -> it instanceof IndexBasedCompletionProvider && ((IndexBasedCompletionProvider)it).getIndex().getKind() == MavenSearchIndex.Kind.LOCAL));
    assertTrue(service.getProviders().stream().anyMatch(it -> it instanceof ProjectModulesCompletionProvider));
  }
}
