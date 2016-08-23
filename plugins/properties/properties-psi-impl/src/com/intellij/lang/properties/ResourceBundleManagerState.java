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

import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleManagerState {

  @Property(surroundWithTag = false)
  @AbstractCollection(elementValueAttribute = "url", elementTag = "file", surroundWithTag = false)
  public Set<String> myDissociatedFiles = new HashSet<>();

  @Property(surroundWithTag = false)
  @AbstractCollection(elementTag = "custom-rb", surroundWithTag = false)
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
