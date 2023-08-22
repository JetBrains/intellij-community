// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.spellchecker;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spellchecker.generator.SpellCheckerDictionaryGenerator;
import com.intellij.spellchecker.inspections.IdentifierSplitter;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;


public class PythonSpellcheckerDictionaryGenerator extends SpellCheckerDictionaryGenerator {
  public PythonSpellcheckerDictionaryGenerator(final Project project, final String dictOutputFolder) {
    super(project, dictOutputFolder, "python");
  }

  @Override
  protected void processFolder(final HashSet<String> seenNames, PsiManager manager, VirtualFile folder) {
    if (!myExcludedFolders.contains(folder)) {
      final String name = folder.getName();
      IdentifierSplitter.getInstance().split(name, TextRange.allOf(name), textRange -> {
        final String word = textRange.substring(name);
        addSeenWord(seenNames, word, Language.ANY);
      });
    }
    super.processFolder(seenNames, manager, folder);
  }

  @Override
  protected void processFile(PsiFile file, final HashSet<String> seenNames) {
    file.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyFunction(@NotNull PyFunction node) {
        super.visitPyFunction(node);
        processLeafsNames(node, seenNames);
      }

      @Override
      public void visitPyClass(@NotNull PyClass node) {
        super.visitPyClass(node);
        processLeafsNames(node, seenNames);
      }

      @Override
      public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
        super.visitPyTargetExpression(node);
        if (PsiTreeUtil.getParentOfType(node, ScopeOwner.class) instanceof PyFile) {
          processLeafsNames(node, seenNames);
        }
      }
    });
  }
}
