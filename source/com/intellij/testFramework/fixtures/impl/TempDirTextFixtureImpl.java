/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class TempDirTextFixtureImpl extends BaseFixture implements TempDirTestFixture {

  private final ArrayList<File> myFilesToDelete = new ArrayList<File>();
  private File myTempDir;

  public VirtualFile copyFile(VirtualFile file) {
    try {
      createTempDirectory();
      VirtualFile tempDir =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(myTempDir.getCanonicalPath().replace(File.separatorChar, '/'));
      return VfsUtil.copyFile(this, file, tempDir);
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot copy " + file, e);
    }
  }

  public void copyAll(final String dataDir) {
    createTempDirectory();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          VirtualFile tempDir =
            LocalFileSystem.getInstance().refreshAndFindFileByPath(myTempDir.getCanonicalPath().replace(File.separatorChar, '/'));
          final VirtualFile from = LocalFileSystem.getInstance().refreshAndFindFileByPath(dataDir);
          assert from != null : dataDir + " not found";
          VfsUtil.copyDirectory(null, from, tempDir, null);
        }
        catch (IOException e) {
          assert false : "Cannot copy " + dataDir + ": " + e;
        }
      }
    });
  }

  public String getTempDirPath() {
    return createTempDirectory().getAbsolutePath();
  }

  @Nullable
  public VirtualFile getFile(final String path) {

    final Ref<VirtualFile> result = new Ref<VirtualFile>(null);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          final String fullPath = myTempDir.getCanonicalPath().replace(File.separatorChar, '/') + "/" + path;
          final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath);
          result.set(file);
        }
        catch (IOException e) {
          assert false : "Cannot find " + path + ": " + e;
        }
      }
    });
    return result.get();
  }

  @NotNull
  public VirtualFile createFile(final String name) {
    final File file = createTempDirectory();
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        try {
          final File file1 = new File(file, name);
          file1.createNewFile();
          return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public void setUp() throws Exception {
    super.setUp();
    createTempDirectory();
  }

  public void tearDown() throws Exception {
    for (final File fileToDelete : myFilesToDelete) {
      delete(fileToDelete);
    }
    super.tearDown();
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
      assert false : "Can't delete " + file.getAbsolutePath();
    }
  }

}
