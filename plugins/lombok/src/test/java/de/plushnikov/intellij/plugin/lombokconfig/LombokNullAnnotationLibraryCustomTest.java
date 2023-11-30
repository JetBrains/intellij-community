package de.plushnikov.intellij.plugin.lombokconfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LombokNullAnnotationLibraryCustomTest {

  @Test
  void testParseCustom() {
    String valueCustom = "custom:type_use:some.nonNull:some.nullable";

    LombokNullAnnotationLibrary result = LombokNullAnnotationLibraryCustom.parseCustom(valueCustom);

    assertNotNull(result, "The result should not be null");
    assertTrue(result.isTypeUse());
    assertEquals("some.nonNull", result.getNonNullAnnotation());
    assertEquals("some.nullable", result.getNullableAnnotation());
  }

  @Test
  void testParseCustomWithoutType() {
    String valueCustom = "custom:some.nonNull:some.nullable";

    LombokNullAnnotationLibrary result = LombokNullAnnotationLibraryCustom.parseCustom(valueCustom);

    assertNotNull(result, "The result should not be null");
    assertFalse(result.isTypeUse());
    assertEquals("some.nonNull", result.getNonNullAnnotation());
    assertEquals("some.nullable", result.getNullableAnnotation());
  }

  @Test
  void testParseCustomWithoutCustom() {
    String valueCustom = "type_use:some.nonNull:some.nullable";

    LombokNullAnnotationLibrary result = LombokNullAnnotationLibraryCustom.parseCustom(valueCustom);

    assertNull(result, "The result should be null as the input string doesn't start with 'custom:'");
  }

  @Test
  void testParseCustomWithoutValidAnnotations() {
    String valueCustom = "custom:type_use:some%nonNull:some.1nullable";

    LombokNullAnnotationLibrary result = LombokNullAnnotationLibraryCustom.parseCustom(valueCustom);

    assertNull(result, "The result should be null as the annotations are not valid");
  }
}