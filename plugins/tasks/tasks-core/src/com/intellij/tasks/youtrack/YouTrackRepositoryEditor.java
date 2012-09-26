package com.intellij.tasks.youtrack;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class YouTrackRepositoryEditor extends BaseRepositoryEditor<YouTrackRepository> {
  private JTextField myDefaultSearch;
  private JBLabel mySearchLabel;

  public YouTrackRepositoryEditor(final Project project, final YouTrackRepository repository, Consumer<YouTrackRepository> changeListener) {
    super(project, repository, changeListener);
    myDefaultSearch.setText(repository.getDefaultSearch());
  }

  @Override
  public void apply() {
    myRepository.setDefaultSearch(myDefaultSearch.getText());
    super.apply();
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    mySearchLabel = new JBLabel("Search:", SwingConstants.RIGHT);
    myDefaultSearch = new JTextField();
    installListener(myDefaultSearch);
    return FormBuilder.createFormBuilder().addLabeledComponent(mySearchLabel, myDefaultSearch).getPanel();
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    mySearchLabel.setAnchor(anchor);
  }
}
