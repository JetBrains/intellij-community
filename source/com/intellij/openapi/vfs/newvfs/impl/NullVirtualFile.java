/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class NullVirtualFile extends StubVirtualFile {
  public static NullVirtualFile INSTANCE = new NullVirtualFile();

  private NullVirtualFile() {}

  @NonNls
  public String toString() {
    return "VirtualFile.NULL_OBJECT";
  }
}