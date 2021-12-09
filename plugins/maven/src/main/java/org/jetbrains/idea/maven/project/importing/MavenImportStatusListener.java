// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public interface MavenImportStatusListener {
  ExtensionPointName<MavenImportStatusListener> EP_NAME =
    ExtensionPointName.create("org.jetbrains.idea.maven.import.status.listener");

  void importFinished(@NotNull MavenImportedContext context);
}
