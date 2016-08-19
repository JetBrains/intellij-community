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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
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
    if (ResourceBundleManager.getInstance(getProject()).isDefaultDissociated(myDefaultPropertiesFile.getVirtualFile())) {
      return Collections.singletonList(myDefaultPropertiesFile);
    }
    PsiFile[] children =
      ApplicationManager.getApplication().runReadAction(new Computable<PsiFile[]>() {
        @Override
        public PsiFile[] compute() {
          return myDefaultPropertiesFile.getParent().getFiles();
        }
      });
    final String baseName = getBaseName();
    List<PropertiesFile> result = new SmartList<>();
    for (PsiFile file : children) {
      if (!file.isValid()) continue;
      PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
      if (propertiesFile == null) {
        continue;
      }
      if (Comparing.strEqual(PropertiesUtil.getDefaultBaseName(file.getVirtualFile()), baseName)) {
        result.add(propertiesFile);
        if (!propertiesFile.equals(myDefaultPropertiesFile) &&
            Comparing.equal(propertiesFile.getName(), myDefaultPropertiesFile.getName())) {
          return Collections.singletonList(myDefaultPropertiesFile);
        }
      }
    }
    return result;
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