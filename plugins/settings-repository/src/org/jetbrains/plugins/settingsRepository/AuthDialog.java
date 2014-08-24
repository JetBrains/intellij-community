package org.jetbrains.plugins.settingsRepository;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.net.AuthenticationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AuthDialog extends DialogWrapper {
  private final AuthenticationPanel authPanel;

  /**
   * If password if prefilled, it is expected to continue remembering it.
   * On the other hand, if password saving is disabled, the checkbox is not shown.
   * In other cases, {@code rememberByDefault} is used.
   */
  public AuthDialog(@NotNull String title, @Nullable String description, @Nullable String login, @Nullable String password) {
    super(false);

    setTitle(title);
    authPanel = new AuthenticationPanel(description, login, password, null);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return authPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return authPanel.getPreferredFocusedComponent();
  }

  public String getUsername() {
    return authPanel.getLogin();
  }

  public String getPassword() {
    return String.valueOf(authPanel.getPassword());
  }
}