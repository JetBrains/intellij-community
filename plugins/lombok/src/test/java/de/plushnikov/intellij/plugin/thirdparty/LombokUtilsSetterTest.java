package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class LombokUtilsSetterTest {
  private static final AccessorsInfo DEFAULT_ACCESSORS = AccessorsInfo.build(false, false, false);

  private String makeResults(String fieldName, boolean isBoolean) {
    String lombokResult = LombokHandlerUtil.toSetterName(DEFAULT_ACCESSORS, fieldName, isBoolean);
    String result = LombokUtils.toSetterName(DEFAULT_ACCESSORS, fieldName, isBoolean);

    assertThat(result, is(lombokResult));
    return result;
  }

  @Test
  public void testToSetterNames_dValue() throws Exception {
    String result = makeResults("dValue", false);

    assertThat(result, equalTo("setDValue"));
  }

  @Test
  public void testToSetterNames_Value() throws Exception {
    String result = makeResults("Value", false);

    assertThat(result, equalTo("setValue"));
  }

  @Test
  public void testToSetterNames_NonBoolean() throws Exception {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("setMyField"));
  }

  @Test
  public void testToSetterNames_NonBoolean_Uppercase() throws Exception {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("setMyField"));
  }

  @Test
  public void testToSetterNames_NonBoolean_Uppercase_Multiple() throws Exception {
    String result = makeResults("MYField", false);

    assertThat(result, equalTo("setMYField"));
  }

  @Test
  public void testToSetterNames_Boolean() throws Exception {
    String result = makeResults("myField", true);

    assertThat(result, equalTo("setMyField"));
  }

  @Test
  public void testToSetterNames_Boolean_Uppercase() throws Exception {
    String result = makeResults("MyField", true);

    assertThat(result, equalTo("setMyField"));
  }

  @Test
  public void testToSetterNames_Boolean_is_Lowercase() throws Exception {
    String result = makeResults("ismyField", true);

    assertThat(result, equalTo("setIsmyField"));
  }

  @Test
  public void testToSetterNames_Boolean_is_Uppercase() throws Exception {
    String result = makeResults("isMyField", true);

    assertThat(result, equalTo("setMyField"));
  }

  @Test
  public void testToSetterNames_Boolean_IS() throws Exception {
    String result = makeResults("ISmyField", true);

    assertThat(result, equalTo("setISmyField"));
  }

}