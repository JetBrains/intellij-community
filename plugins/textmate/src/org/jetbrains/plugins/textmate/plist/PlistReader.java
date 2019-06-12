package org.jetbrains.plugins.textmate.plist;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface PlistReader {
  Plist read(@NotNull File file) throws IOException;

  Plist read(@NotNull InputStream inputStream) throws IOException;
}
