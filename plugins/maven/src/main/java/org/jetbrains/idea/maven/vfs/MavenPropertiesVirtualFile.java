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
package org.jetbrains.idea.maven.vfs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class MavenPropertiesVirtualFile extends VirtualFile {
  private final String myPath;
  private final VirtualFileSystem myFS;
  private final byte[] myContent;

  public MavenPropertiesVirtualFile(String path, Properties properties, VirtualFileSystem FS) {
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
    return builder.toString().getBytes();
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
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
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
  public InputStream getInputStream() throws IOException {
    return VfsUtilCore.byteStreamSkippingBOM(myContent,this);
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }
}
