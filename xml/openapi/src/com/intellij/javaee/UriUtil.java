/*
 * @author max
 */
package com.intellij.javaee;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UriUtil {
  private UriUtil() {}

  /** @see #findRelativeFile(String, com.intellij.psi.PsiFile) */
  @Deprecated
  @Nullable
  public static VirtualFile findRelativeFile(String uri, VirtualFile base) {
    return VfsUtil.findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(uri), base);
  }

  @Nullable
  public static VirtualFile findRelative(String uri, @NotNull PsiFileSystemItem base) {
    String location = ExternalResourceManager.getInstance().getResourceLocation(uri, base.getProject());
    return VfsUtil.findRelativeFile(location, base.getVirtualFile());
  }
}