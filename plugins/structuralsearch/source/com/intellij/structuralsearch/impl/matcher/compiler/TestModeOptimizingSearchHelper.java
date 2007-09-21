package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiFile;

import java.util.Collections;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class TestModeOptimizingSearchHelper extends OptimizingSearchHelperBase {
  private static String lastString;
  private StringBuilder builder = new StringBuilder();
  private int lastLength;

  TestModeOptimizingSearchHelper(CompileContext _context) {
    super(_context);
  }

  public boolean doOptimizing() {
    return true;
  }

  public void clear() {
    lastString = builder.toString();
    builder.setLength(0);
    lastLength = 0;
  }

  protected void doAddSearchJavaReservedWordInCode(final String refname) {
    append(refname, "reserved in code:");
  }

  private void append(final String refname, final String str) {
    if (builder.length() == lastLength) builder.append("[");
    else builder.append("|");
    builder.append(str).append(refname);
  }

  protected void doAddSearchWordInCode(final String refname) {
    append(refname, "in code:");
  }

  protected void doAddSearchWordInComments(final String refname) {
    append(refname, "in comments:");
  }

  protected void doAddSearchWordInLiterals(final String refname) {
    append(refname, "in literals:");
  }

  public void endTransaction() {
    super.endTransaction();
    builder.append("]");
    lastLength = builder.length();
  }

  public boolean addDescendantsOf(final String refname, final boolean subtype) {
    append(refname,"descendants");
    return false;
  }

  public boolean isScannedSomething() {
    return false;
  }

  public Set<PsiFile> getFilesSetToScan() {
    return Collections.emptySet();
  }

  public String getSearchPlan() {
    return lastString;
  }
}
