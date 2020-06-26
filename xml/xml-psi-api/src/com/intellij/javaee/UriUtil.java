// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UriUtil {
  private UriUtil() {}

  /**
   * @deprecated use {@link #findRelative(String, PsiFileSystemItem)}
   */
  @Deprecated
  @Nullable
  public static VirtualFile findRelativeFile(String uri, VirtualFile base) {
    return VfsUtilCore.findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(uri), base);
  }

  @Nullable
  public static VirtualFile findRelative(String uri, @NotNull PsiFileSystemItem base) {
    String location = ExternalResourceManager.getInstance().getResourceLocation(uri, base.getProject());
    VirtualFile file = base.getVirtualFile();
    return VfsUtilCore.findRelativeFile(location, file != null && file.isValid() ? file : null);
  }

  // cannot use UriUtil.SLASH_MATCHER.trimFrom - we don't depend on guava
  @NotNull
  public static String trimSlashFrom(@NotNull String path) {
    return StringUtil.trimStart(StringUtil.trimEnd(path, "/"), "/");
  }
}