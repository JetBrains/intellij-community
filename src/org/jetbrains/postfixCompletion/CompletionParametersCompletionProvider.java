package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

class CompletionParametersCompletionProvider extends CompletionProvider<CompletionParameters> {
    public void addCompletions(@NotNull CompletionParameters parameters,
                               ProcessingContext context,
                               @NotNull CompletionResultSet resultSet) {


        final PsiElement element = parameters.getPosition();
        if (element instanceof PsiIdentifier) {
            final PsiElement parent = element.getParent();
            if (parent instanceof PsiReferenceExpression) {
                final PsiExpression qualifierExpression = ((PsiReferenceExpression) parent).getQualifierExpression();
                if (qualifierExpression != null) {
                    PsiType type = qualifierExpression.getType();


                    resultSet.addElement(new Foo());
                }
            }

        }



        //resultSet.addElement(LookupElementBuilder.create("Hello"));
    }

    static class Foo extends com.intellij.codeInsight.lookup.LookupElement {



        @NotNull
        @Override
        public String getLookupString() {
            return "if";
        }

        @Override
        public void handleInsert(InsertionContext context) {
            //context.commitDocument();
            //final char completionChar = context.getCompletionChar();
            final int startOffset = context.getStartOffset();
            final int endOffset = context.getTailOffset();

            context.getDocument().replaceString(startOffset, endOffset, "__");
            context.commitDocument();

            final PsiElement psiElement = context.getFile().findElementAt(startOffset);

            if (psiElement instanceof PsiIdentifier) {
                final PsiElement parent = psiElement.getParent();
                if (parent instanceof PsiReferenceExpression) {
                    final PsiExpression qualifierExpression = ((PsiReferenceExpression) parent).getQualifierExpression();
                    if (qualifierExpression != null) {


                        PsiElement parent1 = parent.getParent();
                        if (parent1 instanceof PsiExpressionStatement) {
                            JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(qualifierExpression.getProject());
                            PsiElementFactory psiElementFactory = psiFacade.getElementFactory();

                            PsiIfStatement psiStatement = (PsiIfStatement)
                                    psiElementFactory.createStatementFromText("if (expr) { }", qualifierExpression);

                            PsiExpression condition = psiStatement.getCondition();
                            assert condition != null;
                            condition.replace(qualifierExpression);

                            PsiIfStatement newSt = (PsiIfStatement) parent1.replace(psiStatement);


                        }

                        //qualifierExpression.getProject()

                    }
                }

            }
            //super.handleInsert(context);
        }
    }
}
