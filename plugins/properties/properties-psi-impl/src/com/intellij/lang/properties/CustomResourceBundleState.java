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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
@Tag("custom-resource-bundle")
public class CustomResourceBundleState {

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false, elementTag = "file", elementValueAttribute = "value")
  public Set<String> myFileUrls = new HashSet<>();

  @Tag("base-name")
  public String myBaseName;

  @Transient
  @NotNull
  public String getBaseName() {
    return myBaseName;
  }

  public Set<String> getFileUrls() {
    return myFileUrls;
  }

  public List<VirtualFile> getFiles(@NotNull final VirtualFileManager manager) {
    return ContainerUtil.mapNotNull(getFileUrls(), url -> manager.findFileByUrl(url));
  }

  @Nullable
  public CustomResourceBundleState removeNonExistentFiles(final VirtualFileManager virtualFileManager) {
    final List<String> existentFiles = ContainerUtil.filter(myFileUrls, url -> virtualFileManager.findFileByUrl(url) != null);
    if (existentFiles.isEmpty()) {
      return null;
    }
    final CustomResourceBundleState customResourceBundleState = new CustomResourceBundleState();
    customResourceBundleState.myFileUrls.addAll(existentFiles);
    customResourceBundleState.myBaseName = myBaseName;
    return customResourceBundleState;
  }

  public CustomResourceBundleState addAll(final Collection<String> urls) {
    myFileUrls.addAll(urls);
    return this;
  }

  public CustomResourceBundleState setBaseName(String baseName) {
    myBaseName = baseName;
    return this;
  }
}
