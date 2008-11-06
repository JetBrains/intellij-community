package com.jetbrains.python.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonModuleBuilder extends ModuleBuilder implements SourcePathsBuilder {
  private List<Pair<String, String>> mySourcePaths;
  private String myContentRootPath;
  private Sdk mySdk;

  public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {
    if (mySdk != null) {
      rootModel.setSdk(mySdk);
    }
    else {
      rootModel.inheritSdk();
    }
    String moduleRootPath = getContentEntryPath();
    if (moduleRootPath != null) {
      LocalFileSystem lfs = LocalFileSystem.getInstance();
      VirtualFile moduleContentRoot = lfs.refreshAndFindFileByPath(FileUtil.toSystemIndependentName(moduleRootPath));
      if (moduleContentRoot != null) {
        rootModel.addContentEntry(moduleContentRoot);
      }
    }
  }

  public ModuleType getModuleType() {
    return PythonModuleType.getInstance();
  }

  public String getContentEntryPath() {
    return myContentRootPath;
  }

  public void setContentEntryPath(final String moduleRootPath) {
    myContentRootPath = moduleRootPath;
  }

  public List<Pair<String, String>> getSourcePaths() {
    return mySourcePaths;
  }

  public void setSourcePaths(final List<Pair<String, String>> sourcePaths) {
    mySourcePaths = sourcePaths;
  }

  public void addSourcePath(final Pair<String, String> sourcePathInfo) {
    if (mySourcePaths == null) {
      mySourcePaths = new ArrayList<Pair<String, String>>();
    }
    mySourcePaths.add(sourcePathInfo);
  }

  public void setSdk(final Sdk sdk) {
    mySdk = sdk;
  }
}
