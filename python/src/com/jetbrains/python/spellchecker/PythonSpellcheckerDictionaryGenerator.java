package com.jetbrains.python.spellchecker;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spellchecker.generator.SpellCheckerDictionaryGenerator;
import com.intellij.spellchecker.inspections.CheckArea;
import com.intellij.spellchecker.inspections.SplitterFactory;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;

import java.util.HashSet;
import java.util.List;

/**
 * @author yole
 */
public class PythonSpellcheckerDictionaryGenerator extends SpellCheckerDictionaryGenerator {
  public PythonSpellcheckerDictionaryGenerator(final Project project, final String dictOutputFolder) {
    super(project, dictOutputFolder, "python");
  }

  @Override
  protected void processFolder(HashSet<String> seenNames, PsiManager manager, VirtualFile folder) {
    if (!myExcludedFolders.contains(folder)) {
      List<CheckArea> checkAreas = SplitterFactory.getInstance().getIdentifierSplitter().split(folder.getName());
      if (checkAreas != null) {
        processCheckAreas(checkAreas, seenNames);
      }
    }
    super.processFolder(seenNames, manager, folder);
  }

  @Override
  protected void processFile(PsiFile file, final HashSet<String> seenNames) {
    file.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyFunction(PyFunction node) {
        super.visitPyFunction(node);
        processLeafsNames(node, seenNames);
      }

      @Override
      public void visitPyClass(PyClass node) {
        super.visitPyClass(node);
        processLeafsNames(node, seenNames);
      }

      @Override
      public void visitPyTargetExpression(PyTargetExpression node) {
        super.visitPyTargetExpression(node);
        if (PsiTreeUtil.getParentOfType(node, ScopeOwner.class) instanceof PyFile) {
          processLeafsNames(node, seenNames);
        }
      }
    });
  }
}
