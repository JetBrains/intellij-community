package com.intellij.tasks.github;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dennis.Ushakov
 */
public class GitHubRepositoryEditor extends BaseRepositoryEditor<GitHubRepository> {
  private final JTextField myRepoName;
  private final JTextField myRepoAuthor;

  public GitHubRepositoryEditor(final Project project,
                                final GitHubRepository repository,
                                Consumer<GitHubRepository> changeListener) {
    super(project, repository, changeListener);

    myUrlLabel.setVisible(false);
    myURLText.setVisible(false);

    // project author, by default same as username
    myRepoAuthor = new JTextField();
    myRepoAuthor.setText(repository.getRepoAuthor());
    installListener(myRepoAuthor);
    myCustomPanel.add(myRepoAuthor, BorderLayout.NORTH);
    myCustomLabel.add(new JLabel("Repository author:", SwingConstants.RIGHT) {
      @Override
      public Dimension getPreferredSize() {
        final Dimension oldSize = super.getPreferredSize();
        final Dimension size = myRepoAuthor.getPreferredSize();
        return new Dimension(oldSize.width, size.height);
      }
    }, BorderLayout.NORTH);

    // project id
    myRepoName = new JTextField();
    myRepoName.setText(repository.getRepoName());
    installListener(myRepoName);
    myCustomPanel.add(myRepoName, BorderLayout.CENTER);
    myCustomLabel.add(new JLabel("Repository:", SwingConstants.RIGHT) {
      @Override
      public Dimension getPreferredSize() {
        final Dimension oldSize = super.getPreferredSize();
        final Dimension size = myRepoName.getPreferredSize();
        return new Dimension(oldSize.width, size.height);
      }
    }, BorderLayout.CENTER);
  }

  @Override
  public void apply() {
    myRepository.setRepoName(myRepoName.getText().trim());
    myRepository.setRepoAuthor(myRepoAuthor.getText().trim());
    myUseHTTPAuthentication.setSelected(!StringUtil.isEmpty(myUserNameText.getText()));
    super.apply();
  }
}
