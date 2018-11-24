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
package com.jetbrains.python.sdk;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.impl.SimpleProjectRoot;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.JDOMExternalizer.loadStringsList;

/**
 * @author traff
 */
public class PythonSdkAdditionalData implements SdkAdditionalData {
  @NonNls private static final String PATHS_ADDED_BY_USER_ROOT = "PATHS_ADDED_BY_USER_ROOT";
  @NonNls private static final String PATH_ADDED_BY_USER = "PATH_ADDED_BY_USER";
  @NonNls private static final String PATHS_REMOVED_BY_USER_ROOT = "PATHS_REMOVED_BY_USER_ROOT";
  @NonNls private static final String PATH_REMOVED_BY_USER = "PATH_REMOVED_BY_USER";
  @NonNls private static final String ASSOCIATED_PROJECT_PATH = "ASSOCIATED_PROJECT_PATH";


  private Set<SimpleProjectRoot> myAddedPaths = Sets.newHashSet();
  private Set<SimpleProjectRoot> myExcludedPaths = Sets.newHashSet();

  private final PythonSdkFlavor myFlavor;
  private String myAssociatedProjectPath;
  private boolean myAssociateWithNewProject;

  public PythonSdkAdditionalData(@Nullable PythonSdkFlavor flavor) {
    myFlavor = flavor;
  }

  public Object clone() throws CloneNotSupportedException {
    final PythonSdkAdditionalData copy = new PythonSdkAdditionalData(myFlavor);
    copy.setAddedPaths(getAddedPaths());
    copy.setExcludedPaths(getExcludedPaths());
    copy.setAssociatedProjectPath(getAssociatedProjectPath());
    return copy;
  }

  public Set<SimpleProjectRoot> getAddedPaths() {
    return myAddedPaths;
  }

  public void setAddedPaths(Set<SimpleProjectRoot> addedPaths) {
    myAddedPaths = Sets.newHashSet(addedPaths);
  }

  public void setAddedPathsFromVirtualFiles(Set<VirtualFile> addedPaths) {
    myAddedPaths = Sets.newHashSet();
    for (VirtualFile file : addedPaths) {
      myAddedPaths.add(new SimpleProjectRoot(file));
    }
  }

  public void setExcludedPathsFromVirtualFiles(Set<VirtualFile> addedPaths) {
    myExcludedPaths = Sets.newHashSet();
    for (VirtualFile file : addedPaths) {
      myExcludedPaths.add(new SimpleProjectRoot(file));
    }
  }

  public Set<SimpleProjectRoot> getExcludedPaths() {
    return myExcludedPaths;
  }

  public void setExcludedPaths(Set<SimpleProjectRoot> excludedPaths) {
    myExcludedPaths = Sets.newHashSet(excludedPaths);
  }

  public String getAssociatedProjectPath() {
    return myAssociatedProjectPath;
  }

  public void setAssociatedProjectPath(@Nullable String associatedProjectPath) {
    myAssociatedProjectPath = associatedProjectPath;
    myAssociateWithNewProject = false;
  }

  public void associateWithProject(Project project) {
    final String path = project.getBasePath();
    if (path != null) {
      myAssociatedProjectPath = FileUtil.toSystemIndependentName(path);
    }
    myAssociateWithNewProject = false;
  }

  public void associateWithNewProject() {
    myAssociateWithNewProject = true;
  }

  public void reassociateWithCreatedProject(Project project) {
    if (myAssociateWithNewProject) {
      associateWithProject(project);
    }
  }

  public void save(@NotNull final Element rootElement) {
    savePaths(rootElement, myAddedPaths, PATHS_ADDED_BY_USER_ROOT, PATH_ADDED_BY_USER);
    savePaths(rootElement, myExcludedPaths, PATHS_REMOVED_BY_USER_ROOT, PATH_REMOVED_BY_USER);

    if (myAssociatedProjectPath != null) {
      rootElement.setAttribute(ASSOCIATED_PROJECT_PATH, myAssociatedProjectPath);
    }
  }

  private static void savePaths(Element rootElement, Set<SimpleProjectRoot> paths, String root, String element) {
    for (SimpleProjectRoot addedPath : paths) {
      final Element child = new Element(root);
      child.setAttribute(element, addedPath.getUrl());
      rootElement.addContent(child);
    }
  }

  @Nullable
  public PythonSdkFlavor getFlavor() {
    return myFlavor;
  }

  @NotNull
  public static PythonSdkAdditionalData load(Sdk sdk, @Nullable Element element) {
    final PythonSdkAdditionalData data = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk.getHomePath()));

    load(element, data);

    return data;
  }

  protected static void load(@Nullable Element element, @NotNull PythonSdkAdditionalData data) {
    data.setAddedPaths(collectPaths(loadStringsList(element, PATHS_ADDED_BY_USER_ROOT, PATH_ADDED_BY_USER)));
    data.setExcludedPaths(collectPaths(loadStringsList(element, PATHS_REMOVED_BY_USER_ROOT, PATH_REMOVED_BY_USER)));
    if (element != null) {
      data.setAssociatedProjectPath(element.getAttributeValue(ASSOCIATED_PROJECT_PATH));
    }
  }

  protected static Set<SimpleProjectRoot> collectPaths(@NotNull List<String> paths) {
    final Set<SimpleProjectRoot> files = Sets.newHashSet();
    for (String path : paths) {
      if (StringUtil.isEmpty(path)) continue;
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      SimpleProjectRoot root;
      if (file != null) {
        root = new SimpleProjectRoot(file);
      }
      else {
        root = new SimpleProjectRoot(path);
      }
      files.add(root);
    }
    return files;
  }


  public Set<VirtualFile> getAddedPathFiles() {
    return getPathsAsVirtualFiles(myAddedPaths);
  }

  public Set<VirtualFile> getExcludedPathFiles() {
    return getPathsAsVirtualFiles(myExcludedPaths);
  }

  private static Set<VirtualFile> getPathsAsVirtualFiles(Set<SimpleProjectRoot> paths) {
    Set<VirtualFile> ret = Sets.newHashSet();
    for (SimpleProjectRoot root : paths) {
      ret.addAll(Lists.newArrayList(root.getVirtualFiles()));
    }
    return ret;
  }
}
