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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.editor.PropertiesValueHighlighter;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author cdr
 */
public class PropertiesAnnotator implements Annotator {

  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!(element instanceof IProperty)) return;
    final Property property = (Property)element;
    PropertiesFile propertiesFile = property.getPropertiesFile();
    Collection<IProperty> others = propertiesFile.findPropertiesByKey(property.getUnescapedKey());
    ASTNode keyNode = ((PropertyImpl)property).getKeyNode();
    if (others.size() != 1) {
      Annotation annotation = holder.createErrorAnnotation(keyNode, PropertiesBundle.message("duplicate.property.key.error.message"));
      annotation.registerFix(PropertiesQuickFixFactory.getInstance().createRemovePropertyFix(property));
    }

    highlightTokens(property, keyNode, holder, new PropertiesHighlighter());
    ASTNode valueNode = ((PropertyImpl)property).getValueNode();
    if (valueNode != null) {
      highlightTokens(property, valueNode, holder, new PropertiesValueHighlighter());
    }
  }

  private static void highlightTokens(final Property property, final ASTNode node, final AnnotationHolder holder, PropertiesHighlighter highlighter) {
    Lexer lexer = highlighter.getHighlightingLexer();
    final String s = node.getText();
    lexer.start(s);

    while (lexer.getTokenType() != null) {
      IElementType elementType = lexer.getTokenType();
      TextAttributesKey[] keys = highlighter.getTokenHighlights(elementType);
      for (TextAttributesKey key : keys) {
        Pair<String,HighlightSeverity> pair = PropertiesHighlighter.DISPLAY_NAMES.get(key);
        String displayName = pair.getFirst();
        HighlightSeverity severity = pair.getSecond();
        if (severity != null) {
          int start = lexer.getTokenStart() + node.getTextRange().getStartOffset();
          int end = lexer.getTokenEnd() + node.getTextRange().getStartOffset();
          TextRange textRange = new TextRange(start, end);
          final Annotation annotation;
          if (severity == HighlightSeverity.WARNING) {
            annotation = holder.createWarningAnnotation(textRange, displayName);
          }
          else if (severity == HighlightSeverity.ERROR) {
            annotation = holder.createErrorAnnotation(textRange, displayName);
          }
          else {
            annotation = holder.createInfoAnnotation(textRange, displayName);
          }
          TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
          annotation.setEnforcedTextAttributes(attributes);
          if (key == PropertiesHighlighter.PROPERTIES_INVALID_STRING_ESCAPE) {
            annotation.registerFix(new IntentionAction() {
              @NotNull
              public String getText() {
                return PropertiesBundle.message("unescape");
              }

              @NotNull
              public String getFamilyName() {
                return getText();
              }

              public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
                if (!property.isValid() || !property.getManager().isInProject(property)) return false;

                String text = property.getPropertiesFile().getContainingFile().getText();
                int startOffset = annotation.getStartOffset();
                return text.length() > startOffset && text.charAt(startOffset) == '\\';
              }

              public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
                if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
                int offset = annotation.getStartOffset();
                if (property.getPropertiesFile().getContainingFile().getText().charAt(offset) == '\\') {
                  editor.getDocument().deleteString(offset, offset+1);
                }
              }

              public boolean startInWriteAction() {
                return true;
              }
            });
          }
        }
      }
      lexer.advance();
    }
  }
}
