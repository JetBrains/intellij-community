// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.sh.ShTypes;
import com.intellij.sh.psi.ShFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class ShRunner {
  public abstract void run(@NotNull ShFile file);

  public abstract boolean isAvailable(@NotNull Project project);

  @Nullable
  static String getShebangExecutable(@NotNull ShFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && virtualFile.exists()) {
      ASTNode shebang = file.getNode().findChildByType(ShTypes.SHEBANG);
      String prefix = "#!";
      if (shebang != null && shebang.getText().startsWith(prefix)) {
        String path = shebang.getText().substring(prefix.length()).trim();
        File ioFile = new File(path);
        if (ioFile.isAbsolute() && ioFile.canExecute()) {
          return ioFile.getAbsolutePath();
        }
      }
    }
    return null;
  }
}
