package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.light.LightElement;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.NameDefiner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class PyImportedModule extends LightElement implements NameDefiner {
  private final PyFile myContainingFile;
  private final PyQualifiedName myImportedPrefix;

  public PyImportedModule(PyFile containingFile, PyQualifiedName importedPrefix) {
    super(containingFile.getManager(), PythonLanguage.getInstance());
    myContainingFile = containingFile;
    myImportedPrefix = importedPrefix;
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    throw new UnsupportedOperationException();
  }

  public PsiElement getElementNamed(String the_name) {
    PyQualifiedName prefix = myImportedPrefix.append(the_name);
    final List<PyImportElement> imports = ((PyFileImpl)myContainingFile).getImportTargets();
    for (PyImportElement anImport : imports) {
      final PyQualifiedName qName = anImport.getImportedQName();
      if (qName != null && matchesPrefix(qName, prefix)) {
        if (qName.getComponentCount() == prefix.getComponentCount()) {
          return anImport;
        }
        return new PyImportedModule(myContainingFile, prefix);
      }
    }
    return null;
  }

  private static boolean matchesPrefix(PyQualifiedName qName, PyQualifiedName prefix) {
    if (qName.getComponentCount() < prefix.getComponentCount()) {
      return false;
    }
    for (int i = 0; i < prefix.getComponentCount(); i++) {
      if (!qName.getComponents().get(i).equals(prefix.getComponents().get(i))) {
        return false;
      }
    }
    return true;
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
}
