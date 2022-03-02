// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.fixes.JsonSchemaQuickFixTestBase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.yaml.YAMLLanguage;

import java.util.function.Predicate;

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

  public void testAddMissingPropertyAfterComment() {
    doTest(SCHEMA_WITH_NESTING, "image: #aaa\n" +
                                "<warning descr=\"Schema validation: Missing required property 'tag'\"> <caret>  </warning>", "Add missing property 'tag'", "image: #aaa\n" +
                                                                                                                                           "  tag: ");
  }

  public void testAddMissingPropertyAfterWhitespace() {
    doTest(SCHEMA_WITH_NESTING, "image: \n" +
                                "<warning descr=\"Schema validation: Missing required property 'tag'\"> <caret>  </warning>", "Add missing property 'tag'", "image:\n" +
                                                                                                                                           "  tag: ");
  }

  @Language("JSON") private static final String SCHEMA_WITH_NESTING = "{\n" +
                                                               "  \"properties\": {\n" +
                                                               "    \"image\": {\n" +
                                                               "      \"description\": \"Container Image\",\n" +
                                                               "      \"properties\": {\n" +
                                                               "        \"repo\": {\n" +
                                                               "          \"type\": \"string\"\n" +
                                                               "        },\n" +
                                                               "        \"tag\": {\n" +
                                                               "          \"type\": \"string\"\n" +
                                                               "        }\n" +
                                                               "      },\n" +
                                                               "      \"required\": [\n" +
                                                               "        \"tag\"\n" +
                                                               "      ],\n" +
                                                               "      \"type\": \"object\"\n" +
                                                               "    }\n" +
                                                               "  },\n" +
                                                               "  \"title\": \"Values\",\n" +
                                                               "  \"type\": \"object\"\n" +
                                                               "}\n";

}
