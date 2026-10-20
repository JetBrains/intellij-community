// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixture;
import com.jetbrains.jsonSchema.JsonSchemaInjectionTest;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixtureKt.jsonSchemaHighlightingFixture;

@TestApplication
public class YamlByJsonSchemaInjectionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHighlightingTestFixture> schemaFixture =
    jsonSchemaHighlightingFixture(codeInsightFixture, "config.yml", () -> new JsonSchemaComplianceInspection(), YAMLLanguage.INSTANCE);

  private JsonSchemaHighlightingTestFixture mySchemaFixture;

  @BeforeEach
  void setUp() {
    mySchemaFixture = schemaFixture.get();
  }

  @SuppressWarnings("SameParameterValue")
  private void doTest(@Language("JSON") String schema, @Language("YAML") String text, boolean shouldHaveInjection) {
    final PsiFile file = mySchemaFixture.configureInitially(schema, text, "json");
    JsonSchemaInjectionTest.checkInjection(shouldHaveInjection, file, YAMLFile.class);
  }

  @Test
  public void testXml() {
    doTest("""
             {
               "properties": {
                 "X": {
                   "x-intellij-language-injection": "XML"
                 }
               }
             }""", "X: <a<caret>></a>", true);
  }

  @Test
  public void testNoInjection() {
    doTest("""
             {
               "properties": {
                 "X": {
                 }
               }
             }""", "X: <a<caret>></a>", false);
  }

  @Test
  public void testInArray() {
    doTest("""
             {
               "type": "object",
               "additionalProperties": {
                 "type": ["array"],
                 "items": {
                   "type": "string",
                   "x-intellij-language-injection": "XML"
                 }
               }
             }""", "x:\n" +
                   "  - <a><caret></a>;", true);
  }

  @Test
  public void testRegex() {
    doTest("""
             {
               "additionalProperties": {
                 "type": "string",
                 "x-intellij-language-injection": "XML"
               }
             }""", "abc: \"d<caret>ef\"", true);
  }
}
