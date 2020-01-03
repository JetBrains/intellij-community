// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.PathMacroContributor;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.util.io.FileUtil;

final class MavenPathMacroContributor implements PathMacroContributor {
  @Override
  public void registerPathMacros(PathMacros macros) {
    String repository = MavenUtil.resolveLocalRepository(null, null, null).getAbsolutePath();

    for (String each : macros.getAllMacroNames()) {
      String path = macros.getValue(each);
      if (path != null && FileUtil.pathsEqual(repository, path)) {
        return;
      }
    }

    macros.setMacro(PathMacrosImpl.MAVEN_REPOSITORY, repository);
  }
}
