package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.SourceJavaCodeReference;
import com.intellij.psi.impl.source.parsing.ExpressionParsing;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.lang.ASTNode;
import com.intellij.util.CharTable;

public class JavaSourceUtil {
  public static void fullyQualifyReference(CompositeElement reference, PsiClass targetClass) {
    if (((SourceJavaCodeReference)reference).isQualified()) { // qualifed reference
      final PsiClass parentClass = targetClass.getContainingClass();
      if (parentClass == null) return;
      final ASTNode qualifier = reference.findChildByRole(ChildRole.QUALIFIER);
      if (qualifier instanceof SourceJavaCodeReference) {
        ((SourceJavaCodeReference)qualifier).fullyQualify(parentClass);
      }
    }
    else { // unqualified reference, need to qualify with package name
      final String qName = targetClass.getQualifiedName();
      if (qName == null) {
        return; // todo: local classes?
      }
      final int i = qName.lastIndexOf('.');
      if (i > 0) {
        final String prefix = qName.substring(0, i);
        PsiManager manager = reference.getManager();

        final CharTable table = SharedImplUtil.findCharTableByTree(reference);
        final CompositeElement qualifier;
        if (reference instanceof PsiReferenceExpression) {
          qualifier = ExpressionParsing.parseExpressionText(manager, prefix, 0, prefix.length(), table);
        }
        else {
          qualifier = Parsing.parseJavaCodeReferenceText(manager, prefix, table);
        }
        if (qualifier != null) {
          final CharTable systemCharTab = SharedImplUtil.findCharTableByTree(qualifier);
          final LeafElement dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1, systemCharTab, SharedImplUtil.getManagerByTree(qualifier));
          TreeUtil.insertAfter(qualifier, dot);
          reference.addInternal(qualifier, dot, null, Boolean.FALSE);
        }
      }
    }
  }
}
