// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  @Deprecated(forRemoval = true)
  public static @Nullable VirtualFile findRelativeFile(String uri, VirtualFile base) {
    return VfsUtilCore.findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(uri), base);
  }

  public static @Nullable VirtualFile findRelative(String uri, @NotNull PsiFileSystemItem base) {
    String location = ExternalResourceManager.getInstance().getResourceLocation(uri, base.getProject());
    VirtualFile file = base.getVirtualFile();
    return VfsUtilCore.findRelativeFile(location, file != null && file.isValid() ? file : null);
  }

  // cannot use UriUtil.SLASH_MATCHER.trimFrom - we don't depend on guava
  public static @NotNull String trimSlashFrom(@NotNull String path) {
    return StringUtil.trimStart(StringUtil.trimEnd(path, "/"), "/");
  }
}