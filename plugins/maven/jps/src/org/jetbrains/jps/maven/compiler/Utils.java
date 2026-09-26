// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Utils {
  private Utils() { }


  public static <D extends BuildRootDescriptor, T extends BuildTarget<D>> Map<D, List<File>> collectFiles(DirtyFilesHolder<D, T> holder)
    throws IOException {
    final Map<D, List<File>> files = new HashMap<>();

    holder.processDirtyFiles(new FileProcessor<D, T>() {

      @Override
      public boolean apply(@NotNull T t, @NotNull File file, @NotNull D rd) throws IOException {
        List<File> fileList = files.get(rd);
        if (fileList == null) {
          fileList = new ArrayList<>();
          files.put(rd, fileList);
        }

        fileList.add(file);
        return true;
      }
    });
    return files;
  }
}
