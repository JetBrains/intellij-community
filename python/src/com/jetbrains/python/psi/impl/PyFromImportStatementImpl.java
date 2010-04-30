package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayFactory;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyFromImportStatementImpl extends PyBaseElementImpl<PyFromImportStatementStub> implements PyFromImportStatement {
  public PyFromImportStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyFromImportStatementImpl(PyFromImportStatementStub stub) {
    super(stub, PyElementTypes.FROM_IMPORT_STATEMENT);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyFromImportStatement(this);
  }

  public boolean isStarImport() {
    final PyFromImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.isStarImport();
    }
    return getStarImportElement() != null;
  }

  @Nullable
  public PyReferenceExpression getImportSource() {
    return childToPsi(TokenSet.create(PyElementTypes.REFERENCE_EXPRESSION), 0);
  }

  public PyQualifiedName getImportSourceQName() {
    final PyFromImportStatementStub stub = getStub();
    if (stub != null) {
      final PyQualifiedName qName = stub.getImportSourceQName();
      if (qName != null && qName.getComponentCount() == 0) {  // relative import only: from .. import the_name
        return null;
      }
      return qName;
    }

    final PyReferenceExpression importSource = getImportSource();
    if (importSource == null) {
      return null;
    }
    return importSource.asQualifiedName();
  }

  public PyImportElement[] getImportElements() {
    final PyFromImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(PyElementTypes.IMPORT_ELEMENT, new ArrayFactory<PyImportElement>() {
        public PyImportElement[] create(int count) {
          return new PyImportElement[count];
        }
      });
    }
    List<PyImportElement> result = new ArrayList<PyImportElement>();
    final ASTNode importKeyword = getNode().findChildByType(PyTokenTypes.IMPORT_KEYWORD);
    if (importKeyword != null) {
      for(ASTNode node = importKeyword.getTreeNext(); node != null; node = node.getTreeNext()) {
        if (node.getElementType() == PyElementTypes.IMPORT_ELEMENT) {
          result.add((PyImportElement) node.getPsi());
        }
      }
    }
    return result.toArray(new PyImportElement[result.size()]);
  }

  public PyStarImportElement getStarImportElement() {
    return findChildByClass(PyStarImportElement.class);
  }

  public int getRelativeLevel() {
    final PyFromImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getRelativeLevel();
    }
    // crude but OK for reasonable cases, and in unreasonably complex error cases this number is moot anyway. 
    int result = 0;
    PsiElement import_kwd = findChildByType(PyTokenTypes.IMPORT_KEYWORD);
    if (import_kwd != null) {
      ASTNode seeker = import_kwd.getNode();
      while (seeker != null) {
        if (seeker.getElementType() == PyTokenTypes.DOT) result += 1;
        seeker = seeker.getTreePrev();
      }
    }
    return result;
  }

  public boolean isFromFuture() {
    final PyQualifiedName qName = getImportSourceQName();
    return qName != null && qName.matches(PyNames.FUTURE_MODULE);
  }

  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor, @NotNull final ResolveState state, final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    // import is per-file
    if (place.getContainingFile() != getContainingFile()) {
      return true;
    }
    if (isStarImport()) {
      PyReferenceExpression expr = getImportSource();
      if (expr != null) {
        final PsiElement target = ResolveImportUtil.resolveImportReference(expr);
        final PsiElement importedFile = PyUtil.turnDirIntoInit(target);
        if (importedFile != null) {
          return importedFile.processDeclarations(processor, state, null, place);
        }
      }
    }
    else {
      PyImportElement[] importElements = getImportElements();
      for(PyImportElement element: importElements) {
        if (!element.processDeclarations(processor, state, lastParent, place)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    PyPsiUtils.deleteAdjacentComma(this, child, getImportElements());
    super.deleteChildInternal(child);
  }
}
