package com.jetbrains.python.psi.impl.references;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Reference to the import source in a 'from ... import' statement:<br/>
 * <code>from <u>foo</u> import name</code>

 * @author yole
 */
public class PyFromImportSourceReference extends PyImportReference {
  private PyFromImportStatement myStatement;
  
  public PyFromImportSourceReference(PyReferenceExpressionImpl element, PyResolveContext context) {
    super(element, context);
    myStatement = PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class);
    assert myStatement != null;
  }

  @NotNull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    List<PsiElement> targets = ResolveImportUtil.resolveFromImportStatementSource(myStatement, myElement.asQualifiedName());
    return ResolveImportUtil.rateResults(targets);
  }

  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
    PyExpression qualifier = myElement.getQualifier();
    return qualifier == null ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
  }
}
