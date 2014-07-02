package com.intellij.tasks.bugzilla;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class BugzillaRepositoryEditor extends BaseRepositoryEditor<BugzillaRepository> {
  private static final Logger LOG = Logger.getInstance(BugzillaRepository.class);

  private JBLabel myProductLabel;
  private TextFieldWithAutoCompletion<String> myProductInput;

  private JBLabel myComponentLabel;
  private TextFieldWithAutoCompletion<String> myComponentInput;

  public BugzillaRepositoryEditor(Project project,
                                  BugzillaRepository repository,
                                  Consumer<BugzillaRepository> changeListener) {
    super(project, repository, changeListener);

    myUseHttpAuthenticationCheckBox.setVisible(false);

    installListener(myProductInput);
    installListener(myComponentInput);

    myTestButton.setEnabled(myRepository.isConfigured());

    if (myRepository.isConfigured()) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          installProductAndComponentCompletion();
        }
      });
    }
  }

  @Override
  protected void afterTestConnection(boolean connectionSuccessful) {
    super.afterTestConnection(connectionSuccessful);
    if (connectionSuccessful) {
      installProductAndComponentCompletion();
    }
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
    myProductInput = TextFieldWithAutoCompletion.create(myProject, Collections.<String>emptyList(), true,
                                                        myRepository.getProductName());
    myComponentLabel = new JBLabel("Component:", SwingConstants.RIGHT);
    myComponentInput = TextFieldWithAutoCompletion.create(myProject, Collections.<String>emptyList(), false,
                                                          myRepository.getComponentName());
    return FormBuilder.createFormBuilder()
      .addLabeledComponent(myProductLabel, myProductInput)
      .addLabeledComponent(myComponentLabel, myComponentInput)
      .getPanel();
  }

  private void installProductAndComponentCompletion() {
    try {
      Pair<List<String>, List<String>> pair = myRepository.fetchProductAndComponentNames();
      myProductInput.setVariants(pair.getFirst());
      myComponentInput.setVariants(pair.getSecond());
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    super.setAnchor(anchor);
    myProductLabel.setAnchor(anchor);
    myComponentLabel.setAnchor(anchor);
  }
}
