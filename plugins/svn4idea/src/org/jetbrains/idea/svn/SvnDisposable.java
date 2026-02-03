// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.PROJECT)
public final class SvnDisposable implements Disposable {
  @Override
  public void dispose() {
  }

  public static SvnDisposable getInstance(Project project) {
    return project.getService(SvnDisposable.class);
  }
}
