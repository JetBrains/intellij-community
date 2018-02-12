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
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Highlights class definitions, functrion definitions, and decorators.
 * User: dcheryasov
 */
public class PyDefinitionsAnnotator extends PyAnnotator {

  @Override
  public void visitPyClass(PyClass node) {
    final ASTNode name_node = node.getNameNode();
    if (name_node != null) {
      addHighlightingAnnotation(name_node, PyHighlighter.PY_CLASS_DEFINITION);
    }
  }

  @Override
  public void visitPyFunction(PyFunction node) {
    ASTNode name_node = node.getNameNode();
    if (name_node != null) {
      final String name = node.getName();
      LanguageLevel languageLevel = LanguageLevel.forElement(node);
      if (PyNames.UNDERSCORED_ATTRIBUTES.contains(name) || PyNames.getBuiltinMethods(languageLevel).containsKey(name)) {
        PyClass cls = node.getContainingClass();
        if (PyNames.NEW.equals(name)) {
          boolean new_style_class = false;
          try {
            if (cls != null) new_style_class = cls.isNewStyleClass(null);
          }
          catch (IndexNotReadyException ignored) {
          }
          if (new_style_class) {
            addHighlightingAnnotation(name_node, PyHighlighter.PY_PREDEFINED_DEFINITION);
          }
        }
        else {
          addHighlightingAnnotation(name_node, PyHighlighter.PY_PREDEFINED_DEFINITION);
        }
      }
      else {
        addHighlightingAnnotation(name_node, PyHighlighter.PY_FUNC_DEFINITION);
      }
    }
  }

  @Override
  public void visitPyDecoratorList(PyDecoratorList node) {
    PyDecorator[] decos = node.getDecorators();
    for (PyDecorator deco : decos) {
      highlightDecorator(deco);
    }
  }

  private void highlightDecorator(@NotNull PyDecorator node) {
    final PsiElement atSign = node.getFirstChild();
    if (atSign != null) {
      addHighlightingAnnotation(atSign, PyHighlighter.PY_DECORATOR);
      final PsiElement refExpression = PyPsiUtils.getNextNonWhitespaceSibling(atSign);
      if (refExpression != null) {
        addHighlightingAnnotation(refExpression, PyHighlighter.PY_DECORATOR);
      }
    }
  }
}
