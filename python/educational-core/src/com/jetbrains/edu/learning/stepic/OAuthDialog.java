package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DefaultProjectFactoryImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.ui.AuthorizationPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OAuthDialog extends DialogWrapper {
  protected final AuthorizationPanel myLoginPanel;
  private String myProgressTitle;
  private StepicUser myStepicUser;

  public OAuthDialog() {
    super(false);
    myLoginPanel = new AuthorizationPanel();
    myProgressTitle = "Authorizing on Stepik";
    setTitle("Stepik Authorization");
    init();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myLoginPanel.getContentPanel();
  }

  @Override
  protected String getHelpId() {
    return "login_to_stepic";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLoginPanel.getPreferableFocusComponent();
  }

  @Override
  protected void doOKAction() {
    String code = myLoginPanel.getCode();
    if (code == null || code.isEmpty()) return;

    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);

      myStepicUser = StudyUtils.execCancelable(() -> EduStepicAuthorizedClient.login(code, EduStepicNames.EXTERNAL_REDIRECT_URL));
      if (myStepicUser != null) {
        doJustOkAction();
      }
      else {
        setError("Login Failed");
      }
    }, myProgressTitle, true, new DefaultProjectFactoryImpl().getDefaultProject());
  }

  private void setError(@NotNull String errorText) {
    ApplicationManager.getApplication().invokeLater(() -> setErrorText(errorText));
  }

  protected void doJustOkAction() {
    ApplicationManager.getApplication().invokeLater(() -> super.doOKAction());
  }

  public StepicUser getStepicUser() {
    return myStepicUser;
  }
}