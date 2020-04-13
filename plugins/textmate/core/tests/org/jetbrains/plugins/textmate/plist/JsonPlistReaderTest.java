package org.jetbrains.plugins.textmate.plist;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.jetbrains.plugins.textmate.plist.PListValue.*;
import static org.junit.Assert.assertEquals;

public class JsonPlistReaderTest extends PlistReaderTestCase {
  @Override
  protected PlistReader createReader() {
    return new JsonPlistReader();
  }

  @Override
  @Test
  public void parseArray() throws Exception {
    Plist plist = read("{list:['alex','zolotov',42]}");
    assertEquals(
      Plist.fromMap(ContainerUtil.newHashMap(Pair.create("list", array(string("alex"), string("zolotov"), integer(Long.valueOf(42)))))),
      plist);
  }


  @Override
  public void parseDate() {
    // not supported in JSON
  }

  @Override
  public void plistWithoutDictRoot() {
    // not supported in JSON
  }

  @Override
  public void invalidPlist() {
    // not supported in JSON
  }

  @NotNull
  @Override
  protected String prepareText(String string) {
    return StringUtil.unescapeXmlEntities(string.replace("<dict><key>", "{").replace("</dict>", "}").
                  replace("<key>", ",").replace("</key>", ": ").
                  replace("<integer>", "").replace("</integer>", "").
                  replace("<real>", "").replace("</real>", "").
                  replace("<string>", "\"").replace("</string>", "\"").
                  replace("<array>", "[").replace("<array>", "]").
                  replace("<true/>", "true").replace("<false/>", "false"));
  }
}
