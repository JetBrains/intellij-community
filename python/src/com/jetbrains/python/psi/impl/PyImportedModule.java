package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.light.LightElement;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class PyImportedModule extends LightElement implements NameDefiner {
  private final PyFileImpl myContainingFile;
  private final PyQualifiedName myImportedPrefix;

  public PyImportedModule(PyFile containingFile, PyQualifiedName importedPrefix) {
    super(containingFile.getManager(), PythonLanguage.getInstance());
    myContainingFile = (PyFileImpl)containingFile;
    myImportedPrefix = importedPrefix;
  }

  public PyFile getContainingFile() {
    return myContainingFile;
  }

  public PyQualifiedName getImportedPrefix() {
    return myImportedPrefix;
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    throw new UnsupportedOperationException();
  }

  public PsiElement getElementNamed(String the_name) {
    PyQualifiedName prefix = myImportedPrefix.append(the_name);
    final PyImportElement importElement = findMatchingImportElement(prefix);
    if (importElement != null) {
      final PyQualifiedName qName = importElement.getImportedQName();
      if (qName != null && qName.getComponentCount() == prefix.getComponentCount()) {
        return resolve(importElement, prefix);
      }
      return new PyImportedModule(myContainingFile, prefix);
    }
    final PyImportElement fromImportElement = findMatchingFromImport(myImportedPrefix, the_name);
    if (fromImportElement != null) {
      return ResolveImportUtil.resolveImportElement(fromImportElement);
    }

    return null;
  }

  @Nullable
  private PyImportElement findMatchingFromImport(PyQualifiedName prefix, String name) {
    final List<PyFromImportStatement> fromImports = myContainingFile.getFromImports();
    for (PyFromImportStatement fromImport : fromImports) {
      final PyQualifiedName qName = fromImport.getImportSourceQName();
      if (prefix.equals(qName)) {
        final PyImportElement[] importElements = fromImport.getImportElements();
        for (PyImportElement importElement : importElements) {
          final PyQualifiedName importedName = importElement.getImportedQName();
          if (importedName != null && importedName.matches(name)) {
            return importElement;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private PyImportElement findMatchingImportElement(PyQualifiedName prefix) {
    PyImportElement result = findMatchingImportElementInList(prefix, myContainingFile.getImportTargets());
    if (result == null) {
      result = findMatchingImportElementInList(prefix, myContainingFile.getImportTargetsTransitive());
    }
    return result;
  }

  @Nullable
  private static PyImportElement findMatchingImportElementInList(PyQualifiedName prefix, final List<PyImportElement> imports) {
    for (PyImportElement anImport : imports) {
      final PyQualifiedName qName = anImport.getImportedQName();
      if (qName != null && qName.matchesPrefix(prefix)) {
        return anImport;
      }
    }
    return null;
  }

  public boolean mustResolveOutside() {
    return true;
  }

  public String getText() {
    return "import " + myImportedPrefix;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public PsiElement copy() {
    return new PyImportedModule(myContainingFile, myImportedPrefix);
  }

  @Override
  public String toString() {
    return "PyImportedModule:" + myImportedPrefix;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    final PyImportElement importElement = findMatchingImportElement(myImportedPrefix);
    if (importElement != null) {
      final PsiElement element = resolve(importElement, myImportedPrefix);
      if (element != null) {
        return element;
      }
    }
    return super.getNavigationElement();
  }

  @Nullable
  private PsiElement resolve(PyImportElement importElement, final PyQualifiedName prefix) {
    return PyUtil.turnDirIntoInit(ResolveImportUtil.resolveImportElement(importElement, prefix));
  }
}
