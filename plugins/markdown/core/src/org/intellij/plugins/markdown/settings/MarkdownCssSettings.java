// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings;

import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Deprecated
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

  @NotNull
  private Integer myFontSize;

  @NotNull
  private String myFontFamily;

  private MarkdownCssSettings() {
    this(false,
         "",
         false, "",
         JBCefApp.normalizeScaledSize(Objects.requireNonNull(AppEditorFontOptions.getInstance().getState()).FONT_SIZE),//note: may be get from default.css
         Objects.requireNonNull(AppEditorFontOptions.getInstance().getState()).FONT_FAMILY); //note: may be get from default.css
  }

  public MarkdownCssSettings(boolean customStylesheetEnabled,
                             @NotNull String customStylesheetPath,
                             boolean textEnabled,
                             @NotNull String stylesheetText,
                             @NotNull Integer fontSize,
                             @NotNull String fontFamily) {
    myCustomStylesheetEnabled = customStylesheetEnabled;
    myCustomStylesheetPath = customStylesheetPath;
    myTextEnabled = textEnabled;
    myStylesheetText = stylesheetText;
    myFontSize = fontSize;
    myFontFamily = fontFamily;
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

  @NotNull
  public Integer getFontSize() {
    return myFontSize;
  }

  public void setFontSize(@NotNull Integer fontSize) {
    myFontSize = fontSize;
  }

  @NotNull
  public String getFontFamily() {
    return myFontFamily;
  }

  public void setFontFamily(@NotNull String fontFamily) {
    myFontFamily = fontFamily;
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
    if (!myFontSize.equals(settings.myFontSize)) return false;
    if (!myFontFamily.equals(settings.myFontFamily)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myCustomStylesheetEnabled, myCustomStylesheetPath, myTextEnabled, myStylesheetText, myFontSize, myFontFamily);
  }

  public interface Holder {
    void setMarkdownCssSettings(@NotNull MarkdownCssSettings settings);

    @NotNull
    MarkdownCssSettings getMarkdownCssSettings();
  }
}
