package com.jetbrains.python.codeInsight.imports;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.impl.ParamHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An immutable holder of information for one auto-import candidate.
 * User: dcheryasov
 * Date: Apr 23, 2009 4:17:50 PM
 */
// visibility is intentionally package-level
class ImportCandidateHolder implements Comparable {
  private final PsiElement myImportable;
  private final PyImportElement myImportElement;
  private final PsiFileSystemItem myFile;
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
    @NotNull PsiElement importable, @NotNull PsiFileSystemItem file,
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

  public PsiFileSystemItem getFile() {
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
    StringBuilder sb = new StringBuilder();
    if (source != null) {
      PsiElement parent = source.getParent();
      if (parent instanceof PyFromImportStatement) {
        sb.append(name);
      }
      else {
        sb.append(source.getVisibleName()).append(".").append(name);
      }
    }
    else {
      if (!StringUtil.isEmpty(importPath)) {
        sb.append(importPath).append(".");
      }
      sb.append(name);
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
      ParamHelper.appendParameterList(((PyFunction)myImportable).getParameterList(), sb);
    }
    else if (myImportable instanceof PyClass) {
      PyClass[] supers = ((PyClass)myImportable).getSuperClasses();
      if (supers.length > 0) {
        sb.append("(");
        // ", ".join(x.getName() for x in getSuperClasses())
        String[] super_names = new String[supers.length];
        for (int i=0; i < supers.length; i += 1) super_names[i] = supers[i].getName();
        sb.append(StringUtil.join(super_names, ", "));
        sb.append(")");
      }
    }
    if (parent instanceof PyFromImportStatement) {
      sb.append(" from ").append(((PyFromImportStatement)parent).getImportSource().getReferencedName()); // no NPE, we won't add a sourceless import stmt
    }
    return sb.toString();
  }

  @Nullable
  public String getTailText() {
    if (myImportElement == null) {
     String text = "add import";
      if (myAsName != null) {
        text += " as " + myAsName;
      }
      return text;
    }
    return null;
  }

  public int compareTo(Object o) {
    ImportCandidateHolder rhs = (ImportCandidateHolder) o;
    return rhs.getRelevance() - getRelevance();
  }

  int getRelevance() {
    Project project = myImportable.getProject();
    final PsiFile psiFile = myImportable.getContainingFile();
    final VirtualFile vFile = psiFile == null ? null : psiFile.getVirtualFile();
    if (vFile == null) return 0;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    // files under project source are most relevant
    final Module module = fileIndex.getModuleForFile(vFile);
    if (module != null) return 3;
    // then come files directly under Lib
    if (vFile.getParent().getName().equals("Lib")) return 2;
    // tests we don't want
    if (vFile.getParent().getName().equals("test")) return 0;
    return 1;
  }
}
