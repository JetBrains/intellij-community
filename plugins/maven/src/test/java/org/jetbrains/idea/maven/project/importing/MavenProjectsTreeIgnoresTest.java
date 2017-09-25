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
package org.jetbrains.idea.maven.project.importing;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.util.Collections;
import java.util.List;

public class MavenProjectsTreeIgnoresTest extends MavenProjectsTreeTestCase {
  private String myLog = "";
  private List<MavenProject> myRoots;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    myTree.addListener(new MyLoggingListener());
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");
    updateAll(m1, m2);
    myRoots = myTree.getRootProjects();
  }

  public void testSendingNotifications() {
    myTree.setIgnoredState(Collections.singletonList(myRoots.get(0)), true);

    assertEquals("ignored: m1 ", myLog);
    myLog = "";

    myTree.setIgnoredFilesPaths(Collections.singletonList(myRoots.get(1).getPath()));

    assertEquals("ignored: m2 unignored: m1 ", myLog);
    myLog = "";

    myTree.setIgnoredFilesPatterns(Collections.singletonList("*"));

    assertEquals("ignored: m1 ", myLog);
    myLog = "";

    myTree.setIgnoredFilesPatterns(Collections.emptyList());

    assertEquals("unignored: m1 ", myLog);
    myLog = "";
  }

  public void testDoNotSendNotificationsIfNothingChanged() {
    myTree.setIgnoredState(Collections.singletonList(myRoots.get(0)), true);

    assertEquals("ignored: m1 ", myLog);
    myLog = "";

    myTree.setIgnoredState(Collections.singletonList(myRoots.get(0)), true);

    assertEquals("", myLog);
  }

  private class MyLoggingListener implements MavenProjectsTree.Listener {
    @Override
    public void projectsIgnoredStateChanged(List<MavenProject> ignored, List<MavenProject> unignored, boolean fromImport) {
      if (!ignored.isEmpty()) myLog += "ignored: " + format(ignored) + " ";
      if (!unignored.isEmpty()) myLog += "unignored: " + format(unignored) + " ";
      if (ignored.isEmpty() && unignored.isEmpty()) myLog += "empty ";
    }

    private String format(List<MavenProject> projects) {
      return StringUtil.join(projects, project -> project.getMavenId().getArtifactId(), ", ");
    }
  }
}
