package com.intellij.sh.parser;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class ShShebangParserUtil {

  private static final List<String> KNOWN_EXTENSIONS = ContainerUtil.list("exe", "bat", "cmd");
  private static final String prefix = "#!";

  private ShShebangParserUtil() {
  }

  @Nullable
  public static String getInterpreter(String shebang) {
    if (shebang != null && shebang.startsWith(prefix)) {
      String path = shebang.substring(prefix.length()).trim();
      int index = path.indexOf(" ");
      String interpreter = index < 0 ? path : path.substring(0, index);
      if (interpreter.equals("/usr/bin/env")) {
        interpreter = path.substring(index + 1);
        index = interpreter.indexOf(" ");
        interpreter = index < 0 ? interpreter : interpreter.substring(0, index);
      }
      if (SystemInfo.isFileSystemCaseSensitive) {
        interpreter = interpreter.toLowerCase(Locale.ENGLISH);
      }
      interpreter = PathUtil.getFileName(interpreter);
      return trimKnownExt(interpreter);
    }
    return null;
  }

  @NotNull
  private static String trimKnownExt(@NotNull String name) {
    String ext = PathUtil.getFileExtension(name);
    if (ext != null && KNOWN_EXTENSIONS.contains(ext)) {
      name = name.substring(0, name.length() - ext.length() - 1);
    }
    return name;
  }

}
