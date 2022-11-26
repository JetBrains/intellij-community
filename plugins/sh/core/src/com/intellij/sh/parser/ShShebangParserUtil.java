// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.parser;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.sh.ShTypes;
import com.intellij.sh.psi.ShFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ShShebangParserUtil {
  @NonNls private static final List<String> KNOWN_EXTENSIONS = Arrays.asList("exe", "bat", "cmd");
  private static final String PREFIX = "#!";

  private ShShebangParserUtil() {
  }

  @Nullable
  public static String getShebangExecutable(@NotNull ShFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && virtualFile.exists()) {
      ASTNode shebang = file.getNode().findChildByType(ShTypes.SHEBANG);
      String prefix = "#!";
      if (shebang != null && shebang.getText().startsWith(prefix)) {
        return shebang.getText().substring(prefix.length()).trim();
      }
    }
    return null;
  }

  @NotNull
  public static @NlsSafe String getInterpreter(@NotNull ShFile file,
                                               @NotNull List<@NlsSafe String> knownShells,
                                               @NlsSafe @NotNull String defaultShell) {
    String shebang = ApplicationManager.getApplication().isDispatchThread() ? file.findShebang()
                                                                            : ReadAction.compute(() -> file.findShebang());
    String detectedInterpreter = shebang != null ? detectInterpreter(shebang) : null;
    return detectedInterpreter != null && knownShells.contains(detectedInterpreter) ? detectedInterpreter : defaultShell;
  }

  @Nullable
  public static String detectInterpreter(@Nullable String shebang) {
    if (shebang == null || !shebang.startsWith(PREFIX)) return null;

    String interpreterPath = getInterpreterPath(shebang.substring(PREFIX.length()).trim());
    String lowerCasePath = SystemInfo.isFileSystemCaseSensitive ? interpreterPath.toLowerCase(Locale.ENGLISH) : interpreterPath;

    return trimKnownExt(PathUtil.getFileName(lowerCasePath));
  }

  @NotNull
  private static String getInterpreterPath(@NotNull String shebang) {
    int index = shebang.indexOf(" ");
    @NonNls String possiblePath = index < 0 ? shebang : shebang.substring(0, index);
    if (!possiblePath.equals("/usr/bin/env")) return possiblePath;

    String interpreterPath = shebang.substring(index + 1);
    index = interpreterPath.indexOf(" ");
    return index < 0 ? interpreterPath : interpreterPath.substring(0, index);
  }

  @NotNull
  private static String trimKnownExt(@NotNull String name) {
    String ext = PathUtil.getFileExtension(name);
    return ext != null && KNOWN_EXTENSIONS.contains(ext) ? name.substring(0, name.length() - ext.length() - 1) : name;
  }
}
