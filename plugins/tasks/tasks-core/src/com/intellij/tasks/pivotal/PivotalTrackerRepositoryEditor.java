package com.intellij.tasks.pivotal;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dennis.Ushakov
 */
public class PivotalTrackerRepositoryEditor extends BaseRepositoryEditor<PivotalTrackerRepository> {
  private final JTextField myProjectId;
  private final JTextField myAPIKey;
  private final JCheckBox myShouldFormatCommit;
  private final JTextField myCommitMessageFormat;

  public PivotalTrackerRepositoryEditor(final Project project,
                                    final PivotalTrackerRepository repository,
                                    Consumer<PivotalTrackerRepository> changeListener) {
    super(project, repository, changeListener);

    // project id
    myProjectId = new JTextField();
    myProjectId.setText(repository.getProjectId());
    installListener(myProjectId);
    myCustomPanel.add(myProjectId, BorderLayout.NORTH);
    myCustomLabel.add(new JLabel("Project ID:", SwingConstants.RIGHT) {
      @Override
      public Dimension getPreferredSize() {
        final Dimension oldSize = super.getPreferredSize();
        final Dimension size = myProjectId.getPreferredSize();
        return new Dimension(oldSize.width, size.height);
      }
    }, BorderLayout.NORTH);

    // api key
    myAPIKey = new JTextField();
    myAPIKey.setText(repository.getAPIKey());
    installListener(myAPIKey);
    myCustomPanel.add(myAPIKey, BorderLayout.CENTER);
    myCustomLabel.add(new JLabel("API Token:", SwingConstants.RIGHT) {
      @Override
      public Dimension getPreferredSize() {
        final Dimension oldSize = super.getPreferredSize();
        final Dimension size = myAPIKey.getPreferredSize();
        return new Dimension(oldSize.width, size.height);
      }
    }, BorderLayout.CENTER);

    myCommitMessageFormat = new JTextField();
    myCommitMessageFormat.setText(repository.getCommitMessageFormat());
    myCommitMessageFormat.setEnabled(repository.isShouldFormatCommitMessage());
    installListener(myCommitMessageFormat);
    myCustomPanel.add(myCommitMessageFormat, BorderLayout.SOUTH);

    myShouldFormatCommit = new JCheckBox("Add commit message:");
    myShouldFormatCommit.setSelected(repository.isShouldFormatCommitMessage());
    installListener(myShouldFormatCommit);
    myShouldFormatCommit.setHorizontalAlignment(SwingConstants.RIGHT);
    myShouldFormatCommit.setBorder(BorderFactory.createLineBorder(Color.RED)); 
    myCustomLabel.add(myShouldFormatCommit, BorderLayout.SOUTH);
  }

  @Override
  public void apply() {
    myCommitMessageFormat.setEnabled(myShouldFormatCommit.isSelected());
    myRepository.setProjectId(myProjectId.getText().trim());
    myRepository.setAPIKey(myAPIKey.getText().trim());
    myRepository.setShouldFormatCommitMessage(myShouldFormatCommit.isSelected());
    myRepository.setCommitMessageFormat(myCommitMessageFormat.getText());
    myUseHTTPAuthentication.setSelected(!StringUtil.isEmpty(myPasswordText.getText()));
    super.apply();
  }
}
