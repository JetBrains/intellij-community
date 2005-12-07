/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.refactoring.rename.inplace;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author ven
 */
public class VariableInplaceRenamer {

  private PsiVariable myElementToRename;
  @NonNls private static final String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  @NonNls private static final String OTHER_VARIABLE_NAME = "OtherVariable";

  public VariableInplaceRenamer(PsiVariable elementToRename) {
    myElementToRename = elementToRename;
  }

  public void performInplaceRename(@NotNull final Editor editor) {
    final Collection<PsiReference> refs = ReferencesSearch.search(myElementToRename).findAll();
    myElementToRename.getContainingFile();
    ResolveSnapshot snapshot = null;
    final PsiElement scope = myElementToRename instanceof PsiParameter ?
        ((PsiParameter) myElementToRename).getDeclarationScope() :
        PsiTreeUtil.getParentOfType(myElementToRename, PsiCodeBlock.class);
    if (scope != null) {
      snapshot = ResolveSnapshot.createSnapshot(scope);
    }

    final TemplateBuilder builder = new TemplateBuilder(scope);

    final Project project = myElementToRename.getProject();
    final PsiIdentifier nameIdentifier = myElementToRename.getNameIdentifier();
    if (nameIdentifier != null) {
      MyExpression expression = new MyExpression(myElementToRename.getName());
      builder.replaceElement(nameIdentifier, PRIMARY_VARIABLE_NAME, expression, true);
    }
    for (PsiReference ref : refs) {
      PsiElement element = ref.getElement();
      builder.replaceElement(element, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }


    final ResolveSnapshot snapshot1 = snapshot;
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Template template = builder.buildInlineTemplate();
            assert scope != null;
            TextRange range = scope.getTextRange();
            assert range != null;
            editor.getCaretModel().moveToOffset(range.getStartOffset());
            TemplateManager.getInstance(project).startTemplate(editor, template,
                new TemplateStateListener() {
                  public void templateFinished(Template template) {
                    if (snapshot1 != null) {
                      final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
                      if (templateState != null) {
                        String newName = templateState.getVariableValue(PRIMARY_VARIABLE_NAME).toString();
                        if (PsiManager.getInstance(project).getNameHelper().isIdentifier(newName)) {
                          snapshot1.apply(newName);
                        }
                      }
                    }
                  }
                });
          }

        });
      }
    }, RefactoringBundle.message("rename.title"), null);


  }

  public static boolean mayRenameInplace(PsiVariable elementToRename) {
    if (!(elementToRename instanceof PsiLocalVariable) && !(elementToRename instanceof PsiParameter)) return false;
    SearchScope useScope = elementToRename.getUseScope();
    if (!(useScope instanceof LocalSearchScope)) return false;
    PsiElement[] scopeElements = ((LocalSearchScope) useScope).getScope();
    if (scopeElements.length > 1) return false; //assume there are no elements with use scopes with holes in'em
    PsiFile containingFile = elementToRename.getContainingFile();
    if (!PsiTreeUtil.isAncestor(containingFile, scopeElements[0], false)) return false;

    String stringToSearch = RefactoringUtil.getStringToSearch(elementToRename, true);
    List<UsageInfo> usages = new ArrayList<UsageInfo>();
    RefactoringUtil.addUsagesInStringsAndComments(elementToRename, stringToSearch, usages, new RefactoringUtil.UsageInfoFactory() {
      public UsageInfo createUsageInfo(PsiElement usage, int startOffset, int endOffset) {
        return new UsageInfo(usage); //will not need usage
      }
    });

    return usages.size() == 0;
  }

  private static class MyExpression implements Expression {
    private final String myName;

    public MyExpression(String name) {
      myName = name;
    }

    public LookupItem[] calculateLookupItems(ExpressionContext context) {
      return new LookupItem[0];
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return new TextResult(myName);
    }

    public Result calculateResult(ExpressionContext context) {
      return new TextResult(myName);
    }
  }
}
