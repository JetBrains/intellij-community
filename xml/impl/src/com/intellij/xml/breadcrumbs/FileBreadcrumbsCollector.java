// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to replace the mechanism of gathering breadcrumbs for a file.
 */
public abstract class FileBreadcrumbsCollector {
  public static final ExtensionPointName<FileBreadcrumbsCollector> EP_NAME = ExtensionPointName.create("com.intellij.fileBreadcrumbsCollector");

  /**
   * Checks if this collector handles the given file.
   */
  public abstract boolean handlesFile(@NotNull VirtualFile virtualFile);

  /**
   * Adds event listeners required to redraw the breadcrumbs when the contents of the file changes.
   * @param file the file to watch
   * @param disposable the disposable used to detach listeners when the file is closed.
   * @param changesHandler the callback to be called when any changes are detected.
   */
  public abstract void watchForChanges(@NotNull VirtualFile file, @NotNull Disposable disposable, @NotNull Runnable changesHandler);

  public abstract void updateCrumbs(@NotNull VirtualFile virtualFile,
                                    @NotNull Editor editor,
                                    ProgressIndicator progressIndicator,
                                    @NotNull Consumer<Iterable<Crumb>> consumer);
}
