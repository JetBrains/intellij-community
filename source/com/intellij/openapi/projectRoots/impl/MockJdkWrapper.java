/**
 * @author cdr
 */
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.roots.RootProvider;

import java.io.File;

/**
 * used to override JdkHome location in order to provide correct paths
 */
public final class MockJdkWrapper implements ProjectJdk {
  private final String myHomePath;
  private final ProjectJdk myDelegate;

  public MockJdkWrapper(String homePath, ProjectJdk delegate) {
    myHomePath = homePath;
    myDelegate = delegate;
  }

  public VirtualFile getHomeDirectory() {
    return LocalFileSystem.getInstance().findFileByIoFile(new File(getHomePath()));
  }

  public String getHomePath() {
    final String homePath = FileUtil.toSystemDependentName(myHomePath == null ? myDelegate.getHomePath() : myHomePath);
    return StringUtil.trimEnd(homePath, File.separator);
  }

  public SdkType getSdkType() {
    return myDelegate.getSdkType();
  }

  public String getName() {
    return myDelegate.getName();
  }

  public String getVersionString() {
    return myDelegate.getVersionString();
  }

  public String getBinPath() {
    return getSdkType().getBinPath(this);
  }

  public String getToolsPath() {
    return getSdkType().getToolsPath(this);
  }

  public String getVMExecutablePath() {
    return getSdkType().getVMExecutablePath(this);
  }

  public String getRtLibraryPath() {
    return getSdkType().getRtLibraryPath(this);
  }

  public RootProvider getRootProvider() {
    return myDelegate.getRootProvider();
  }

  public Object clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();
  }

  public SdkAdditionalData getSdkAdditionalData() {
    return null;
  }

  public SdkModificator getSdkModificator() {
    return null;
  }
}