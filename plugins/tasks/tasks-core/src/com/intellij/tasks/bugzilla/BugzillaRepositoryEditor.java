package com.intellij.tasks.bugzilla;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class BugzillaRepositoryEditor extends BaseRepositoryEditor<BugzillaRepository> {
  private JBLabel myProductLabel;
  private JBTextField myProductInput;

  private JBLabel myComponentLabel;
  private JBTextField myComponentInput;

  public BugzillaRepositoryEditor(Project project,
                                  BugzillaRepository repository,
                                  Consumer<BugzillaRepository> changeListener) {
    super(project, repository, changeListener);

    myUseHttpAuthenticationCheckBox.setVisible(false);

    myProductInput.setText(myRepository.getProductName());
    myComponentInput.setText(myRepository.getComponentName());

    installListener(myProductInput);
    installListener(myComponentInput);

    myTestButton.setEnabled(myRepository.isConfigured());
  }

  @Override
  public void apply() {
    super.apply();
    myRepository.setProductName(myProductInput.getText());
    myRepository.setComponentName(myComponentInput.getText());

    myTestButton.setEnabled(myRepository.isConfigured());
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myProductLabel = new JBLabel("Product:", SwingConstants.RIGHT);
    myProductInput = new JBTextField();
    myComponentLabel = new JBLabel("Component:", SwingConstants.RIGHT);
    myComponentInput = new JBTextField();
    return FormBuilder.createFormBuilder()
      .addLabeledComponent(myProductLabel, myProductInput)
      .addLabeledComponent(myComponentLabel, myComponentInput)
      .getPanel();
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    super.setAnchor(anchor);
    myProductLabel.setAnchor(anchor);
    myComponentLabel.setAnchor(anchor);
  }
}
