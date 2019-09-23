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

  public void testAddMissingProperty() {
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

  public void testRemoveProhibitedProperty() {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"a\": {},\n" +
           "    \"c\": {}\n" +
           "  },\n" +
           "  \"additionalProperties\": false\n" +
           "}", "a: 5\n<warning>b: 6<caret></warning>\nc: 7", "Remove prohibited property 'b'", "a: 5\n" +
                                                                                         "c: 7");
  }

  public void testEmptyFile() {
    doTest("{\n" +
           "  \"type\": \"object\",\n" +
           "\n" +
           "  \"properties\": {\n" +
           "    \"versionAsStringArray\": {\n" +
           "      \"type\": \"array\"\n" +
           "    }\n" +
           "  },\n" +
           "  \"required\": [\"versionAsStringArray\"]\n" +
           "}", "<warning></warning>", "Add missing property 'versionAsStringArray'", "versionAsStringArray:\n" +
                                                                   "  - ");
  }

  public void testEmptyObject() {
    doTest("{\n" +
           "  \"type\": \"object\",\n" +
           "\n" +
           "  \"properties\": {\n" +
           "    \"versionAsStringArray\": {\n" +
           "      \"type\": \"object\",\n" +
           "      \"properties\": {\n" +
           "        \"xxx\": {\n" +
           "          \"type\": \"array\"\n" +
           "        }\n" +
           "      },\n" +
           "      \"required\": [\"xxx\"]\n" +
           "    }\n" +
           "  },\n" +
           "  \"required\": [\"versionAsStringArray\"]\n" +
           "}", "versionAsStringArray:\n" +
                "<warning>  <caret></warning>", "Add missing property 'xxx'", "versionAsStringArray:\n" +
                                                                       "  xxx:\n" +
                                                                       "    - ");
  }

  public void testEmptyObjectMultipleProps() {
    doTest("{\n" +
           "  \"type\": \"object\",\n" +
           "\n" +
           "  \"properties\": {\n" +
           "    \"versionAsStringArray\": {\n" +
           "      \"type\": \"object\",\n" +
           "      \"properties\": {\n" +
           "        \"xxx\": {\n" +
           "          \"type\": \"number\"\n" +
           "        },\n" +
           "        \"yyy\": {\n" +
           "          \"type\": \"string\"\n" +
           "        },\n" +
           "        \"zzz\": {\n" +
           "          \"type\": \"number\"\n" +
           "        }\n" +
           "      },\n" +
           "      \"required\": [\"xxx\", \"yyy\", \"zzz\"]\n" +
           "    }\n" +
           "  },\n" +
           "  \"required\": [\"versionAsStringArray\"]\n" +
           "}", "versionAsStringArray:\n" +
                "<warning>  <caret></warning>","Add missing properties 'xxx', 'yyy', 'zzz'", "versionAsStringArray:\n" +
                                                                                      "  xxx: 0\n" +
                                                                                      "  yyy:\n" +
                                                                                      "  zzz: 0");
  }
}
