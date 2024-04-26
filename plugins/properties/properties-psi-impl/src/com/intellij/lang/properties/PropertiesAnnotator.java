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

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.PropertiesHighlighter.PropertiesComponent;
import com.intellij.lang.properties.editor.PropertiesValueHighlighter;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lexer.Lexer;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class PropertiesAnnotator implements Annotator {
  private static final ExtensionPointName<DuplicatePropertyKeyAnnotationSuppressor>
    EP_NAME = ExtensionPointName.create("com.intellij.properties.duplicatePropertyKeyAnnotationSuppressor");

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!(element instanceof Property property)) return;
    PropertiesFile propertiesFile = property.getPropertiesFile();
    final String key = property.getUnescapedKey();
    if (key == null) return;
    Collection<IProperty> others = propertiesFile.findPropertiesByKey(key);
    ASTNode keyNode = ((PropertyImpl)property).getKeyNode();
    if (keyNode == null) return;
    if (others.size() != 1 &&
      EP_NAME.findFirstSafe(suppressor -> suppressor.suppressAnnotationFor(property)) == null) {
      holder.newAnnotation(HighlightSeverity.ERROR,PropertiesBundle.message("duplicate.property.key.error.message")).range(keyNode)
      .withFix(PropertiesQuickFixFactory.getInstance().createRemovePropertyFix(property)).create();
    }

    highlightTokens(keyNode, holder, new PropertiesHighlighter());
    ASTNode valueNode = ((PropertyImpl)property).getValueNode();
    if (valueNode != null) {
      highlightTokens(valueNode, holder, new PropertiesValueHighlighter());
    }
  }

  private static void highlightTokens(final ASTNode node, final AnnotationHolder holder, PropertiesHighlighter highlighter) {
    Lexer lexer = highlighter.getHighlightingLexer();
    final String s = node.getText();
    lexer.start(s);

    while (lexer.getTokenType() != null) {
      IElementType elementType = lexer.getTokenType();
      TextAttributesKey[] keys = highlighter.getTokenHighlights(elementType);
      for (TextAttributesKey key : keys) {
        final String displayName = PropertiesComponent.getDisplayName(key);
        final HighlightSeverity severity = PropertiesComponent.getSeverity(key);
        if (severity != null && displayName != null) {
          int start = lexer.getTokenStart() + node.getTextRange().getStartOffset();
          int end = lexer.getTokenEnd() + node.getTextRange().getStartOffset();
          TextRange textRange = new TextRange(start, end);
          TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
          AnnotationBuilder builder = holder.newAnnotation(severity, displayName).range(textRange).enforcedTextAttributes(attributes);

          int startOffset = textRange.getStartOffset();
          if (key == PropertiesComponent.PROPERTIES_INVALID_STRING_ESCAPE.getTextAttributesKey()) {
            builder = builder.withFix(new ModCommandAction() {
              @Override
              @NotNull
              public String getFamilyName() {
                return PropertiesBundle.message("unescape");
              }

              @Override
              public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
                if (!BaseIntentionAction.canModify(context.file())) return null;

                String text = context.file().getText();
                return text.length() > startOffset && text.charAt(startOffset) == '\\' ?
                       Presentation.of(getFamilyName()) : null;
              }

              @Override
              public @NotNull ModCommand perform(@NotNull ActionContext context) {
                return ModCommand.psiUpdate(context, updater -> {
                  updater.getWritable(context.file()).getViewProvider().getDocument().deleteString(startOffset, startOffset + 1);
                });
              }
            });
          }
          builder.create();
        }
      }
      lexer.advance();
    }
  }
}
