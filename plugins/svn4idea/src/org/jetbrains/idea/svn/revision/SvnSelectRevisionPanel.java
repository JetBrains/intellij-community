package org.jetbrains.idea.svn.revision;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.update.SvnRevisionPanel;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class SvnSelectRevisionPanel extends JPanel {
  private JCheckBox myRevisionBox;
  private SvnRevisionPanel mySvnRevisionPanel;
  private JPanel myPanel;

  public SvnSelectRevisionPanel() {
    super(new BorderLayout());

    myRevisionBox.setSelected(false);
    mySvnRevisionPanel.setEnabled(false);

    myRevisionBox.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
          mySvnRevisionPanel.setEnabled(myRevisionBox.isSelected());
      }
    });
  }

  public void setProject(final Project project) {
    mySvnRevisionPanel.setProject(project);
  }

  public void setUrlProvider(final SvnRevisionPanel.UrlProvider urlProvider) {
    mySvnRevisionPanel.setUrlProvider(urlProvider);
  }

  @NotNull
  public SVNRevision getRevision() throws ConfigurationException {
    if (myRevisionBox.isSelected()) {
      return mySvnRevisionPanel.getRevision();
    }
    return SVNRevision.HEAD;
  }
}
