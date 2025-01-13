package org.jetbrains.plugins.textmate.plist;

import org.jetbrains.annotations.NotNull;

import java.io.*;

public interface PlistReader {
  /**
   * @deprecated use {@link #read(InputStream)}
   */
  @Deprecated
  default Plist read(@NotNull File file) throws IOException {
    try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
      return read(in);
    }
  }

  Plist read(@NotNull InputStream inputStream) throws IOException;
}
