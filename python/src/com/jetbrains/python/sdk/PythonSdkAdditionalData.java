package com.jetbrains.python.sdk;

import com.google.common.collect.Sets;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.django.util.VirtualFileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PythonSdkAdditionalData implements SdkAdditionalData {
  @NonNls private static final String PATHS_ADDED_BY_USER_ROOT = "PATHS_ADDED_BY_USER_ROOT";
  @NonNls private static final String PATH_ADDED_BY_USER = "PATH_ADDED_BY_USER";
  @NonNls private static final String PATHS_REMOVED_BY_USER_ROOT = "PATHS_REMOVED_BY_USER_ROOT";
  @NonNls private static final String PATH_REMOVED_BY_USER = "PATH_REMOVED_BY_USER";
  @NonNls private static final String ASSOCIATED_PROJECT_PATH = "ASSOCIATED_PROJECT_PATH";


  private Set<VirtualFile> myAddedPaths = Sets.newHashSet();
  private Set<VirtualFile> myExcludedPaths = Sets.newHashSet();
  private final PythonSdkFlavor myFlavor;
  private String myAssociatedProjectPath;

  public PythonSdkAdditionalData(@Nullable PythonSdkFlavor flavor) {
    myFlavor = flavor;
  }

  public Object clone() throws CloneNotSupportedException {
    try {
      final PythonSdkAdditionalData copy = (PythonSdkAdditionalData)super.clone();
      copy.setAddedPaths(getAddedPaths());
      copy.setExcludedPaths(getExcludedPaths());
      copy.setAssociatedProjectPath(getAssociatedProjectPath());
      return copy;
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public Set<VirtualFile> getAddedPaths() {
    return myAddedPaths;
  }

  public void setAddedPaths(Set<VirtualFile> addedPaths) {
    myAddedPaths = Sets.newHashSet(addedPaths);
  }

  public Set<VirtualFile> getExcludedPaths() {
    return myExcludedPaths;
  }

  public void setExcludedPaths(Set<VirtualFile> excludedPaths) {
    myExcludedPaths = Sets.newHashSet(excludedPaths);
  }

  public String getAssociatedProjectPath() {
    return myAssociatedProjectPath;
  }

  public void setAssociatedProjectPath(@Nullable String associatedProjectPath) {
    myAssociatedProjectPath = associatedProjectPath;
  }

  public void associateWithProject(Project project) {
    final String path = project.getBasePath();
    if (path != null) {
      myAssociatedProjectPath = FileUtil.toSystemIndependentName(path);
    }
  }

  @Override
  public void checkValid(SdkModel sdkModel) throws ConfigurationException {

  }

  public void save(@NotNull final Element rootElement) {
    for (VirtualFile addedPath : myAddedPaths) {
      final Element child = new Element(PATHS_ADDED_BY_USER_ROOT);
      child.setAttribute(PATH_ADDED_BY_USER, addedPath.getPath());
      rootElement.addContent(child);
    }

    for (VirtualFile removed : myExcludedPaths) {
      final Element child = new Element(PATHS_REMOVED_BY_USER_ROOT);
      child.setAttribute(PATH_REMOVED_BY_USER, removed.getPath());
      rootElement.addContent(child);
    }
    if (myAssociatedProjectPath != null) {
      rootElement.setAttribute(ASSOCIATED_PROJECT_PATH, myAssociatedProjectPath);
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
    data.setAddedPaths(loadStringList(element, PATHS_ADDED_BY_USER_ROOT, PATH_ADDED_BY_USER));
    data.setExcludedPaths(loadStringList(element, PATHS_REMOVED_BY_USER_ROOT, PATH_REMOVED_BY_USER));
    data.setAssociatedProjectPath(element.getAttributeValue(ASSOCIATED_PROJECT_PATH));
  }

  private static Set<VirtualFile> loadStringList(@Nullable Element element, @NotNull String rootName, @NotNull String attrName) {
    final List<String> paths = new LinkedList<String>();
    if (element != null) {
      final List list = element.getChildren(rootName);
      if (list != null) {
        for (Object o : list) {
          paths.add(((Element)o).getAttribute(attrName).getValue());
        }
      }
    }
    final Set<VirtualFile> files = Sets.newHashSet();
    for (String path : paths) {
      VirtualFile vf = VirtualFileUtil.findFile(path);
      if (vf != null) {
        files.add(vf);
      }
    }
    return files;
  }
}
