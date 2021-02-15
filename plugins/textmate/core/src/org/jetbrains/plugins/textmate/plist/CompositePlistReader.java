package org.jetbrains.plugins.textmate.plist;

import org.jetbrains.annotations.NotNull;

import java.io.*;

public class CompositePlistReader implements PlistReader {
  private final PlistReader myJsonReader = new JsonPlistReader();
  private final PlistReader myXmlReader = new XmlPlistReader();

  @Override
  public Plist read(@NotNull File file) throws IOException {
    return read(new BufferedInputStream(new FileInputStream(file)));
  }

  @Override
  public Plist read(@NotNull InputStream inputStream) throws IOException {
    inputStream.mark(256);
    int symbol = inputStream.read();
    int tries = 0;
    while (symbol > 0 && Character.isWhitespace(symbol) && tries < 255) {
      symbol = inputStream.read();
      tries++;
    }
    inputStream.reset();
    if (symbol == '{') {
      return myJsonReader.read(inputStream);
    }
    if (symbol == '<') {
      return myXmlReader.read(inputStream);
    }
    throw new IOException("Unknown bundle type, first char: " + (char)symbol);
  }
}
