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
