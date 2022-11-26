package de.plushnikov.intellij.plugin.thirdparty;

import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class LombokUtilsAllGetterTest {

  private final List<String> lombokResult = new ArrayList<>();
  private final List<String> result = new ArrayList<>();

  private void makeResults(String fieldName, boolean isBoolean, AccessorsInfo accessorsInfo) {
    lombokResult.clear();
    result.clear();

    lombokResult.addAll(LombokHandlerUtil.toAllGetterNames(accessorsInfo, fieldName, isBoolean));
    result.addAll(LombokUtils.toAllGetterNames(accessorsInfo, fieldName, isBoolean));

    assertThat(result, is(lombokResult));
  }

  @Test
  public void testToAllGetterNames_NonBoolean() {
    makeResults("myField", false, AccessorsInfo.DEFAULT);

    assertThat(result, is(Collections.singletonList("getMyField")));
  }

  @Test
  public void testToAllGetterNames_NonBoolean_Uppercase() {
    makeResults("myField", false, AccessorsInfo.DEFAULT);

    assertThat(result, is(Collections.singletonList("getMyField")));
  }

  @Test
  public void testToAllGetterNames_NonBoolean_Uppercase_Multiple() {
    makeResults("MYField", false, AccessorsInfo.DEFAULT);

    assertThat(result, is(Collections.singletonList("getMYField")));
  }

  @Test
  public void testToAllGetterNames_Boolean() {
    makeResults("myField", true, AccessorsInfo.DEFAULT);

    assertThat(result, is(Arrays.asList("getMyField", "isMyField")));
  }

  @Test
  public void testToAllGetterNames_Boolean_Uppercase() {
    makeResults("MyField", true, AccessorsInfo.DEFAULT);

    assertThat(result, is(Arrays.asList("getMyField", "isMyField")));
  }

  @Test
  public void testToAllGetterNames_Boolean_is_Lowercase() {
    makeResults("ismyField", true, AccessorsInfo.DEFAULT);

    assertThat(result, is(Arrays.asList("isIsmyField", "getIsmyField")));
  }

  @Test
  public void testToAllGetterNames_Boolean_is_Uppercase() {
    makeResults("isMyField", true, AccessorsInfo.DEFAULT);

    assertThat(result, is(Arrays.asList("isIsMyField", "getIsMyField", "getMyField", "isMyField")));
  }

  @Test
  public void testToAllGetterNames_Boolean_IS() {
    makeResults("ISmyField", true, AccessorsInfo.DEFAULT);

    assertThat(result, is(Arrays.asList("getISmyField", "isISmyField")));
  }


  @Test
  public void testToAllGetterNames_NonBoolean_Fluent() {
    makeResults("myField", false, AccessorsInfo.DEFAULT.withFluent(true));

    assertThat(result, is(Collections.singletonList("myField")));
  }
}
