// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      @NotNull
      @Override
      public List<PropertiesFile> getPropertiesFiles() {
        return Collections.emptyList();
      }

      @NotNull
      @Override
      public PropertiesFile getDefaultPropertiesFile() {
        throw new IllegalStateException();
      }

      @Override
      @NotNull
      public String getBaseName() {
        return "";
      }

      @Override
      @NotNull
      public VirtualFile getBaseDirectory() {
        throw new IllegalStateException();
      }

      @NotNull
      @Override
      public Project getProject() {
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
