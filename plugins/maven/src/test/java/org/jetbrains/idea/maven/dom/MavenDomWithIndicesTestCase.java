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

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.indices.MavenIndicesTestFixture;
import org.jetbrains.idea.maven.onlinecompletion.IndexBasedCompletionProvider;
import org.jetbrains.idea.maven.onlinecompletion.ProjectModulesCompletionProvider;
import org.jetbrains.idea.reposearch.DependencySearchProvider;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

public abstract class MavenDomWithIndicesTestCase extends MavenDomTestCase {
  protected MavenIndicesTestFixture myIndicesFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIndicesFixture = createIndicesFixture();
    myIndicesFixture.setUp();
    List<DependencySearchProvider> indexProviders =
      new ArrayList<>(ContainerUtil.map(myIndicesFixture.getProjectIndicesManager().getIndices(), IndexBasedCompletionProvider::new));
    indexProviders.add(new ProjectModulesCompletionProvider(myProject));
    DependencySearchService.getInstance(myProject).setProviders(indexProviders,
                                                                emptyList()
    );
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

}