/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties;

import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleManagerState {
  @Property(surroundWithTag = false)
  @XCollection(elementName = "file", valueAttributeName = "url")
  public Set<String> myDissociatedFiles = new HashSet<>();

  @Property(surroundWithTag = false)
  @XCollection(elementName = "custom-rb")
  public List<CustomResourceBundleState> myCustomResourceBundles = new ArrayList<>();

  public Set<String> getDissociatedFiles() {
    return myDissociatedFiles;
  }

  public List<CustomResourceBundleState> getCustomResourceBundles() {
    return myCustomResourceBundles;
  }

  public boolean isEmpty() {
    return myCustomResourceBundles.isEmpty() && myDissociatedFiles.isEmpty();
  }

  public ResourceBundleManagerState removeNonExistentFiles() {
    final ResourceBundleManagerState newState = new ResourceBundleManagerState();

    final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();

    for (final String dissociatedFileUrl : myDissociatedFiles) {
      if (virtualFileManager.findFileByUrl(dissociatedFileUrl) != null) {
        newState.myDissociatedFiles.add(dissociatedFileUrl);
      }
    }

    for (CustomResourceBundleState customResourceBundle : myCustomResourceBundles) {
      final CustomResourceBundleState updatedCustomResourceBundle = customResourceBundle.removeNonExistentFiles(virtualFileManager);
      if (updatedCustomResourceBundle != null) {
        newState.myCustomResourceBundles.add(updatedCustomResourceBundle);
      }
    }

    return newState;
  }
}
