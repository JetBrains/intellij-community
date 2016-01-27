/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class EmptyResourceBundle {
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

      @NotNull
      public String getBaseName() {
        return "";
      }

      @NotNull
      public VirtualFile getBaseDirectory() {
        throw new IllegalStateException();
      }

      @NotNull
      @Override
      public Project getProject() {
        throw new IllegalStateException();
      }
    };
  }
  public static ResourceBundle getInstance() {
    return Holder.NULL;
  }
}
