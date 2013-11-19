/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;

/**
 * Marks built-in names.
 * User: dcheryasov
 * Date: Jan 10, 2009 12:17:15 PM
 */
public class PyBuiltinAnnotator extends PyAnnotator {
  @Override
  public void visitPyReferenceExpression(PyReferenceExpression node) {
    final String name = node.getName();
    if (name == null) return; 
    boolean highlighted_as_attribute = highlightAsAttribute(node, name);
    if (! highlighted_as_attribute && node.getQualifier() == null) {
      // things like len()
      ResolveResult[] resolved = node.getReference().multiResolve(false); // constructors, etc may give multiple results...
      if (resolved.length > 0) {
        if (PyBuiltinCache.getInstance(node).hasInBuiltins(resolved[0].getElement())) { // ...but we only care about the default resolution
          Annotation ann;
          PsiElement parent = node.getParent();
          if (parent instanceof PyDecorator) {
            // don't mark the entire decorator, only mark the "@", else we'll conflict with deco annotator
            ann = getHolder().createInfoAnnotation(parent.getFirstChild(), null); // first child is there, or we'd not parse as deco
          }
          else ann = getHolder().createInfoAnnotation(node, null);
          ann.setTextAttributes(PyHighlighter.PY_BUILTIN_NAME);
        }
      }
    }
  }

  @Override
  public void visitPyTargetExpression(PyTargetExpression node) {
    final String name = node.getName();
    if (name == null) return;
    highlightAsAttribute(node, name);
  }

  /**
   * Try to highlight a node as a class attribute.
   * @param node what to work with
   * @return true iff the node was highlighted.  
   */
  private boolean highlightAsAttribute(PyQualifiedExpression node, String name) {
    LanguageLevel languageLevel = LanguageLevel.forElement(node);
    if (PyNames.UnderscoredAttributes.contains(name) || PyNames.getBuiltinMethods(languageLevel).containsKey(name)) {
      // things like __len__
      if (
        (node.getQualifier() != null) // foo.__len__
        || (PyUtil.getConcealingParent(node) instanceof PyClass) // class Foo: ... __len__ = myLenImpl
      ) {
        final ASTNode astNode = node.getNode();
        if (astNode != null) {
          ASTNode tgt = astNode.findChildByType(PyTokenTypes.IDENTIFIER); // only the id, not all qualifiers subtree
          if (tgt != null) {
            Annotation ann = getHolder().createInfoAnnotation(tgt, null);
            ann.setTextAttributes(PyHighlighter.PY_PREDEFINED_USAGE);
            return true;
          }
        }
      }
    }
    return false;
  }

}
