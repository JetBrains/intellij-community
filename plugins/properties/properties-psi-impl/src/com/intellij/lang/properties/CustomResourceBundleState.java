/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
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
  @XCollection(elementName = "file")
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
