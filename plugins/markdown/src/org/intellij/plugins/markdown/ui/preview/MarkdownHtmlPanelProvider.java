// Copyright 2000-2018 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.xmlb.annotations.Attribute;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

public abstract class MarkdownHtmlPanelProvider {

  public static final ExtensionPointName<MarkdownHtmlPanelProvider> EP_NAME =
    ExtensionPointName.create("org.intellij.markdown.html.panel.provider");

  @NotNull
  public abstract MarkdownHtmlPanel createHtmlPanel();

  @NotNull
  public abstract AvailabilityInfo isAvailable();

  @NotNull
  public abstract ProviderInfo getProviderInfo();

  public static MarkdownHtmlPanelProvider @NotNull [] getProviders() {
    return EP_NAME.getExtensions();
  }

  @NotNull
  public static MarkdownHtmlPanelProvider createFromInfo(@NotNull ProviderInfo providerInfo) {
    try {
      return ((MarkdownHtmlPanelProvider)Class.forName(providerInfo.getClassName()).newInstance());
    }
    catch (Exception e) {
      Messages.showMessageDialog(
        MarkdownBundle.message("dialog.message.cannot.set.preview.panel.provider", providerInfo.getName(), e.getMessage()),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
      Logger.getInstance(MarkdownHtmlPanelProvider.class).error(e);
      return getProviders()[0];
    }
  }

  public static boolean hasAvailableProviders() {
    return Arrays.stream(getProviders()).anyMatch(provider -> provider.isAvailable() == AvailabilityInfo.AVAILABLE);
  }

  public static class ProviderInfo {
    @NotNull
    @Attribute("name")
    private String myName;
    @NotNull
    @Attribute("className")
    private String className;

    @SuppressWarnings("unused")
    private ProviderInfo() {
      myName = "";
      className = "";
    }

    public ProviderInfo(@NotNull String name, @NotNull String className) {
      myName = name;
      this.className = className;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @NotNull
    public String getClassName() {
      return className;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ProviderInfo info = (ProviderInfo)o;

      if (!myName.equals(info.myName)) return false;
      if (!className.equals(info.className)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myName.hashCode();
      result = 31 * result + className.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return myName;
    }
  }

  public static abstract class AvailabilityInfo {
    public static final AvailabilityInfo AVAILABLE = new AvailabilityInfo() {
      @Override
      public boolean checkAvailability(@NotNull JComponent parentComponent) {
        return true;
      }
    };

    public static final AvailabilityInfo UNAVAILABLE = new AvailabilityInfo() {
      @Override
      public boolean checkAvailability(@NotNull JComponent parentComponent) {
        return false;
      }
    };

    public abstract boolean checkAvailability(@NotNull JComponent parentComponent);
  }
}
