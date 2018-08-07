// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.util.io.FileUtil;

public class MavenEnvironmentRegistrar implements BaseComponent {
  private static final String MAVEN_REPOSITORY = "MAVEN_REPOSITORY";

  @Override
  public void initComponent() {
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

    macros.setMacro(MAVEN_REPOSITORY, repository);
  }
}
