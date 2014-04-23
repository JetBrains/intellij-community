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

import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesUtilTest extends TestCase {

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

  public void testBaseNameWithCountryAndVariant() {
    assertBaseNameEquals("property-file_fr.file_en_GB_UNIX.utf8.properties", "property-file_fr.file.utf8");
  }

  public void testBaseNameWithCountry() {
    assertBaseNameEquals("property-file_fr.file_en_GB.utf8.properties", "property-file_fr.file.utf8");
  }

  public void testBaseName() {
    assertBaseNameEquals("Base_Properties.utf8.properties", "Base_Properties.utf8");
  }

  public void test1() {
    assertBaseNameEquals("Base_Page_fr.utf8.properties", "Base_Page.utf8");
  }
  public void test2() {
    assertBaseNameEquals("Base_Page_en.utf8.properties", "Base_Page.utf8");
  }
  public void test3() {
    assertBaseNameEquals("Base_Page.utf8.properties", "Base_Page.utf8");
  }

  private static void assertBaseNameEquals(final String propertyFileName, final String expectedBaseName) {
    final String actualBaseName = PropertiesUtil.getBaseName(new StubVirtualFile() {
      @NotNull
      @Override
      public String getName() {
        return propertyFileName;
      }
    });
    assertEquals(expectedBaseName, actualBaseName);
  }
}
