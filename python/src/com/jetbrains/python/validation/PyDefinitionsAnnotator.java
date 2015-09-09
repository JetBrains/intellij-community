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
package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;

/**
 * Highlights class definitions, functrion definitions, and decorators.
 * User: dcheryasov
 * Date: Jan 9, 2009 9:53:38 AM
 */
public class PyDefinitionsAnnotator extends PyAnnotator {

  @Override
  public void visitPyClass(PyClass node) {
    final ASTNode name_node = node.getNameNode();
    if (name_node != null) {
      Annotation ann = getHolder().createInfoAnnotation(name_node, null);
      ann.setTextAttributes(PyHighlighter.PY_CLASS_DEFINITION);
    }
  }

  @Override
  public void visitPyFunction(PyFunction node) {
    ASTNode name_node =  node.getNameNode();
    if (name_node != null) {
      Annotation ann = getHolder().createInfoAnnotation(name_node, null);
      final String name = node.getName();
      LanguageLevel languageLevel = LanguageLevel.forElement(node);
      if (PyNames.UnderscoredAttributes.contains(name) || PyNames.getBuiltinMethods(languageLevel).containsKey(name)) {
        PyClass cls = node.getContainingClass();
        if (PyNames.NEW.equals(name)) {
          boolean new_style_class = false;
          try {
            if (cls != null) new_style_class = cls.isNewStyleClass(null);
          }
          catch (IndexNotReadyException ignored) {
          }
          if (new_style_class) {
            ann.setTextAttributes(PyHighlighter.PY_PREDEFINED_DEFINITION);
          }
        }
        else {
          ann.setTextAttributes(PyHighlighter.PY_PREDEFINED_DEFINITION);
        }
      }
      else ann.setTextAttributes(PyHighlighter.PY_FUNC_DEFINITION);
    }
  }

  @Override
  public void visitPyDecoratorList(PyDecoratorList node) {
    PyDecorator[] decos = node.getDecorators();
    for (PyDecorator deco : decos) {
      highlightDecorator(deco);  
    }
  }

  private void highlightDecorator(PyDecorator node) {
    // highlight only the identifier
    PsiElement mk = node.getFirstChild(); // the '@'
    if (mk != null) {
      mk = mk.getNextSibling(); // ref
      if (mk != null) {
        Annotation ann = getHolder().createInfoAnnotation(mk, null);
        ann.setTextAttributes(PyHighlighter.PY_DECORATOR);
      }
    }
  }
}
