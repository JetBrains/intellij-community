// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Predicate;
import com.jetbrains.jsonSchema.fixes.JsonSchemaQuickFixTestBase;
import org.jetbrains.yaml.YAMLLanguage;

public class YamlByJsonSchemaQuickFixTest extends JsonSchemaQuickFixTestBase {
  @Override
  protected String getTestFileName() {
    return "config.yml";
  }

  @Override
  protected InspectionProfileEntry getInspectionProfile() {
    return new YamlJsonSchemaHighlightingInspection();
  }

  @Override
  protected Predicate<VirtualFile> getAvailabilityPredicate() {
    return file -> file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(
      YAMLLanguage.INSTANCE);
  }

  public void testAddMissingProperty() throws Exception {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"a\": {\n" +
           "      \"default\": \"q\"\n" +
           "    }\n" +
           "  },\n" +
           "  \"required\": [\"a\", \"b\"]\n" +
           "}", "<warning>c: 5</warning>", "Add missing properties 'a', 'b'", "a: q\n" +
                                                                              "b:\n" +
                                                                              "c: 5");
  }

  public void testRemoveProhibitedProperty() throws Exception {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"a\": {},\n" +
           "    \"c\": {}\n" +
           "  },\n" +
           "  \"additionalProperties\": false\n" +
           "}", "a: 5\n<warning>b: 6</warning>\nc: 7", "Remove prohibited property 'b'", "a: 5\n" +
                                                                                         "c: 7");
  }
}
