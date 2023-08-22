package org.jetbrains.plugins.textmate.plist;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class CompositePlistReaderTest {
  @Test
  public void parseJson() throws IOException {
    Plist read = new CompositePlistReader().read(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
    assertNotNull(read);
  }

  @Test
  public void parseJsonWithNewline() throws IOException {
    Plist read = new CompositePlistReader().read(new ByteArrayInputStream("\n\n{}".getBytes(StandardCharsets.UTF_8)));
    assertNotNull(read);
  }

  @Test
  public void parseXml() throws IOException {
    Plist read = new CompositePlistReader().read(new ByteArrayInputStream(("""
                                                                             <?xml version="1.0" encoding="UTF-8"?>
                                                                             <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                                                                             <plist version="1.0">
                                                                             <dict><key>someKey</key><string>someValue</string></dict></plist>""").getBytes(StandardCharsets.UTF_8)));
    assertNotNull(read);
  }

  @Test
  public void parseUnknown() {
    try {
      new CompositePlistReader().read(new ByteArrayInputStream("!!!".getBytes(StandardCharsets.UTF_8)));
      fail("");
    } catch (IOException ignored) { }
  }
}
