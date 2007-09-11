package com.intellij.lang.properties;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
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
class PropertiesAnnotator implements Annotator {

  public void annotate(PsiElement element, AnnotationHolder holder) {
    if (!(element instanceof Property)) return;
    final Property property = (Property)element;
    PropertiesFile propertiesFile = property.getContainingFile();
    Collection<Property> others = propertiesFile.findPropertiesByKey(property.getUnescapedKey());
    ASTNode keyNode = ((PropertyImpl)property).getKeyNode();
    if (others.size() != 1) {
      Annotation annotation = holder.createErrorAnnotation(keyNode, PropertiesBundle.message("duplicate.property.key.error.message"));
      annotation.registerFix(new RemovePropertyFix(property));
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
    lexer.start(s,0,s.length(),0);
    
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
                return QuickFixBundle.message("unescape");
              }

              @NotNull
              public String getFamilyName() {
                return getText();
              }

              public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
                return property.isValid()
                       && property.getManager().isInProject(property)
                       && property.getContainingFile().getText().charAt(annotation.getStartOffset()) == '\\'
                  ;
              }

              public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
                if (!CodeInsightUtil.prepareFileForWrite(file)) return;
                int offset = annotation.getStartOffset();
                if (property.getContainingFile().getText().charAt(offset) == '\\') {
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
