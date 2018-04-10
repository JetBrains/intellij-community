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
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.NotNull;

/**
 * Marks built-in names.
 *
 * @author dcheryasov
 */
public class PyBuiltinAnnotator extends PyAnnotator {
  @Override
  public void visitPyReferenceExpression(PyReferenceExpression node) {
    final String name = node.getName();
    if (name == null) return;
    final boolean highlightedAsAttribute = highlightAsAttribute(node, name);
    if (highlightedAsAttribute) {
      return;
    }
    if ((PyBuiltinCache.isInBuiltins(node) || PyUtil.isPy2ReservedWord(node)) && !(node.getParent() instanceof PyDecorator)) {
      addHighlightingAnnotation(node, PyHighlighter.PY_BUILTIN_NAME);
    }
  }

  @Override
  public void visitPyTargetExpression(PyTargetExpression node) {
    final String name = node.getName();
    if (name != null) {
      highlightAsAttribute(node, name);
    }
  }

  /**
   * Try to highlight a node as a class attribute.
   *
   * @param node what to work with
   * @return true iff the node was highlighted.
   */
  private boolean highlightAsAttribute(@NotNull PyQualifiedExpression node, @NotNull String name) {
    final LanguageLevel languageLevel = LanguageLevel.forElement(node);
    if (PyNames.UNDERSCORED_ATTRIBUTES.contains(name) || PyNames.getBuiltinMethods(languageLevel).containsKey(name)) {
      // things like __len__: foo.__len__ or class Foo: ... __len__ = my_len_impl
      if (node.isQualified() || ScopeUtil.getScopeOwner(node) instanceof PyClass) {
        final ASTNode astNode = node.getNode();
        if (astNode != null) {
          final ASTNode tgt = astNode.findChildByType(PyTokenTypes.IDENTIFIER); // only the id, not all qualifiers subtree
          if (tgt != null) {
            addHighlightingAnnotation(tgt, PyHighlighter.PY_PREDEFINED_USAGE);
            return true;
          }
        }
      }
    }
    return false;
  }
}
