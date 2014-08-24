package org.jetbrains.plugins.settingsRepository;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RepositoryAuthenticationForm {
  private static final Pattern HREF_PATTERN = Pattern.compile("<a(?:\\s+href\\s*=\\s*[\"']([^\"']*)[\"'])?\\s*>([^<]*)</a>");

  private JTextField token;
  private SimpleColoredComponent note;
  private JPasswordField password;

  private JPanel panel;

  public RepositoryAuthenticationForm(@Nullable String token, @Nullable String password, @Nullable String note) {
    this.token.setText(token);
    this.password.setText(password);

    if (note == null) {
      this.note.setVisible(false);
    }
    else {
      Matcher matcher = HREF_PATTERN.matcher(note);
      int prev = 0;
      if (matcher.find()) {
        do {
          if (matcher.start() != prev) {
            this.note.append(note.substring(prev, matcher.start()), new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, null));
          }
          this.note.append(matcher.group(2), new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, JBColor.blue), new SimpleColoredComponent.BrowserLauncherTag(matcher.group(1)));
          prev = matcher.end();
        }
        while (matcher.find());

        LinkMouseListenerBase.installSingleTagOn(this.note);
      }

      if (prev < note.length()) {
        this.note.append(note.substring(prev), new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, null));
      }
    }
  }

  @NotNull
  public JPanel getPanel() {
    return panel;
  }

  @Nullable
  public String getUsername() {
    return StringUtil.nullize(token.getText(), true);
  }

  @Nullable
  public char[] getPassword() {
    char[] chars = password.getPassword();
    return chars == null || chars.length == 0 ? null : chars;
  }
}