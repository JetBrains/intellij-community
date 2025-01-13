package org.jetbrains.plugins.textmate.plist;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.jetbrains.plugins.textmate.plist.PListValue.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JsonPlistReaderTest {

  @Test
  public void parseArray() {
    Plist plist = read("{list:[\"alex\",\"zolotov\",42]}");
    HashMap<String, PListValue> map = new HashMap<>() {{
      put("list", array(string("alex"), string("zolotov"), integer(Integer.valueOf(42))));
    }};
    assertEquals(Plist.fromMap(map), plist);
  }

  @Test
  public void getStringMethod() {
    Plist plist = read("{someKey: \"someValue\"}");
    assertEquals("someValue", plist.getPlistValue("someKey").getString());
    assertEquals("default", plist.getPlistValue("unknown", "default").getString());
  }

  @Test
  public void parseString() {
    Plist plist = read("{someKey: \"someValue\",anotherKey: \">\"}");
    assertEquals(2, plist.entries().size());
    assertEquals(string("someValue"), plist.getPlistValue("someKey"));
    assertEquals(string(">"), plist.getPlistValue("anotherKey"));
    assertEquals(string("default"), plist.getPlistValue("unknown", "default"));
    assertNull(plist.getPlistValue("unknown"));
  }

  @Test
  public void parseBoolean() {
    Plist plist = read("{true: true,false: false}");
    assertEquals(2, plist.entries().size());
    assertEquals(bool(true), plist.getPlistValue("true"));
    assertEquals(bool(false), plist.getPlistValue("false"));
    assertNull(plist.getPlistValue("unknown"));
    assertEquals(bool(true), plist.getPlistValue("unknown", true));
    assertEquals(bool(false), plist.getPlistValue("unknown", false));
  }

  @Test
  public void parseInteger() {
    Plist plist = read("{int: 124}");
    assertEquals(1, plist.entries().size());
    assertEquals(integer(Integer.valueOf(124)), plist.getPlistValue("int"));
    assertNull(plist.getPlistValue("unknown"));
    assertEquals(integer(Integer.valueOf(124)), plist.getPlistValue("unknown", Integer.valueOf(124)));
  }

  @Test
  public void parseReal() {
    Plist plist = read("{real: 145.3}");
    assertEquals(1, plist.entries().size());
    assertEquals(real(Double.valueOf(145.3)), plist.getPlistValue("real"));
    assertEquals(real(Double.valueOf(120.0)), plist.getPlistValue("unknown", 120.0));
    assertNull(plist.getPlistValue("unknown"));
  }


  @Test
  public void parseInnerDict() {
    Plist plist = read("{dict: {name: \"alex\",lastname: \"zolotov\",age: 22}}");
    HashMap<String, PListValue> inner = new HashMap<>() {{
      put("name", string("alex"));
      put("lastname", string("zolotov"));
      put("age", integer(Integer.valueOf(22)));
    }};
    HashMap<String, PListValue> map = new HashMap<>() {{
      put("dict", dict(Plist.fromMap(inner)));
    }};
    assertEquals(Plist.fromMap(map), plist);
  }

  protected Plist read(String string) {
    return new JsonPlistReader().read(new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)));
  }
}
