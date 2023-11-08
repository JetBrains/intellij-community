// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.PROJECT)
public final class MavenDisposable implements Disposable {
  @Override
  public void dispose() {
  }

  public static MavenDisposable getInstance(Project project) {
    return project.getService(MavenDisposable.class);
  }
}
