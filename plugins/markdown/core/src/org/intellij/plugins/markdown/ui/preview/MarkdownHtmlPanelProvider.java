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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public abstract class MarkdownHtmlPanelProvider {

  public static final ExtensionPointName<MarkdownHtmlPanelProvider> EP_NAME =
    new ExtensionPointName<>("org.intellij.markdown.html.panel.provider");

  public abstract @NotNull MarkdownHtmlPanel createHtmlPanel();

  @ApiStatus.Experimental
  public @NotNull MarkdownHtmlPanel createHtmlPanel(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return createHtmlPanel();
  }

  public abstract @NotNull AvailabilityInfo isAvailable();

  public abstract @NotNull ProviderInfo getProviderInfo();

  public @NotNull SourceTextPreprocessor getSourceTextPreprocessor() {
    return SourceTextPreprocessor.getDefault();
  }

  public static  @NotNull List<MarkdownHtmlPanelProvider> getProviders() {
    return EP_NAME.getExtensionList();
  }

  public static @NotNull MarkdownHtmlPanelProvider createFromInfo(@NotNull ProviderInfo providerInfo) {
    for (MarkdownHtmlPanelProvider provider : getProviders()) {
      if (provider.getProviderInfo().getClassName().equals(providerInfo.getClassName())) {
        return provider;
      }
    }
    return getProviders().get(0);
  }

  public static boolean hasAvailableProviders() {
    return ContainerUtil.exists(getProviders(), provider -> provider.isAvailable() == AvailabilityInfo.AVAILABLE);
  }

  public static @NotNull List<MarkdownHtmlPanelProvider> getAvailableProviders() {
    return ContainerUtil.filter(getProviders(), provider -> provider.isAvailable() == AvailabilityInfo.AVAILABLE);
  }

  public static final class ProviderInfo {
    @Attribute("name") private @NotNull String myName;
    @Attribute("className") private @NotNull String className;

    @SuppressWarnings("unused")
    private ProviderInfo() {
      myName = "";
      className = "";
    }

    public ProviderInfo(@NotNull String name, @NotNull String className) {
      myName = name;
      this.className = className;
    }

    public @NotNull String getName() {
      return myName;
    }

    public @NotNull String getClassName() {
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

  public abstract static class AvailabilityInfo {
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
