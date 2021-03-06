// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.vfs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class MavenPropertiesVirtualFile extends VirtualFile {
  private final String myPath;
  @NotNull
  private final VirtualFileSystem myFS;
  private final byte[] myContent;

  public MavenPropertiesVirtualFile(String path, Properties properties, @NotNull VirtualFileSystem FS) {
    myPath = path;
    myFS = FS;

    myContent = createContent(properties);
  }

  private static byte[] createContent(Properties properties) {
    StringBuilder builder = new StringBuilder();
    TreeSet<String> sortedKeys = new TreeSet<>((Set)properties.keySet());
    for (String each : sortedKeys) {
      builder.append(StringUtil.escapeProperty(each, true));
      builder.append("=");
      builder.append(StringUtil.escapeProperty(properties.getProperty(each), false));
      builder.append("\n");
    }
    return builder.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  @NotNull
  public String getName() {
    return myPath;
  }

  @Override
  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFS;
  }

  @Override
  @NotNull
  public String getPath() {
    return myPath;
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public VirtualFile getParent() {
    return null;
  }

  @Override
  public VirtualFile[] getChildren() {
    return null;
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    if (myContent == null) throw new IOException();
    return myContent;
  }

  @Override
  public long getTimeStamp() {
    return -1;
  }

  @Override
  public long getModificationStamp() {
    return Arrays.hashCode(myContent);
  }

  @Override
  public long getLength() {
    return myContent.length;
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  @Override
  public @NotNull InputStream getInputStream() throws IOException {
    return VfsUtilCore.byteStreamSkippingBOM(myContent,this);
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }
}
