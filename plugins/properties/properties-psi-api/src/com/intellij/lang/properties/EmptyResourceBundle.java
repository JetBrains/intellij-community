// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Anna Bulenkova
 */
public final class EmptyResourceBundle {
  private EmptyResourceBundle() {}
  private static class Holder {
    private static final ResourceBundle NULL = new ResourceBundle() {
      @Override
      public @NotNull List<PropertiesFile> getPropertiesFiles() {
        return Collections.emptyList();
      }

      @Override
      public @NotNull PropertiesFile getDefaultPropertiesFile() {
        throw new IllegalStateException();
      }

      @Override
      public @NotNull String getBaseName() {
        return "";
      }

      @Override
      public @NotNull VirtualFile getBaseDirectory() {
        throw new IllegalStateException();
      }

      @Override
      public @NotNull Project getProject() {
        throw new IllegalStateException();
      }

      @Override
      public boolean isValid() {
        return false;
      }
    };
  }
  public static ResourceBundle getInstance() {
    return Holder.NULL;
  }
}
