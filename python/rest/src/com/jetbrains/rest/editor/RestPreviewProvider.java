// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RestPreviewProvider {
  public static final ExtensionPointName<RestPreviewProvider> EP_NAME =
    ExtensionPointName.create("restructured.text.html.preview.provider");

  @Nullable
  public abstract String toHtml(String text, VirtualFile virtualFile, Project project);

  @NotNull
  public static RestPreviewProvider[] getProviders() {
    return EP_NAME.getExtensions();
  }

}
