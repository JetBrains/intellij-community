package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.EmptyIterable;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyAsScopeProcessor;
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
    super(stub, PyElementTypes.IMPORT_ELEMENT);
  }

  @Nullable
  public PyReferenceExpression getImportReference() {
    final ASTNode importRefNode = getNode().findChildByType(PyElementTypes.REFERENCE_EXPRESSION);
    if (importRefNode == null) return null;
    return (PyReferenceExpression)importRefNode.getPsi();
  }

  public PyQualifiedName getImportedQName() {
    final PyImportElementStub stub = getStub();
    if (stub != null) {
      return stub.getImportedQName();
    }
    final PyReferenceExpression importReference = getImportReference();
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
      return importedName != null ? importedName.getLastComponent() : null;
    }
    PyTargetExpression asname = getAsNameElement();
    if (asname != null) return asname.getName();
    final PyReferenceExpression import_ref = getImportReference();
    if (import_ref != null) return PyResolveUtil.toPath(import_ref, ".");
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
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor, @NotNull final ResolveState state, final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    // import is per-file
    if (place.getContainingFile() != getContainingFile()) {
      return true;
    }
    final PyReferenceExpression importRef = getImportReference();
    if (importRef != null) {
      final PsiElement element = importRef.getReference().resolve();
      if (element != null) {
        if (processor instanceof PyAsScopeProcessor) {
          PyTargetExpression asName = getAsNameElement();
          if (asName != null) {
            return ((PyAsScopeProcessor) processor).execute(element, asName.getText()); // might resolve to asName to show the source of name
          }
          // maybe the incoming name is qualified
          PyReferenceExpression place_ref = PsiTreeUtil.getChildOfType(place, PyReferenceExpression.class);
          if (place_ref != null) {
            // unfold all qualifiers of import and reference and compare them
            List<PyReferenceExpression> place_path = PyResolveUtil.unwindQualifiers(place_ref);
            List<PyReferenceExpression> ref_path = PyResolveUtil.unwindQualifiers(importRef);
            if (PyResolveUtil.pathsMatch(place_path, ref_path)) {
              assert ref_path != null; // extraneous, but makes npe inspection happy
              for (PyReferenceExpression rex: ref_path) { // the thing the processor is looking for must be somewhere here
                final PsiElement elt = rex.getReference().resolve();
                if (!processor.execute(elt, state)) return false;
              }
              return true; // none matched
            }
          }
        }
        return processor.execute(element, state);
      }
    }
    return true;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {

      @NotNull
      private String getRefName(String default_name) {
        PyReferenceExpression ref = getImportReference();
        if (ref != null) {
          String refname = ref.getName();
          if (refname != null) return refname;
        }
        return default_name;
      }

      public String getPresentableText() {
        return getRefName("<none>");
      }

      public String getLocationString() {
        PyElement elt = PsiTreeUtil.getParentOfType(PyImportElementImpl.this, PyImportStatement.class, PyFromImportStatement.class);
        StringBuffer buf = new StringBuffer("| ");
        if (elt != null) { // always? who knows :)
          if (elt instanceof PyFromImportStatement) { // from ... import ...
            buf.append("from ");
            PyReferenceExpression imp_src = ((PyFromImportStatement)elt).getImportSource();
            if (imp_src != null) {
              buf.append(PyResolveUtil.toPath(imp_src, "."));
            }
            else buf.append("<?>");
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

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    PyElement ret = getAsNameElement();
    if (ret == null) {
      List<PyReferenceExpression> unwound_path = PyResolveUtil.unwindQualifiers(getImportReference());
      if ((unwound_path != null) && (unwound_path.size() > 0)) ret = unwound_path.get(0); 
    }
    if (ret == null) {
      return EmptyIterable.getInstance();
    }
    return Collections.singleton(ret);
  }

  public PsiElement getElementNamed(final String the_name) {
    String asName = getAsName();
    if (asName != null) {
      if (!Comparing.equal(the_name, asName)) return null;
      return ResolveImportUtil.resolveImportElement(this);
    }
    else {
      final PyQualifiedName qName = getImportedQName();
      if (qName == null || qName.getComponentCount() == 0 || !qName.getComponents().get(0).equals(the_name)) {
        return null;
      }
      if (qName.getComponentCount() == 1) {
        return ResolveImportUtil.resolveImportElement(this, PyQualifiedName.fromComponents(the_name));
      }
      return new PyImportedModule((PyFile) getContainingFile(), PyQualifiedName.fromComponents(the_name));
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
    return "PyImportElement:" + (qName != null ? qName : "null");
  }
}
