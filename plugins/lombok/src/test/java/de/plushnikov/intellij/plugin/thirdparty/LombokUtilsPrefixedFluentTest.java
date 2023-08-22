package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class LombokUtilsPrefixedFluentTest {

  private static final AccessorsInfo ACCESSORS = AccessorsInfo.build(true, false, false, false,
                                                                     CapitalizationStrategy.defaultValue(), "m", "");

  private static String makeResults(String fieldName, boolean isBoolean) {
    return LombokUtils.toGetterName(ACCESSORS, fieldName, isBoolean);
  }

  @Test
  public void testToGetterNames_mValue() {
    String result = makeResults("mValue", false);

    assertThat(result, equalTo("value"));
  }

  @Test
  public void testToGetterNames_Value() {
    String result = makeResults("Value", false);

    assertThat(result, equalTo("Value"));
  }

  @Test
  public void testToGetterNames_NonBoolean() {
    String result = makeResults("myField", false);

    assertThat(result, equalTo("myField"));
  }

  @Test
  public void testToGetterNames_NonBoolean_Uppercase() {
    String result = makeResults("mYField", false);

    assertThat(result, equalTo("yField"));
  }

  @Test
  public void testToGetterNames_NonBoolean_Uppercase_Multiple() {
    String result = makeResults("MYField", false);

    assertThat(result, equalTo("MYField"));
  }

  @Test
  public void testToGetterNames_Boolean() {
    String result = makeResults("myField", true);

    assertThat(result, equalTo("myField"));
  }

  @Test
  public void testToGetterNames_Boolean_Uppercase() {
    String result = makeResults("MYField", true);

    assertThat(result, equalTo("MYField"));
  }

  @Test
  public void testToGetterNames_Boolean_is_Lowercase() {
    String result = makeResults("ismyField", true);

    assertThat(result, equalTo("ismyField"));
  }

  @Test
  public void testToGetterNames_Boolean_is_Uppercase() {
    String result = makeResults("isMyField", true);

    assertThat(result, equalTo("isMyField"));
  }

  @Test
  public void testToGetterNames_Boolean_IS() {
    String result = makeResults("ISmyField", true);

    assertThat(result, equalTo("ISmyField"));
  }

}
