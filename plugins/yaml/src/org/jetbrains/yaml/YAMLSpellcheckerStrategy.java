/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.yaml;

import com.intellij.json.JsonSchemaSpellcheckerClient;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;

public class YAMLSpellcheckerStrategy extends SpellcheckingStrategy {
  @NotNull
  @Override
  public Tokenizer getTokenizer(final PsiElement element) {
    final ASTNode node = element.getNode();
    if (node != null){
      final IElementType type = node.getElementType();
      if (type == YAMLTokenTypes.SCALAR_TEXT ||
          type == YAMLTokenTypes.SCALAR_LIST ||
          type == YAMLTokenTypes.TEXT ||
          type == YAMLTokenTypes.SCALAR_KEY ||
          type == YAMLTokenTypes.SCALAR_STRING ||
          type == YAMLTokenTypes.SCALAR_DSTRING ||
          type == YAMLTokenTypes.COMMENT) {
        if (new JsonSchemaSpellcheckerClientForYaml(element).matchesNameFromSchema()) {
          return EMPTY_TOKENIZER;
        }
        else {
          return TEXT_TOKENIZER;
        }
      }
    }
    return super.getTokenizer(element);
  }

  private static class JsonSchemaSpellcheckerClientForYaml extends JsonSchemaSpellcheckerClient {
    @NotNull private final PsiElement element;

    protected JsonSchemaSpellcheckerClientForYaml(@NotNull PsiElement element) {
      this.element = element;
    }

    @Override
    protected @NotNull PsiElement getElement() {
      return element;
    }

    @Override
    protected @Nullable String getValue() {
      PsiElement parent = element.getParent();
      if (element.getNode().getElementType() == YAMLTokenTypes.SCALAR_KEY) {
        return ((YAMLKeyValue)parent).getKeyText();
      }
      else if (parent instanceof YAMLScalar) {
        return ((YAMLScalar)parent).getTextValue();
      }
      else {
        return null;
      }
    }

    @Override
    protected boolean isXIntellijInjection(@NotNull JsonSchemaService service, @NotNull JsonSchemaObject rootSchema) {
      return false;
    }
  }
}