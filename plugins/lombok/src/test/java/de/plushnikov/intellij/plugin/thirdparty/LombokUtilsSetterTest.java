package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class LombokUtilsSetterTest {

  private static String makeResults(String fieldName, boolean isBoolean) {
    return LombokUtils.toSetterName(AccessorsInfo.DEFAULT, fieldName, isBoolean);
  }

  @Test
  public void testToSetterNames_dValue() {
    String result = makeResults("dValue", false);

    assertThat(result, equalTo("setDValue"));
  }

  @Test
  public void testToSetterNames_Value() {
    String result = makeResults("Value", false);

    assertThat(result, equalTo("setValue"));
  }

  @Test
  public void testToSetterNames_NonBoolean() {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("setMyField"));
  }

  @Test
  public void testToSetterNames_NonBoolean_Uppercase() {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("setMyField"));
  }

  @Test
  public void testToSetterNames_NonBoolean_Uppercase_Multiple() {
    String result = makeResults("MYField", false);

    assertThat(result, equalTo("setMYField"));
  }

  @Test
  public void testToSetterNames_Boolean() {
    String result = makeResults("myField", true);

    assertThat(result, equalTo("setMyField"));
  }

  @Test
  public void testToSetterNames_Boolean_Uppercase() {
    String result = makeResults("MyField", true);

    assertThat(result, equalTo("setMyField"));
  }

  @Test
  public void testToSetterNames_Boolean_is_Lowercase() {
    String result = makeResults("ismyField", true);

    assertThat(result, equalTo("setIsmyField"));
  }

  @Test
  public void testToSetterNames_Boolean_is_Uppercase() {
    String result = makeResults("isMyField", true);

    assertThat(result, equalTo("setMyField"));
  }

  @Test
  public void testToSetterNames_Boolean_IS() {
    String result = makeResults("ISmyField", true);

    assertThat(result, equalTo("setISmyField"));
  }

}
