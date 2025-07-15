/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Highlights class definitions, function definitions, and decorators.
 */
public final class PyDefinitionsHighlightingAnnotator extends PyAnnotatorBase {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull PyAnnotationHolder holder) {
    element.accept(new MyVisitor(holder));
  }

  private static class MyVisitor extends PyElementVisitor {
    private final @NotNull PyAnnotationHolder myHolder;

    private MyVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
      final ASTNode name_node = node.getNameNode();
      if (name_node != null) {
        myHolder.addHighlightingAnnotation(name_node, PyHighlighter.PY_CLASS_DEFINITION);
      }
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      ASTNode nameNode = node.getNameNode();
      if (nameNode != null) {
        final String name = node.getName();
        LanguageLevel languageLevel = LanguageLevel.forElement(node);
        if (PyNames.UNDERSCORED_ATTRIBUTES.contains(name) || PyNames.getBuiltinMethods(languageLevel).containsKey(name)) {
          PyClass cls = node.getContainingClass();
          if (PyUtil.isNewMethod(node)) {
            boolean new_style_class = false;
            try {
              if (cls != null) new_style_class = cls.isNewStyleClass(null);
            }
            catch (IndexNotReadyException ignored) {
            }
            if (new_style_class) {
              myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_PREDEFINED_DEFINITION);
            }
          }
          else {
            myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_PREDEFINED_DEFINITION);
          }
        }
        else {
          if (ScopeUtil.getScopeOwner(node) instanceof PyFunction) {
            myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_NESTED_FUNC_DEFINITION);
          }
          else {
            myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_FUNC_DEFINITION);
          }
        }
      }
    }

    @Override
    public void visitPyDecorator(@NotNull PyDecorator node) {
      final PsiElement atSign = node.getFirstChild();
      if (atSign != null) {
        myHolder.addHighlightingAnnotation(atSign, PyHighlighter.PY_DECORATOR);
        if (node.getQualifiedName() != null) {
          myHolder.addHighlightingAnnotation(Objects.requireNonNull(node.getCallee()), PyHighlighter.PY_DECORATOR);
        }
      }
    }
  }
}
