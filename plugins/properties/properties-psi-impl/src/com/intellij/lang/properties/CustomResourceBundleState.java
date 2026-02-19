// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.HashSet;
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
  public @NotNull String getBaseName() {
    return myBaseName;
  }

  public Set<String> getFileUrls() {
    return myFileUrls;
  }

  public @Unmodifiable List<VirtualFile> getFiles(final @NotNull VirtualFileManager manager) {
    return ContainerUtil.mapNotNull(getFileUrls(), url -> manager.findFileByUrl(url));
  }

  public @Nullable CustomResourceBundleState removeNonExistentFiles(final VirtualFileManager virtualFileManager) {
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
