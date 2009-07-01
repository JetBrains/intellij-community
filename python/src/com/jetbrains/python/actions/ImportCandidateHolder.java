package com.jetbrains.python.actions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An immutable holder of information for one auto-import candidate.
 * User: dcheryasov
 * Date: Apr 23, 2009 4:17:50 PM
 */
// visibility is intentionally package-level
class ImportCandidateHolder {
  private final PsiElement myImportable;
  private final PyImportElement myImportElement;
  private final PsiFile myFile;
  private final String myPath;
  private final String myAsName;

  /**
   * Creates new instance.
   * @param importable an element that could be imported either from import element or from file.
   * @param file the file which is the source of the importable
   * @param importElement an existing import element that can be a source for the importable.
   * @param path import path for the file, as a qualified name (a.b.c)
   * @param asName name to use in a new import statement for 'as' clause, if an import is added.
   */
  public ImportCandidateHolder(
    @NotNull PsiElement importable, @NotNull PsiFile file,
    @Nullable PyImportElement importElement, @Nullable String path, @Nullable String asName
  ) {
    myFile = file;
    myImportable = importable;
    myImportElement = importElement;
    myPath = path;
    myAsName = asName;
    assert importElement != null || path != null; // one of these must be present 
  }

  public PsiElement getImportable() {
    return myImportable;
  }

  public PyImportElement getImportElement() {
    return myImportElement;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public String getPath() {
    return myPath;
  }

  public String getAsName() {
    return myAsName;
  }

  /**
   * Helper method that builds an import path, handling all these "import foo", "import foo as bar", "from bar import foo", etc.
   * Either importPath or importSource must be not null.
   * @param name what is ultimately imported.
   * @param importPath known path to import the name.
   * @param source known ImportElement to import the name; its 'as' clause is used if present.
   * @return a properly qualified name.
   */
  public static String getQualifiedName(String name, String importPath, PyImportElement source) {
    StringBuffer sb = new StringBuffer();
    PsiElement parent = null;
    if (source != null) {
      parent = source.getParent();
      if (parent instanceof PyFromImportStatement) {
        sb.append(name);
      }
      else {
        sb.append(source.getVisibleName()).append(".").append(name);
      }
    }
    else {
      sb.append(importPath).append(".").append(name);
    }
    return sb.toString();
  }

  public String getPresentableText(String myName) {
    StringBuilder sb = new StringBuilder(getQualifiedName(myName, myPath, myImportElement));
    PsiElement parent = null;
    if (myImportElement != null) {
      parent = myImportElement.getParent();
    }
    if (myImportable instanceof PyFunction) {
      sb.append("(");
      // below: ", ".join([x.getRepr(False) for x in getParameters()])
      PyParameter[] params = ((PyFunction)myImportable).getParameterList().getParameters();
      String[] param_reprs = new String[params.length];
      for (int i=0; i < params.length; i += 1) param_reprs[i] = params[i].getRepr(false);
      PyUtil.joinSubarray(param_reprs, 0, params.length, ", ", sb);
      sb.append(")");
    }
    else if (myImportable instanceof PyClass) {
      PyClass[] supers = ((PyClass)myImportable).getSuperClasses();
      if (supers.length > 0) {
        sb.append("(");
        // ", ".join(x.getName() for x in getSuperClasses())
        String[] super_names = new String[supers.length];
        for (int i=0; i < supers.length; i += 1) super_names[i] = supers[i].getName();
        PyUtil.joinSubarray(super_names, 0, supers.length, ", ", sb);
        sb.append(")");
      }
    }
    if (parent instanceof PyFromImportStatement) {
      sb.append(" from ").append(((PyFromImportStatement)parent).getImportSource().getReferencedName()); // no NPE, we won't add a sourceless import stmt
    }
    else if (myImportElement == null) { // no import, only file
      sb.append(" # add import");
      if (myAsName != null) sb.append(" as ").append(myAsName);
    }
    return sb.toString();
  }
}
