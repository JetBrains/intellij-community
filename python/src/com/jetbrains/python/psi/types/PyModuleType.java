package com.jetbrains.python.psi.types;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.resolve.VariantsProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
// .impl looks impure

/**
 * @author yole
 */
public class PyModuleType implements PyType { // Maybe make it a PyClassType referring to builtins.___module or suchlike.
  private final PsiFile myModule;

  protected static Set<String> ourPossibleFields;

  static {
    ourPossibleFields = new HashSet<String>();
    /*ourPossibleFields.addAll(PyObjectType.ourPossibleFields);*/
    ourPossibleFields.add("__name__");
    ourPossibleFields.add("__file__");
    ourPossibleFields.add("__path__");
    ourPossibleFields = Collections.unmodifiableSet(ourPossibleFields); 
  }

  public PyModuleType(PsiFile source) {
    myModule = source;
  }
  
  public PsiFile getModule() {
    return myModule;
  }

  public PsiElement resolveMember(final String name) {
    //return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(name), myModule, null, null);
    return ResolveImportUtil.resolveChild(myModule, name, null, false);
  }


  /**
   * @return a list of submodules of this module, either files or dirs, for aesier naming.
   */
  @NotNull
  public List<PsiFileSystemItem> getSubmodulesList() {
    List<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();

    if (PyNames.INIT_DOT_PY.equals(myModule.getName())) { // our module is a dir, not a single file
      PsiDirectory mydir = myModule.getContainingDirectory();
      if (mydir != null) { // just in case
        // file modules
        for (PsiFile f : mydir.getFiles()) {
          if (f instanceof PyFile && !f.getName().equals(PyNames.INIT_DOT_PY)) result.add(f);
        }
        // dir modules
        for (PsiDirectory dir : mydir.getSubdirectories()) {
          if (dir.findFile(PyNames.INIT_DOT_PY) instanceof PyFile) result.add(dir);
        }
      }
    }
    return result;
  }

  public Object[] getCompletionVariants(final PyReferenceExpression referenceExpression, ProcessingContext context) {
    Set<String> names_already = context.get(PyType.CTX_NAMES);
    List<Object> result = new ArrayList<Object>();
    if (PsiTreeUtil.getParentOfType(referenceExpression, PyImportElement.class) == null) { // we're not in an import
      final VariantsProcessor processor = new VariantsProcessor();
      myModule.processDeclarations(processor, ResolveState.initial(), null, referenceExpression);
      if (names_already != null) {
        for (LookupElement le : processor.getResultList()) {
          String name = le.getLookupString();
          if (!names_already.contains(name)) {
            result.add(le);
            names_already.add(name);
          }
        }
      }
      else result.addAll(processor.getResultList());
    }
    for (PsiFileSystemItem pfsi : getSubmodulesList()) {
      String s = pfsi.getName();
      int pos = s.lastIndexOf('.'); // it may not contain a dot, except in extension; cut it off.
      if (pos > 0) s = s.substring(0, pos);
      if (!PyNames.isIdentifier(s)) continue; // file is e.g. a script with a strange name, not a module
      if (names_already != null) {
        if (names_already.contains(s)) continue;
        else names_already.add(s);
      }
      result.add(LookupElementBuilder.create(pfsi, s).setPresentableText(s));
    }
    return result.toArray();
  }

  public String getName() {
    PsiFile mod = getModule();
    if (mod != null) return mod.getName();
    else return null;
  }

  @NotNull
  public static Set<String> getPossibleInstanceMembers() {
    return ourPossibleFields;
  }
  
}
