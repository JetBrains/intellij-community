package org.jetbrains.plugins.textmate.plist;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;

import static org.jetbrains.plugins.textmate.plist.PListValue.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

abstract public class PlistReaderTestCase {

  private PlistReader myReader;

  protected abstract PlistReader createReader();

  @Before
  public void setUp() {
    myReader = createReader();
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void getStringMethod() throws Exception {
    Plist plist = read("<dict><key>someKey</key><string>someValue</string></dict>");
    assertEquals("someValue", plist.getPlistValue("someKey").getString());
    assertEquals("default", plist.getPlistValue("unknown", "default").getString());
  }

  @Test
  public void parseString() throws Exception {
    Plist plist = read("<dict><key>someKey</key><string>someValue</string><key>escape</key><string>&gt;</string></dict>");
    assertEquals(2, plist.entries().size());
    assertEquals(string("someValue"), plist.getPlistValue("someKey"));
    assertEquals(string(">"), plist.getPlistValue("escape"));
    assertEquals(string("default"), plist.getPlistValue("unknown", "default"));
    assertNull(plist.getPlistValue("unknown"));
  }

  @Test
  public void parseBoolean() throws Exception {
    Plist plist = read("<dict><key>true</key><true/><key>false</key><false/></dict>");
    assertEquals(2, plist.entries().size());
    assertEquals(bool(true), plist.getPlistValue("true"));
    assertEquals(bool(false), plist.getPlistValue("false"));
    assertNull(plist.getPlistValue("unknown"));
    assertEquals(bool(true), plist.getPlistValue("unknown", true));
    assertEquals(bool(false), plist.getPlistValue("unknown", false));
  }

  @Test
  public void parseInteger() throws Exception {
    Plist plist = read("<dict><key>int</key><integer>124</integer></dict>");
    assertEquals(1, plist.entries().size());
    assertEquals(integer(Long.valueOf(124)), plist.getPlistValue("int"));
    assertNull(plist.getPlistValue("unknown"));
    assertEquals(integer(Long.valueOf(124)), plist.getPlistValue("unknown", Long.valueOf(124)));
  }

  @Test
  public void parseReal() throws Exception {
    Plist plist = read("<dict><key>real</key><real>145.3</real></dict>");
    assertEquals(1, plist.entries().size());
    assertEquals(real(Double.valueOf(145.3)), plist.getPlistValue("real"));
    assertEquals(real(Double.valueOf(120.0)), plist.getPlistValue("unknown", 120.0));
    assertNull(plist.getPlistValue("unknown"));
  }

  @Test
  public void parseDate() throws Exception {
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.MILLISECOND, 0); //Plist doesn't respect to ms
    Date date = calendar.getTime();

    Plist plist = read("<dict><key>date</key><date>" + Plist.dateFormatter().format(date) + "</date></dict>");
    assertEquals(1, plist.entries().size());
    assertEquals(date(date), plist.getPlistValue("date"));
    assertNull(plist.getPlistValue("unknown"));
  }

  @Test
  public void parseArray() throws Exception {
    Plist plist = read("<dict><key>list</key><array><string>alex</string><string>zolotov</string><integer>42</integer></array></dict>");
    assertEquals(
      Plist.fromMap(ContainerUtil.newHashMap(Pair.create("list", array(string("alex"), string("zolotov"), integer(Long.valueOf(42)))))),
      plist);
  }

  @Test
  public void parseInnerDict() throws Exception {
    Plist plist = read("<dict><key>dict</key><dict>" +
                       "<key>name</key><string>alex</string>" +
                       "<key>lastname</key><string>zolotov</string>" +
                       "<key>age</key><integer>22</integer>" +
                       "</dict></dict>");
    assertEquals(Plist.fromMap(
      ContainerUtil.newHashMap(Pair.create("dict", dict(Plist.fromMap(ContainerUtil.newHashMap(
        Pair.create("name", string("alex")),
        Pair.create("lastname", string("zolotov")),
        Pair.create("age", integer(Long.valueOf(22)))
      )))))), plist);
  }

  @Test
  public void plistWithoutDictRoot() throws Exception {
    Plist plist = read("<key>someKey</key><string>someValue</string>");
    assertEquals(Plist.EMPTY_PLIST, plist);
  }

  @Test
  public void invalidPlist() throws Exception {
    Plist plist = read("<dict><key>someKey</key><string>someValue</string>" +
                       "<string>withoutKey</string>" +
                       "<key>someKey2</key>" +
                       "<string>someValue2</string>" +
                       "</dict>");
    assertEquals(Plist.fromMap(ContainerUtil.newLinkedHashMap(
      Pair.create("someKey", string("someValue")),
      Pair.create("someKey2", string("someValue2")))), plist);
  }

  protected Plist read(String string) throws IOException {
    return myReader.read(new ByteArrayInputStream(prepareText(string).getBytes(StandardCharsets.UTF_8)));
  }

  @NotNull
  protected abstract String prepareText(String string);
}
