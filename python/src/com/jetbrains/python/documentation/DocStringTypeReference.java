package com.jetbrains.python.documentation;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyImportedModuleType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

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
      PyQualifiedName qname = ResolveImportUtil.findCanonicalImportPath(cls, element);
      if (qname != null) {
        qname = qname.append(cls.getName());
        ElementManipulator<PyStringLiteralExpression> manipulator = ElementManipulators.getManipulator(e);
        myType = new PyClassType(cls, false);
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
    }

    return variants.toArray();
  }
}
