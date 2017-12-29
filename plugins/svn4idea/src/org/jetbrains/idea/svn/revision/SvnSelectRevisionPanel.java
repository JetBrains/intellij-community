// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.revision;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.update.SvnRevisionPanel;

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

  public void setUrlProvider(@Nullable ThrowableComputable<Url, SvnBindException> urlProvider) {
    mySvnRevisionPanel.setUrlProvider(urlProvider);
  }

  public void setRoot(final VirtualFile root) {
    mySvnRevisionPanel.setRoot(root);
  }

  @NotNull
  public Revision getRevision() throws ConfigurationException {
    return mySvnRevisionPanel.getRevision();
  }
}
