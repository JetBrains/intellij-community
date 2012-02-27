package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.EmptyIterable;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * The "import foo" or "import foo as bar" parts.
 *
 * @author yole
 */
public class PyImportElementImpl extends PyBaseElementImpl<PyImportElementStub> implements PyImportElement {
  public PyImportElementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyImportElementImpl(PyImportElementStub stub) {
    this(stub, PyElementTypes.IMPORT_ELEMENT);
  }

  public PyImportElementImpl(PyImportElementStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Nullable
  public PyReferenceExpression getImportReferenceExpression() {
    final ASTNode node = getNode().findChildByType(PyElementTypes.REFERENCE_EXPRESSION);
    return node == null ? null : (PyReferenceExpression) node.getPsi();
  }

  public PyQualifiedName getImportedQName() {
    final PyImportElementStub stub = getStub();
    if (stub != null) {
      return stub.getImportedQName();
    }
    final PyReferenceExpression importReference = getImportReferenceExpression();
    return importReference != null ? importReference.asQualifiedName() : null;
  }

  public PyTargetExpression getAsNameElement() {
    final ASTNode asNameNode = getNode().findChildByType(PyElementTypes.TARGET_EXPRESSION);
    if (asNameNode == null) return null;
    return (PyTargetExpression)asNameNode.getPsi();
  }

  public String getAsName() {
    final PyImportElementStub stub = getStub();
    if (stub != null) {
      final String asName = stub.getAsName();
      return StringUtil.isEmpty(asName) ? null : asName;
    }
    final PyTargetExpression element = getAsNameElement();
    return element != null ? element.getName() : null;
  }

  @Nullable
  public String getVisibleName() {
    final PyImportElementStub stub = getStub();
    if (stub != null) {
      final String asName = stub.getAsName();
      if (!StringUtil.isEmpty(asName)) {
        return asName;
      }
      final PyQualifiedName importedName = stub.getImportedQName();
      if (importedName != null && importedName.getComponentCount() > 0) {
        return importedName.getComponents().get(0);
      }
    }
    else {
      PyTargetExpression asNameElement = getAsNameElement();
      if (asNameElement != null) {
        return asNameElement.getName();
      }
      final PyQualifiedName importedName = getImportedQName();
      if (importedName != null && importedName.getComponentCount() > 0) {
        return importedName.getComponents().get(0);
      }
    }
    return null; // we might have not found any names
  }

  @Nullable
  public PyStatement getContainingImportStatement() {
    final PyImportElementStub stub = getStub();
    PsiElement parent;
    if (stub != null) {
      parent = stub.getParentStub().getPsi();
    }
    else {
      parent = getParent();
    }
    return parent instanceof PyStatement ? (PyStatement)parent : null;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {

      @NotNull
      private String getRefName(String default_name) {
        PyReferenceExpression ref = getImportReferenceExpression();
        if (ref != null) {
          String refName = ref.getName();
          if (refName != null) return refName;
        }
        return default_name;
      }

      public String getPresentableText() {
        return getRefName("<none>");
      }

      public String getLocationString() {
        PyElement elt = PsiTreeUtil.getParentOfType(PyImportElementImpl.this, PyImportStatement.class, PyFromImportStatement.class);
        final StringBuilder buf = new StringBuilder("| ");
        if (elt != null) { // always? who knows :)
          if (elt instanceof PyFromImportStatement) { // from ... import ...
            buf.append("from ");
            PyReferenceExpression imp_src = ((PyFromImportStatement)elt).getImportSource();
            if (imp_src != null) {
              buf.append(PyResolveUtil.toPath(imp_src));
            }
            else {
              buf.append("<?>");
            }
            buf.append(" import ");
          }
          else { // "import ... "
            buf.append("import ");
          }
          buf.append(getRefName("<?>"));
        }
        else {
          buf.append("import?.. ");
        }
        // are we the name or the 'as'?
        PyTargetExpression as_part = getAsNameElement();
        if (as_part != null) {
          buf.append(" as ").append(as_part.getName());
        }
        return buf.toString();
      }

      public Icon getIcon(final boolean open) {
        return null;
      }
    };
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    PyElement ret = getAsNameElement();
    if (ret == null) {
      final PyReferenceExpression importReference = getImportReferenceExpression();
      if (importReference != null) {
        final List<PyExpression> qualifiers = PyResolveUtil.unwindQualifiers(importReference);
        if (qualifiers.size() > 0) {
          ret = qualifiers.get(0);
        }
      }
    }
    if (ret == null) {
      return EmptyIterable.getInstance();
    }
    return Collections.singleton(ret);
  }

  public PsiElement getElementNamed(final String name) {
    return getElementNamed(name, true);
  }

  @Override
  public PsiElement getElementNamed(String name, boolean resolveImportElement) {
    String asName = getAsName();
    if (asName != null) {
      if (!Comparing.equal(name, asName)) return null;
      // [yole] I'm not sure why we always resolve the module in this branch but the tests seem to rely on that
      return ResolveImportUtil.resolveImportElement(this);
    }
    else {
      final PyQualifiedName qName = getImportedQName();
      if (qName == null || qName.getComponentCount() == 0 || !qName.getComponents().get(0).equals(name)) {
        return null;
      }
      if (qName.getComponentCount() == 1) {
        return resolveImportElement ? ResolveImportUtil.resolveImportElement(this, PyQualifiedName.fromComponents(name)) : this;
      }
      final PsiNamedElement container = getStubOrPsiParentOfType(PsiNamedElement.class);
      return new PyImportedModule(this, container, PyQualifiedName.fromComponents(name));
    }
  }

  public boolean mustResolveOutside() {
    return true; // formally
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyImportElement(this);
  }

  @Override
  public String toString() {
    final PyQualifiedName qName = getImportedQName();
    return String.format("%s:%s", super.toString(), qName != null ? qName : "null");
  }
}
