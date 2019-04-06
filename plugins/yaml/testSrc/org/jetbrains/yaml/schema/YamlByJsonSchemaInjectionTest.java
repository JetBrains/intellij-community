// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.Predicate;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.yaml.YAMLLanguage;

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
  private void doTest(@Language("JSON") String schema, @Language("YAML") String text, boolean shouldHaveInjection) throws Exception {
    final PsiFile file = configureInitially(schema, text, "json");
    PsiElement injectedElement = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(file, getEditor().getCaretModel().getOffset());
    assertSame(shouldHaveInjection, injectedElement != null);
  }

  public void testXml() throws Exception {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"X\": {\n" +
           "      \"x-intellij-language-injection\": \"XML\"\n" +
           "    }\n" +
           "  }\n" +
           "}", "X: <a<caret>></a>", true);
  }

  public void testNoInjection() throws Exception {
    doTest("{\n" +
           "  \"properties\": {\n" +
           "    \"X\": {\n" +
           "    }\n" +
           "  }\n" +
           "}", "X: <a<caret>></a>", false);
  }
}
