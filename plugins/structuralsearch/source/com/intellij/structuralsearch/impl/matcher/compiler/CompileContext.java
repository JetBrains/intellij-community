package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import gnu.trove.THashMap;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 17.11.2004
 * Time: 19:26:37
 * To change this template use File | Settings | File Templates.
 */
class CompileContext {
  PsiSearchHelper helper;
  THashMap<PsiFile,PsiFile> filesToScan;
  THashMap<PsiFile,PsiFile> filesToScan2;
  HashMap<String,String> scanned;
  HashMap<String,String> scannedComments;
  HashMap<String,String> scannedLiterals;
  int scanRequest;

  boolean findMatchingFiles;
  CompiledPattern pattern;
  MatchOptions options;
  Project project;

  public boolean isScannedSomething() {
    return scanned.size() > 0 || scannedComments.size() > 0 || scannedLiterals.size() > 0;
  }
}
