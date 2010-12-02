package com.intellij.tasks.lighthouse;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dennis.Ushakov
 */
public class LighthouseRepositoryEditor extends BaseRepositoryEditor<LighthouseRepository> {
  private final JTextField myProjectId;
  private final JTextField myAPIKey;

  public LighthouseRepositoryEditor(final Project project,
                                    final LighthouseRepository repository,
                                    Consumer<LighthouseRepository> changeListener) {
    super(project, repository, changeListener);
    myUserNameText.setVisible(false);
    myUsernameLabel.setVisible(false);
    myPasswordText.setVisible(false);
    myPasswordLabel.setVisible(false);

    myCustomLabel.add(new JLabel("Project ID:", SwingConstants.RIGHT), BorderLayout.NORTH);

    myProjectId = new JTextField();
    myProjectId.setText(repository.getProjectId());
    installListener(myProjectId);

    myCustomPanel.add(myProjectId, BorderLayout.NORTH);

    myCustomLabel.add(new JLabel("API Token:", SwingConstants.RIGHT), BorderLayout.SOUTH);

    myAPIKey = new JTextField();
    myAPIKey.setText(repository.getAPIKey());
    installListener(myAPIKey);

    myCustomPanel.add(myAPIKey, BorderLayout.SOUTH);    
  }

    @Override
  public void apply() {
    myRepository.setProjectId(myProjectId.getText().trim());
    myRepository.setAPIKey(myAPIKey.getText().trim());
    super.apply();
  }
}
