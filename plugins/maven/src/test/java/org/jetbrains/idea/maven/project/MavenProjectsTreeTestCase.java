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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;

import java.io.IOException;
import static java.util.Arrays.asList;
import java.util.Collections;
import java.util.List;

public abstract class MavenProjectsTreeTestCase extends MavenImportingTestCase {
  protected MavenProjectsTree myTree = new MavenProjectsTree();

  protected void updateAll(VirtualFile... files) throws MavenProcessCanceledException, MavenException {
    updateAll(Collections.<String>emptyList(), files);
  }

  protected void updateAll(List<String> profiles, VirtualFile... files) throws MavenProcessCanceledException, MavenException {
    myTree.resetManagedFilesAndProfiles(asList(files), profiles);
    myTree.updateAll(false, getMavenGeneralSettings(), EMPTY_MAVEN_PROCESS, null);
  }

  protected void update(VirtualFile file) throws MavenProcessCanceledException {
    myTree.update(asList(file), false, getMavenGeneralSettings(), EMPTY_MAVEN_PROCESS, null);
  }

  protected void deleteProject(VirtualFile file) throws MavenProcessCanceledException {
    myTree.delete(asList(file), getMavenGeneralSettings(), EMPTY_MAVEN_PROCESS, null);
  }

  protected void updateTimestamps(VirtualFile... files) throws IOException {
    for (VirtualFile each : files) {
      each.setBinaryContent(each.contentsToByteArray());
    }
  }
}