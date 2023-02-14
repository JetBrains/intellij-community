/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project.importing;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

public abstract class MavenProjectsTreeTestCase extends MavenMultiVersionImportingTestCase {
  protected MavenProjectsTree myTree;


  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    myTree = new MavenProjectsTree(myProject);
  }

  protected void updateAll(VirtualFile... files) {
    updateAll(Collections.emptyList(), files);
  }

  protected void updateAll(List<String> profiles, VirtualFile... files) {
    myTree.resetManagedFilesAndProfiles(asList(files), new MavenExplicitProfiles(profiles));
    myTree.updateAll(false, getMavenGeneralSettings(), getMavenProgressIndicator());
  }

  protected void update(VirtualFile file) {
    myTree.update(asList(file), false, getMavenGeneralSettings(), getMavenProgressIndicator());
  }

  protected void deleteProject(VirtualFile file) {
    myTree.delete(asList(file), getMavenGeneralSettings(), getMavenProgressIndicator());
  }

  protected void updateTimestamps(final VirtualFile... files) throws IOException {
    WriteAction.runAndWait(() -> {
      for (VirtualFile each : files) {
        each.setBinaryContent(each.contentsToByteArray());
      }
    });
  }
}
