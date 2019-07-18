package org.jetbrains.plugins.textmate.plist;

import org.jetbrains.annotations.NotNull;

public class XmlPlistReaderTest extends PlistReaderTestCase {
  @Override
  protected PlistReader createReader() {
    return new XmlPlistReader();
  }

  @Override
  @NotNull
  protected String prepareText(String string) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
           "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
           "<plist version=\"1.0\">\n" +
           string +
           "</plist>";
  }
}
