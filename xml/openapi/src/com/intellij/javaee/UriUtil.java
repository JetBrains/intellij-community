/*
 * @author max
 */
package com.intellij.javaee;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class UriUtil {
  private UriUtil() {}

  @Nullable
  public static VirtualFile findRelativeFile(String uri, VirtualFile base) {
    return VfsUtil.findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(uri), base);
  }
}