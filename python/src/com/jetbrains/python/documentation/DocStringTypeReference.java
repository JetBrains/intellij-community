package com.jetbrains.python.documentation;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User : catherine
 */
public class DocStringTypeReference extends PsiReferenceBase<PsiElement> {
  private PyType myType;
  private TextRange myFullRange;

  public DocStringTypeReference(PsiElement element, TextRange range, TextRange fullRange, @Nullable PyType type) {
    super(element, range);
    myFullRange = fullRange;
    myType = type;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element.equals(resolve())) {
      return element;
    }
    if (myElement instanceof PyStringLiteralExpression && element instanceof PyClass) {
      final PyStringLiteralExpression e = (PyStringLiteralExpression)myElement;
      final PyClass cls = (PyClass)element;
      PyQualifiedName qname = QualifiedNameFinder.findCanonicalImportPath(cls, element);
      if (qname != null) {
        qname = qname.append(cls.getName());
        ElementManipulator<PyStringLiteralExpression> manipulator = ElementManipulators.getManipulator(e);
        myType = new PyClassTypeImpl(cls, false);
        return manipulator.handleContentChange(e, myFullRange, qname.toString());
      }
    }
    return null;
  }

  public boolean isSoft() {
    return true;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    if (newElementName.endsWith(PyNames.DOT_PY)) {
      newElementName = newElementName.substring(0, newElementName.length() - PyNames.DOT_PY.length());
    }
    return super.handleElementRename(newElementName);
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (myType instanceof PyImportedModuleType) {
      return element.equals(PyUtil.turnInitIntoDir(resolve()));
    }
    return super.isReferenceTo(element);
  }

  @Nullable
  public PsiElement resolve() {
    if (myType instanceof PyClassType) {
      return ((PyClassType)myType).getPyClass();
    }
    if (myType instanceof PyImportedModuleType) {
      final PyImportedModule module = ((PyImportedModuleType)myType).getImportedModule();
      return module.resolve();
    }
    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final PsiFile file = myElement.getContainingFile();
    final ArrayList variants = Lists.newArrayList("str", "int", "basestring", "bool", "buffer", "bytearray", "complex", "dict",
                                                  "tuple", "enumerate", "file", "float", "frozenset", "list", "long", "set", "object");
    if (file instanceof PyFile) {
      variants.addAll(((PyFile)file).getTopLevelClasses());
      final List<PyFromImportStatement> fromImports = ((PyFile)file).getFromImports();
      for (PyFromImportStatement fromImportStatement : fromImports) {
        final PyImportElement[] elements = fromImportStatement.getImportElements();
        for (PyImportElement element : elements) {
          final PyReferenceExpression referenceExpression = element.getImportReferenceExpression();
          if (referenceExpression == null) continue;
          final PyType type = referenceExpression.getType(TypeEvalContext.fast());
          if (type instanceof PyClassType) {
            variants.add(((PyClassType)type).getPyClass());
          }
        }
      }
    }

    return variants.toArray();
  }
}
