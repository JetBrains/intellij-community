package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class LombokUtilsGetterTest {

  private static String makeResults(String fieldName, boolean isBoolean) {
    String lombokResult = LombokHandlerUtil.toGetterName(AccessorsInfo.DEFAULT, fieldName, isBoolean);
    String result = LombokUtils.toGetterName(AccessorsInfo.DEFAULT, fieldName, isBoolean);

    assertThat(result, is(lombokResult));
    return result;
  }

  @Test
  public void testToGetterNames_dValue() {
    String result = makeResults("dValue", false);

    assertThat(result, equalTo("getDValue"));
  }

  @Test
  public void testToGetterNames_Value() {
    String result = makeResults("Value", false);

    assertThat(result, equalTo("getValue"));
  }

  @Test
  public void testToGetterNames_NonBoolean() {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("getMyField"));
  }

  @Test
  public void testToGetterNames_NonBoolean_Uppercase() {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("getMyField"));
  }

  @Test
  public void testToGetterNames_NonBoolean_Uppercase_Multiple() {
    String result = makeResults("MYField", false);

    assertThat(result, equalTo("getMYField"));
  }

  @Test
  public void testToGetterNames_Boolean() {
    String result = makeResults("myField", true);

    assertThat(result, equalTo("isMyField"));
  }

  @Test
  public void testToGetterNames_Boolean_Uppercase() {
    String result = makeResults("MyField", true);

    assertThat(result, equalTo("isMyField"));
  }

  @Test
  public void testToGetterNames_Boolean_is_Lowercase() {
    String result = makeResults("ismyField", true);

    assertThat(result, equalTo("isIsmyField"));
  }

  @Test
  public void testToGetterNames_Boolean_is_Uppercase() {
    String result = makeResults("isMyField", true);

    assertThat(result, equalTo("isMyField"));
  }

  @Test
  public void testToGetterNames_Boolean_IS() {
    String result = makeResults("ISmyField", true);

    assertThat(result, equalTo("isISmyField"));
  }

}
