/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties;

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesUtilTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testBaseNameWithoutLocale() {
    assertBaseNameEquals("property-file.properties", "property-file");
  }

  public void testBaseNameWithLocale() {
    assertBaseNameEquals("property-file_en._fr.asd_ru.properties", "property-file_en._fr.asd");
  }

  public void testBaseNameWithoutLocaleWithAdditionalExtension() {
    assertBaseNameEquals("property-file.utf8.properties", "property-file.utf8");
  }

  public void testBaseNameWithLocaleWithAdditionalExtension() {
    assertBaseNameEquals("property-file_fr.file_en.utf8.properties", "property-file_fr.file.utf8");
  }

  public void testBaseNameWithLongLocale() {
    assertBaseNameEquals("property_latin.properties", "property_latin");
  }

  public void testBaseNameWithCountryAndVariant() {
    assertBaseNameEquals("property-file_fr.file_en_GB_UNIX.utf8.properties", "property-file_fr.file.utf8");
  }

  public void testBaseNameWithCountry() {
    assertBaseNameEquals("property-file_fr.file_en_GB.utf8.properties", "property-file_fr.file.utf8");
  }

  public void testBaseName() {
    assertBaseNameEquals("Base_Properties.utf8.properties", "Base_Properties.utf8");
  }

  private void assertBaseNameEquals(final String propertyFileName, final String expectedBaseName) {
    final String actualBaseName = ResourceBundleManager.getInstance(getProject()).getBaseName(myFixture.configureByText(propertyFileName, ""));
    assertEquals(expectedBaseName, actualBaseName);
  }
}
