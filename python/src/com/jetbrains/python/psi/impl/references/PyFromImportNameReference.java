package com.jetbrains.python.psi.impl.references;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Reference to an imported name in a 'from ... import' statement:<br/>
 * <code>from foo import <u>name</u></code>
 *
 * @author yole
 */
public class PyFromImportNameReference extends PyImportReference {
  private final PyImportElement myImportElement;
  private final PyFromImportStatement myStatement;

  public PyFromImportNameReference(PyReferenceExpressionImpl element, PyResolveContext context) {
    super(element, context);
    myImportElement = PsiTreeUtil.getParentOfType(element, PyImportElement.class);
    assert myImportElement != null;
    myStatement = PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class);
    assert myStatement != null;
  }

  @NotNull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    return ResolveImportUtil.resolveNameInFromImport(myImportElement, myElement.asQualifiedName(), myStatement);
  }
}
