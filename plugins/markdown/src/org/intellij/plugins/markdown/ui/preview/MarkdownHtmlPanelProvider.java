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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public abstract class MarkdownHtmlPanelProvider {

  public static final ExtensionPointName<MarkdownHtmlPanelProvider> EP_NAME =
    ExtensionPointName.create("org.intellij.markdown.html.panel.provider");

  @NotNull
  public abstract MarkdownHtmlPanel createHtmlPanel();

  @ApiStatus.Experimental
  @NotNull
  public MarkdownHtmlPanel createHtmlPanel(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return createHtmlPanel();
  }

  @NotNull
  public abstract AvailabilityInfo isAvailable();

  @NotNull
  public abstract ProviderInfo getProviderInfo();

  public static MarkdownHtmlPanelProvider @NotNull [] getProviders() {
    return EP_NAME.getExtensions();
  }

  @NotNull
  public static MarkdownHtmlPanelProvider createFromInfo(@NotNull ProviderInfo providerInfo) {
    for (MarkdownHtmlPanelProvider provider : getProviders()) {
      if (provider.getProviderInfo().getClassName().equals(providerInfo.getClassName())) {
        return provider;
      }
    }
    return getProviders()[0];
  }

  public static boolean hasAvailableProviders() {
    return ContainerUtil.exists(getProviders(), provider -> provider.isAvailable() == AvailabilityInfo.AVAILABLE);
  }

  public static @NotNull List<MarkdownHtmlPanelProvider> getAvailableProviders() {
    return ContainerUtil.filter(getProviders(), provider -> provider.isAvailable() == AvailabilityInfo.AVAILABLE);
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
