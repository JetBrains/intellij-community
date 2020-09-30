// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

public final class MarkdownCssSettings {
  public static final MarkdownCssSettings DEFAULT = new MarkdownCssSettings();

  @SuppressWarnings("FieldMayBeFinal")
  @Attribute("UriEnabled")
  private boolean myCustomStylesheetEnabled;

  @SuppressWarnings("FieldMayBeFinal")
  @Attribute("StylesheetUri")
  @NotNull
  private String myCustomStylesheetPath;

  @SuppressWarnings("FieldMayBeFinal")
  @Attribute("TextEnabled")
  private boolean myTextEnabled;

  @SuppressWarnings("FieldMayBeFinal")
  @Attribute("StylesheetText")
  @NotNull
  private String myStylesheetText;

  private MarkdownCssSettings() {
    this(false, "", false, "");
  }

  public MarkdownCssSettings(boolean customStylesheetEnabled,
                             @NotNull String customStylesheetPath,
                             boolean textEnabled,
                             @NotNull String stylesheetText) {
    myCustomStylesheetEnabled = customStylesheetEnabled;
    myCustomStylesheetPath = customStylesheetPath;
    myTextEnabled = textEnabled;
    myStylesheetText = stylesheetText;
  }

  public boolean isCustomStylesheetEnabled() {
    return myCustomStylesheetEnabled;
  }

  @NotNull
  public String getCustomStylesheetPath() {
    return myCustomStylesheetPath;
  }

  public boolean isTextEnabled() {
    return myTextEnabled;
  }

  @NotNull
  public String getCustomStylesheetText() {
    return myStylesheetText;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MarkdownCssSettings settings = (MarkdownCssSettings)o;

    if (myCustomStylesheetEnabled != settings.myCustomStylesheetEnabled) return false;
    if (myTextEnabled != settings.myTextEnabled) return false;
    if (!myCustomStylesheetPath.equals(settings.myCustomStylesheetPath)) return false;
    if (!myStylesheetText.equals(settings.myStylesheetText)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myCustomStylesheetEnabled ? 1 : 0);
    result = 31 * result + myCustomStylesheetPath.hashCode();
    result = 31 * result + (myTextEnabled ? 1 : 0);
    result = 31 * result + myStylesheetText.hashCode();
    return result;
  }

  public interface Holder {
    void setMarkdownCssSettings(@NotNull MarkdownCssSettings settings);

    @NotNull
    MarkdownCssSettings getMarkdownCssSettings();
  }
}
