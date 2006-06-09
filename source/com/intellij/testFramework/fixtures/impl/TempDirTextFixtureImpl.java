/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class TempDirTextFixtureImpl implements TempDirTestFixture {

  private final ArrayList<File> myFilesToDelete = new ArrayList<File>();
  private File myTempDir;

  public VirtualFile copyFile(VirtualFile file) {
    try {
      createTempDirectory();
      VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(myTempDir.getCanonicalPath().replace(File.separatorChar, '/'));
      return VfsUtil.copyFile(this, file, vDir);
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot copy " + file, e);
    }
  }

  public String getTempDirPath() {
    return createTempDirectory().getAbsolutePath();
  }

  public void setUp() throws Exception {

  }

  public void tearDown() throws Exception {
    for (final File fileToDelete : myFilesToDelete) {
      delete(fileToDelete);
    }
  }

  protected File createTempDirectory() {
    try {
      if (myTempDir == null) {
        myTempDir = FileUtil.createTempDirectory("unitTest", null);
        myFilesToDelete.add(myTempDir);
      }
      return myTempDir;
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot create temp dir", e);
    }
  }

  private static void delete(File file) {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      for (File fileToDelete : files) {
        delete(fileToDelete);
      }
    }

    boolean b = file.delete();
    if (!b && file.exists()) {
      TestCase.fail("Can't delete " + file.getAbsolutePath());
    }
  }

}
