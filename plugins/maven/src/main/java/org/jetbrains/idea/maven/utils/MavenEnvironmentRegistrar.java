// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.util.io.FileUtil;

public class MavenEnvironmentRegistrar implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    registerPathVariable();
  }

  private static void registerPathVariable() {
    String repository = MavenUtil.resolveLocalRepository(null, null, null).getAbsolutePath();
    PathMacros macros = PathMacros.getInstance();

    for (String each : macros.getAllMacroNames()) {
      String path = macros.getValue(each);
      if (path != null && FileUtil.pathsEqual(repository, path)) {
        return;
      }
    }

    macros.setMacro(PathMacrosImpl.MAVEN_REPOSITORY, repository);
  }
}
