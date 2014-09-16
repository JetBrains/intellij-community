package org.jetbrains.settingsRepository;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RepositoryAuthenticationForm extends DialogWrapper {
  private static final Pattern HREF_PATTERN = Pattern.compile("<a(?:\\s+href\\s*=\\s*[\"']([^\"']*)[\"'])?\\s*>([^<]*)</a>");

  private static final SimpleTextAttributes LINK_TEXT_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, JBColor.blue);
  private static final SimpleTextAttributes SMALL_TEXT_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, null);

  private JTextField tokenField;
  private SimpleColoredComponent noteComponent;
  private JPasswordField passwordField;

  private JPanel panel;
  private JLabel tokenLabel;
  private JLabel messageLabel;

  private final JComponent initialFocusedComponent;

  public RepositoryAuthenticationForm(@NotNull String message, @Nullable String token, @Nullable String password, @Nullable String note, boolean onlyPassword) {
    super(false);

    setTitle("Settings Repository");
    setResizable(false);

    messageLabel.setText(message);
    messageLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

    if (onlyPassword) {
      tokenLabel.setVisible(false);
      tokenField.setVisible(false);

      passwordField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          setOKActionEnabled(e.getDocument().getLength() != 0);
        }
      });
      initialFocusedComponent = passwordField;
      setOKActionEnabled(false);
    }
    else {
      tokenField.setText(token);
      passwordField.setText(password);
      initialFocusedComponent = StringUtil.isEmpty(token) ? tokenField : passwordField;
    }

    if (note == null) {
      noteComponent.setVisible(false);
    }
    else {
      Matcher matcher = HREF_PATTERN.matcher(note);
      int prev = 0;
      if (matcher.find()) {
        do {
          if (matcher.start() != prev) {
            noteComponent.append(note.substring(prev, matcher.start()), SMALL_TEXT_ATTRIBUTES);
          }
          noteComponent.append(matcher.group(2), LINK_TEXT_ATTRIBUTES, new SimpleColoredComponent.BrowserLauncherTag(matcher.group(1)));
          prev = matcher.end();
        }
        while (matcher.find());

        LinkMouseListenerBase.installSingleTagOn(noteComponent);
      }

      if (prev < note.length()) {
        noteComponent.append(note.substring(prev), SMALL_TEXT_ATTRIBUTES);
      }
    }

    init();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return initialFocusedComponent;
  }

  @Override
  protected JComponent createCenterPanel() {
    return panel;
  }

  @Nullable
  public String getUsername() {
    return StringUtil.nullize(tokenField.getText(), true);
  }

  @Nullable
  public char[] getPassword() {
    char[] chars = passwordField.getPassword();
    return chars == null || chars.length == 0 ? null : chars;
  }
}
