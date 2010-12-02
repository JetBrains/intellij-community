package com.intellij.tasks.youtrack;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.util.Consumer;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class YouTrackRepositoryEditor extends BaseRepositoryEditor<YouTrackRepository> {

  private final JTextField myDefaultSearch;

  public YouTrackRepositoryEditor(final Project project, final YouTrackRepository repository, Consumer<YouTrackRepository> changeListener) {
    super(project, repository, changeListener);

    myCustomLabel.add(new JLabel("Search:", SwingConstants.RIGHT));

    myDefaultSearch = new JTextField();
    myDefaultSearch.setText(repository.getDefaultSearch());
    installListener(myDefaultSearch);

    myCustomPanel.add(myDefaultSearch);
  }

  @Override
  public void apply() {

    myRepository.setDefaultSearch(myDefaultSearch.getText());
    super.apply();

  }
}
