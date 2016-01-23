package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class LombokUtilsPrefixedFluentTest {

  private static final AccessorsInfo DEFAULT_ACCESSORS = AccessorsInfo.build(true, false, false, "m", "");

  private String makeResults(String fieldName, boolean isBoolean) {
    String lombokResult = LombokHandlerUtil.toGetterName(DEFAULT_ACCESSORS, fieldName, isBoolean);
    String result = LombokUtils.toGetterName(DEFAULT_ACCESSORS, fieldName, isBoolean);

    assertThat(result, is(lombokResult));
    return result;
  }

  @Test
  public void testToGetterNames_mValue() throws Exception {
    String result = makeResults("mValue", false);

    assertThat(result, equalTo("value"));
  }

  @Test
  public void testToGetterNames_Value() throws Exception {
    String result = makeResults("Value", false);

    assertThat(result, equalTo("Value"));
  }

  @Test
  public void testToGetterNames_NonBoolean() throws Exception {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("myField"));
  }

  @Test
  public void testToGetterNames_NonBoolean_Uppercase() throws Exception {
    String result = makeResults("mYField", false);

    assertThat(result, equalTo("yField"));
  }

  @Test
  public void testToGetterNames_NonBoolean_Uppercase_Multiple() throws Exception {
    String result = makeResults("MYField", false);

    assertThat(result, equalTo("MYField"));
  }

  @Test
  public void testToGetterNames_Boolean() throws Exception {
    String result = makeResults("myField", true);

    assertThat(result, equalTo("myField"));
  }

  @Test
  public void testToGetterNames_Boolean_Uppercase() throws Exception {
    String result = makeResults("MYField", true);

    assertThat(result, equalTo("MYField"));
  }

  @Test
  public void testToGetterNames_Boolean_is_Lowercase() throws Exception {
    String result = makeResults("ismyField", true);

    assertThat(result, equalTo("ismyField"));
  }

  @Test
  public void testToGetterNames_Boolean_is_Uppercase() throws Exception {
    String result = makeResults("isMyField", true);

    assertThat(result, equalTo("isMyField"));
  }

  @Test
  public void testToGetterNames_Boolean_IS() throws Exception {
    String result = makeResults("ISmyField", true);

    assertThat(result, equalTo("ISmyField"));
  }

}