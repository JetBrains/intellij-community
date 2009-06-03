package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.jetbrains.python.PyHighlighter;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveUtil;

/**
 * Marks built-in names.
 * User: dcheryasov
 * Date: Jan 10, 2009 12:17:15 PM
 */
public class PyBuiltinAnnotator extends PyAnnotator {
  @Override
  public void visitPyReferenceExpression(PyReferenceExpression node) {
    if (PyNames.UnderscoredNames.contains(node.getName())) {
      // things like __len__
      if (
        (node.getQualifier() != null) // foo.__len__
        || (PyResolveUtil.getConcealingParent(node) instanceof PyClass) // class Foo: ... __len__ = myLenImpl
      ) {
        final ASTNode astNode = node.getNode();
        if (astNode != null) {
          ASTNode tgt = astNode.findChildByType(PyTokenTypes.IDENTIFIER); // only the id, not all qualifiers subtree
          if (tgt != null) {
            Annotation ann = getHolder().createInfoAnnotation(tgt, null);
            ann.setTextAttributes(PyHighlighter.PY_PREDEFINED_USAGE);
          }
        }
      }
    }
    else if (node.getQualifier() == null) {
      // things like len()
      ResolveResult[] resolved = node.multiResolve(false); // constructors, etc give multiple results...
      if (resolved.length > 0) {
        if (PyBuiltinCache.hasInBuiltins(resolved[0].getElement())) { // ...but we only care about single-resolvers
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

}
