// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase;
import com.jetbrains.jsonSchema.JsonSchemaInjectionTest;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLFile;

import java.util.function.Predicate;

public class YamlByJsonSchemaInjectionTest extends JsonSchemaHighlightingTestBase {
  @Override
  protected String getTestFileName() {
    return "config.yml";
  }
  @Override
  protected InspectionProfileEntry getInspectionProfile() {
    return new JsonSchemaComplianceInspection();
  }

  @Override
  protected Predicate<VirtualFile> getAvailabilityPredicate() {
    return file -> file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(
      YAMLLanguage.INSTANCE);
  }

  @SuppressWarnings("SameParameterValue")
  private void doTest(@Language("JSON") String schema, @Language("YAML") String text, boolean shouldHaveInjection) {
    final PsiFile file = configureInitially(schema, text, "json");
    JsonSchemaInjectionTest.checkInjection(shouldHaveInjection, file, YAMLFile.class);
  }

  public void testXml() {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"X\": {\n" +
           "      \"x-intellij-language-injection\": \"XML\"\n" +
           "    }\n" +
           "  }\n" +
           "}", "X: <a<caret>></a>", true);
  }

  public void testNoInjection() {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"X\": {\n" +
           "    }\n" +
           "  }\n" +
           "}", "X: <a<caret>></a>", false);
  }

  public void testInArray() {
    doTest("{\n" +
           "  \"type\": \"object\",\n" +
           "  \"additionalProperties\": {\n" +
           "    \"type\": [\"array\"],\n" +
           "    \"items\": {\n" +
           "      \"type\": \"string\",\n" +
           "      \"x-intellij-language-injection\": \"XML\"\n" +
           "    }\n" +
           "  }\n" +
           "}", "x:\n" +
                "  - <a><caret></a>;", true);
  }

  public void testRegex() {
    doTest("{\n" +
           "  \"additionalProperties\": {\n" +
           "    \"type\": \"string\",\n" +
           "    \"x-intellij-language-injection\": \"XML\"\n" +
           "  }\n" +
           "}", "abc: \"d<caret>ef\"", true);
  }
}
