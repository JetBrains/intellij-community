package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.TokenType;
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

  @NotNull
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

    int result = 0;
    ASTNode seeker = getNode().getFirstChildNode();
    while(seeker != null && (seeker.getElementType() == PyTokenTypes.FROM_KEYWORD || seeker.getElementType() == TokenType.WHITE_SPACE)) {
      seeker = seeker.getTreeNext();
    }
    while (seeker != null && seeker.getElementType() == PyTokenTypes.DOT) {
      result++;
      seeker = seeker.getTreeNext();
    }
    return result;
  }

  public boolean isFromFuture() {
    final PyQualifiedName qName = getImportSourceQName();
    return qName != null && qName.matches(PyNames.FUTURE_MODULE);
  }

  @Override
  public PsiElement getLeftParen() {
    return findChildByType(PyTokenTypes.LPAR);
  }

  @Override
  public PsiElement getRightParen() {
    return findChildByType(PyTokenTypes.RPAR);
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
        final List<PsiElement> targets = ResolveImportUtil.resolveImportReference(expr);
        for (PsiElement target : targets) {
          final PsiElement importedFile = PyUtil.turnDirIntoInit(target);
          if (importedFile != null) {
            if (!importedFile.processDeclarations(processor, state, null, place)) {
              return false;
            }
          }
        }
      }
    }
    else {
      PyImportElement[] importElements = getImportElements();
      for(PyImportElement element: importElements) {
        final PsiElement resolved = ResolveImportUtil.resolveImportElement(element);
        if (resolved != null && !processor.execute(resolved, state)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    if (anchor == null) {
      // adding last element; the import may be "from ... import (...)", must get before the last ")"
      PsiElement last_child = getLastChild();
      if (last_child != null) {
        ASTNode rpar_node = last_child.getNode();
        if (rpar_node != null && rpar_node.getElementType() == PyTokenTypes.RPAR) anchor = rpar_node;
      }
    }
    final ASTNode result = super.addInternal(first, last, anchor, before);
    ASTNode prevNode = result;
    do {
      prevNode = prevNode.getTreePrev();
    } while(prevNode != null && prevNode.getElementType() == TokenType.WHITE_SPACE);

    if (prevNode != null && prevNode.getElementType() == PyElementTypes.IMPORT_ELEMENT &&
        result.getElementType() == PyElementTypes.IMPORT_ELEMENT) {
      ASTNode comma = PyElementGenerator.getInstance(getProject()).createComma();
      super.addInternal(comma, comma, prevNode, false);
    }
    
    return result;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    PyPsiUtils.deleteAdjacentComma(this, child, getImportElements());
    super.deleteChildInternal(child);
  }
}
