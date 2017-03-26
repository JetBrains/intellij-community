/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author Alexey
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ResourceBundleImpl extends ResourceBundle {
  @NotNull private final PropertiesFile myDefaultPropertiesFile;
  private boolean myValid = true;

  public ResourceBundleImpl(@NotNull final PropertiesFile defaultPropertiesFile) {
    myDefaultPropertiesFile = defaultPropertiesFile;
  }

  @NotNull
  @Override
  public List<PropertiesFile> getPropertiesFiles() {
    return PropertiesImplUtil.getResourceBundleWithCachedFiles(myDefaultPropertiesFile).getFiles();
  }

  @NotNull
  @Override
  public PropertiesFile getDefaultPropertiesFile() {
    return myDefaultPropertiesFile;
  }

  @NotNull
  @Override
  public String getBaseName() {
    return ResourceBundleManager.getInstance(getProject()).getBaseName(myDefaultPropertiesFile.getContainingFile());
  }

  @NotNull
  public VirtualFile getBaseDirectory() {
    return myDefaultPropertiesFile.getParent().getVirtualFile();
  }

  @Override
  public boolean isValid() {
    return myValid && myDefaultPropertiesFile.getContainingFile().isValid();
  }

  public void invalidate() {
    myValid = false;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ResourceBundleImpl resourceBundle = (ResourceBundleImpl)o;
    if (!myDefaultPropertiesFile.equals(resourceBundle.myDefaultPropertiesFile)) return false;
    return true;
  }

  public int hashCode() {
    return myDefaultPropertiesFile.hashCode();
  }

  public String getUrl() {
    return getBaseDirectory() + "/" + getBaseName();
  }

  @Override
  public String toString() {
    return "ResourceBundleImpl:" + getBaseName();
  }
}