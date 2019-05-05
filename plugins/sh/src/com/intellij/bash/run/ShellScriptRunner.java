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

  public abstract void run(@NotNull ShFile bashFile);

  public abstract boolean isAvailable(@NotNull Project project);

  @Nullable
  public static String getShebangExecutable(@NotNull ShFile bashFile) {
    VirtualFile virtualFile = bashFile.getVirtualFile();
    if (virtualFile != null && virtualFile.exists()) {
      ASTNode shebang = bashFile.getNode().findChildByType(ShTokenTypes.SHEBANG);
      String prefix = "#!";
      if (shebang != null && shebang.getText().startsWith(prefix)) {
        String path = shebang.getText().substring(prefix.length()).trim();
        File file = new File(path);
        if (file.isAbsolute() && file.canExecute()) {
          return file.getAbsolutePath();
        }
      }
    }
    return null;
  }
}
