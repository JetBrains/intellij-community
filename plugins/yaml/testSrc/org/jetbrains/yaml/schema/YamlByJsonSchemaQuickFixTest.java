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
    doTest("""
             {
               "properties": {
                 "a": {
                   "default": "q"
                 }
               },
               "required": ["a", "b"]
             }""", "<warning>c: 5</warning>", "Add missing properties 'a', 'b'", """
             a: q
             b:
             c: 5""");
  }

  public void testRemoveProhibitedProperty() {
    doTest("""
             {
               "properties": {
                 "a": {},
                 "c": {}
               },
               "additionalProperties": false
             }""", "a: 5\n<warning>b: 6<caret></warning>\nc: 7", "Remove prohibited property 'b'", "a: 5\n" +
                                                                                                   "c: 7");
  }

  public void testEmptyFile() {
    doTest("""
             {
               "type": "object",

               "properties": {
                 "versionAsStringArray": {
                   "type": "array"
                 }
               },
               "required": ["versionAsStringArray"]
             }""", "<warning></warning>", "Add missing property 'versionAsStringArray'", "versionAsStringArray:\n" +
                                                                                         "  - ");
  }

  public void testEmptyObject() {
    doTest("""
             {
               "type": "object",

               "properties": {
                 "versionAsStringArray": {
                   "type": "object",
                   "properties": {
                     "xxx": {
                       "type": "array"
                     }
                   },
                   "required": ["xxx"]
                 }
               },
               "required": ["versionAsStringArray"]
             }""", "versionAsStringArray:\n" +
                   "<warning>  <caret></warning>", "Add missing property 'xxx'", """
             versionAsStringArray:
               xxx:
                 -\s""");
  }

  public void testEmptyObjectMultipleProps() {
    doTest("""
             {
               "type": "object",

               "properties": {
                 "versionAsStringArray": {
                   "type": "object",
                   "properties": {
                     "xxx": {
                       "type": "number"
                     },
                     "yyy": {
                       "type": "string"
                     },
                     "zzz": {
                       "type": "number"
                     }
                   },
                   "required": ["xxx", "yyy", "zzz"]
                 }
               },
               "required": ["versionAsStringArray"]
             }""", "versionAsStringArray:\n" +
                   "<warning>  <caret></warning>", "Add missing properties 'xxx', 'yyy', 'zzz'", """
             versionAsStringArray:
               xxx: 0
               yyy:
               zzz: 0""");
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

  @Language("JSON") private static final String SCHEMA_WITH_NESTING = """
    {
      "properties": {
        "image": {
          "description": "Container Image",
          "properties": {
            "repo": {
              "type": "string"
            },
            "tag": {
              "type": "string"
            }
          },
          "required": [
            "tag"
          ],
          "type": "object"
        }
      },
      "title": "Values",
      "type": "object"
    }
    """;

}
