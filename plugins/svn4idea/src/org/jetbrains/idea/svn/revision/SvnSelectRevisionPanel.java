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
package org.jetbrains.idea.svn.revision;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.update.SvnRevisionPanel;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import java.awt.*;

public class SvnSelectRevisionPanel extends JPanel {
  private SvnRevisionPanel mySvnRevisionPanel;
  private JPanel myPanel;

  public SvnSelectRevisionPanel() {
    super(new BorderLayout());
  }

  public void setProject(final Project project) {
    mySvnRevisionPanel.setProject(project);
  }

  public void setUrlProvider(final SvnRevisionPanel.UrlProvider urlProvider) {
    mySvnRevisionPanel.setUrlProvider(urlProvider);
  }

  public void setRoot(final VirtualFile root) {
    mySvnRevisionPanel.setRoot(root);
  }

  @NotNull
  public SVNRevision getRevision() throws ConfigurationException {
    return mySvnRevisionPanel.getRevision();
  }
}
