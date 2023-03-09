package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class LombokUtilsAllSetterTest {

  private final List<String> result = new ArrayList<>();

  private void makeResults(String fieldName, boolean isBoolean) {
    result.clear();

    result.addAll(LombokUtils.toAllSetterNames(AccessorsInfo.DEFAULT, fieldName, isBoolean));
  }

  @Test
  public void testToAllSetterNames_NonBoolean() {
    makeResults("myField", false);

    assertThat(result, is(Collections.singletonList("setMyField")));
  }

  @Test
  public void testToAllSetterNames_NonBoolean_Uppercase() {
    makeResults("myField", false);

    assertThat(result, is(Collections.singletonList("setMyField")));
  }

  @Test
  public void testToAllSetterNames_NonBoolean_Uppercase_Multiple() {
    makeResults("MYField", false);

    assertThat(result, is(Collections.singletonList("setMYField")));
  }

  @Test
  public void testToAllSetterNames_Boolean() {
    makeResults("myField", true);

    assertThat(result, is(Collections.singletonList("setMyField")));
  }

  @Test
  public void testToAllSetterNames_Boolean_Uppercase() {
    makeResults("MyField", true);

    assertThat(result, is(Collections.singletonList("setMyField")));
  }

  @Test
  public void testToAllSetterNames_Boolean_is_Lowercase() {
    makeResults("ismyField", true);

    assertThat(result, is(Collections.singletonList("setIsmyField")));
  }

  @Test
  public void testToAllSetterNames_Boolean_is_Uppercase() {
    makeResults("isMyField", true);

    assertThat(result, is(Arrays.asList("setMyField", "setIsMyField")));
  }

  @Test
  public void testToAllSetterNames_Boolean_IS() {
    makeResults("ISmyField", true);

    assertThat(result, is(Collections.singletonList("setISmyField")));
  }

}
