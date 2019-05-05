package com.intellij.bash.run;

import com.intellij.bash.lexer.ShTokenTypes;
import com.intellij.bash.psi.ShFile;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class ShellScriptRunner {
  public abstract void run(@NotNull ShFile file);

  public abstract boolean isAvailable(@NotNull Project project);

  @Nullable
  public static String getShebangExecutable(@NotNull ShFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && virtualFile.exists()) {
      ASTNode shebang = file.getNode().findChildByType(ShTokenTypes.SHEBANG);
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
