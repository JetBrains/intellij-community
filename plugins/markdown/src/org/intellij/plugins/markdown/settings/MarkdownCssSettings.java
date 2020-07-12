// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.net.URISyntaxException;
import java.net.URL;

public final class MarkdownCssSettings {
  public static final MarkdownCssSettings DEFAULT = new MarkdownCssSettings();

  @SuppressWarnings("FieldMayBeFinal")
  @Attribute("UriEnabled")
  private boolean myUriEnabled;

  @SuppressWarnings("FieldMayBeFinal")
  @Attribute("StylesheetUri")
  @NotNull
  private String myStylesheetUri;

  @SuppressWarnings("FieldMayBeFinal")
  @Attribute("TextEnabled")
  private boolean myTextEnabled;

  @SuppressWarnings("FieldMayBeFinal")
  @Attribute("StylesheetText")
  @NotNull
  private String myStylesheetText;

  private MarkdownCssSettings() {
    this(false, getDefaultCssURI(), false, "");
  }

  public MarkdownCssSettings(boolean uriEnabled, @NotNull String stylesheetUri, boolean textEnabled, @NotNull String stylesheetText) {
    myUriEnabled = uriEnabled;
    myStylesheetUri = stylesheetUri;
    myTextEnabled = textEnabled;
    myStylesheetText = stylesheetText;
  }

  public boolean isUriEnabled() {
    return myUriEnabled;
  }

  @NotNull
  public String getStylesheetUri() {
    return myStylesheetUri;
  }

  public boolean isTextEnabled() {
    return myTextEnabled;
  }

  @NotNull
  public String getStylesheetText() {
    return myStylesheetText;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MarkdownCssSettings settings = (MarkdownCssSettings)o;

    if (myUriEnabled != settings.myUriEnabled) return false;
    if (myTextEnabled != settings.myTextEnabled) return false;
    if (!myStylesheetUri.equals(settings.myStylesheetUri)) return false;
    if (!myStylesheetText.equals(settings.myStylesheetText)) return false;

    return true;
  }

  @NotNull
  private static String getDefaultCssURI() {
    try {
      final URL resource = MarkdownCssSettings.class.getResource("default.css");
      return resource != null ? resource.toURI().toString() : "";
    }
    catch (URISyntaxException e) {
      Logger.getInstance(MarkdownCssSettings.class).error(e);
      return "";
    }
  }

  @Override
  public int hashCode() {
    int result = (myUriEnabled ? 1 : 0);
    result = 31 * result + myStylesheetUri.hashCode();
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
