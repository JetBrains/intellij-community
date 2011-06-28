package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author yole
 */
public class PySuperMethodCompletionContributor extends CompletionContributor {
  public PySuperMethodCompletionContributor() {
    extend(CompletionType.BASIC,
           psiElement().afterLeafSkipping(psiElement().whitespace(), psiElement().withElementType(PyTokenTypes.DEF_KEYWORD)),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               PsiElement position = parameters.getOriginalPosition();
               PyClass containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
               if (containingClass == null && position instanceof PsiWhiteSpace) {
                 position = PsiTreeUtil.prevLeaf(position);
                 containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
               }
               if (containingClass == null) {
                 return;
               }
               List<String> seenNames = new ArrayList<String>();
               for (PyFunction function : containingClass.getMethods()) {
                 seenNames.add(function.getName());
               }
               for (PyClass ancestor : containingClass.iterateAncestorClasses()) {
                 for (PyFunction superMethod : ancestor.getMethods()) {
                   if (!seenNames.contains(superMethod.getName())) {
                     String text = superMethod.getName() + superMethod.getParameterList().getText();
                     LookupElementBuilder element = LookupElementBuilder.create(text);
                     result.addElement(TailTypeDecorator.withTail(element, TailType.CASE_COLON));
                     seenNames.add(superMethod.getName());
                   }
                 }
               }
             }
           });
  }
}
