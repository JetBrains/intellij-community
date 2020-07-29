// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.util;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Consumer;

import java.io.File;


@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public final class SPFileUtil {

  public static void processFilesRecursively(final String rootPath, final Consumer<? super String> consumer) {
    final File rootFile = new File(rootPath);
    if (rootFile.exists() && rootFile.isDirectory()) {
      FileUtil.processFilesRecursively(rootFile, file -> {
        if (!file.isDirectory()) {
          final String path = file.getPath();
          if (path.endsWith(".dic")) {
            consumer.consume(path);
          }
        }
        return true;
      });
    }
  }
}
