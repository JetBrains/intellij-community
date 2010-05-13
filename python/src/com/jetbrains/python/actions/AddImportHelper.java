package com.jetbrains.python.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonDocStringFinder;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * Does the actual job of adding an import statement into a file.
 * User: dcheryasov
 * Date: Apr 24, 2009 3:17:59 AM
 */
public class AddImportHelper {
  private static final Logger LOG = Logger.getInstance("#" + AddImportHelper.class.getName());

  private AddImportHelper() {
  }

  private static PsiElement getInsertPosition(final PsiFile file) {
    PsiElement feeler = file.getFirstChild();
    LOG.assertTrue(feeler != null);
    // skip initial comments and whitespace and try to get just below the last import stmt
    boolean skipped_over_imports = false;
    boolean skipped_over_doc = false;
    PsiElement seeker = feeler;
    do {
      if (PyUtil.instanceOf(feeler, PyImportStatement.class, PyFromImportStatement.class)) {
        seeker = feeler;
        feeler = feeler.getNextSibling();
        skipped_over_imports = true;
      }
      else if (PyUtil.instanceOf(feeler, PsiWhiteSpace.class, PsiComment.class)) {
        seeker = feeler;
        feeler = feeler.getNextSibling();
      }
      // maybe we arrived at the doc comment stmt; skip over it, too
      else if (!skipped_over_imports && ! skipped_over_doc && file instanceof PyFile) {
        PsiElement doc_elt = PythonDocStringFinder.find((PyElement)file); // this gives the literal; its parent is the expr seeker may have encountered
        if (doc_elt != null && doc_elt.getParent() == feeler) {
          feeler = feeler.getNextSibling();
          seeker = feeler; // skip over doc even if there's nothing below it
          skipped_over_doc = true;
        }
        else break; // not a doc comment, stop on it
      }
      else break; // some other statement, stop
    } while (feeler != null);
    return seeker;
  }

  /**
   * Adds an import statement, presumably below all other initial imports in the file.
   * @param file where to operate
   * @param name which to import (qualified is OK)
   * @param asName optional name for 'as' clause
   * @param project to which the file presumably belongs
   */
  public static void addImportStatement(PsiFile file, String name, @Nullable String asName) {
    String as_clause;
    if (asName == null) as_clause = "";
    else as_clause = " as " + asName;
    final PyImportStatement importNodeToInsert = PyElementGenerator.getInstance(file.getProject()).createImportStatementFromText(
        "import " + name + as_clause + "\n\n");
    try {
      file.addBefore(importNodeToInsert, getInsertPosition(file));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  /**
   * Adds an "import ... from ..." statement below other top-level imports.
   * @param file where to operate
   * @param from name of the module
   * @param name imported name
   * @param asName optional name for 'as' clause
   * @param project where the file belongs
   */
  public static  void addImportFromStatement(PsiFile file, String from, String name, @Nullable String asName) {
    String as_clause;
    if (asName == null) as_clause = "";
    else as_clause = " as " + asName;
    final PyFromImportStatement importNodeToInsert = PyElementGenerator.getInstance(file.getProject()).createFromText(
        PyFromImportStatement.class, "from " + from + " import " + name + as_clause + "\n\n");
    try {
      file.addBefore(importNodeToInsert, getInsertPosition(file));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

}
