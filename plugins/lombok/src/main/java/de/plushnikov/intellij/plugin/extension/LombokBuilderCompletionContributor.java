package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHelper;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LombokBuilderCompletionContributor extends CompletionContributor {

  public LombokBuilderCompletionContributor() {

    extend(CompletionType.BASIC,
           PsiJavaPatterns.psiElement().afterLeaf("."),
           new CompletionProvider<>() {

             private static void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
               if (item.getPsiElement() instanceof PsiModifierListOwner psiModifierListOwner) {
                 final PsiAnnotation psiAnnotation = BuilderHelper.getBuilderAnnotation(psiModifierListOwner);
                 if (null == psiAnnotation) {
                   return;
                 }

                 final List<String> remainingBuilderMethods = BuilderHelper.getAllBuilderMethodNames(psiModifierListOwner, psiAnnotation,
                                                                                               Predicates.alwaysTrue());

                 final int contextStartOffset = context.getStartOffset();
                 final PsiElement elementAtCaret = context.getFile().findElementAt(contextStartOffset);
                 final PsiMethodCallExpression methodCallExpression =
                   PsiTreeUtil.getPrevSiblingOfType(elementAtCaret, PsiMethodCallExpression.class);
                 final List<String> existingMethodCalls = BuilderHelper.getAllMethodsInChainFromMiddle(methodCallExpression);
                 // remove existing methods if any
                 remainingBuilderMethods.removeAll(existingMethodCalls);

                 if (remainingBuilderMethods.isEmpty()) {
                   // If there are no remaining methods, just add the build() method
                   final String buildMethodName = BuilderHandler.getBuildMethodName(psiAnnotation);
                   context.getDocument().replaceString(contextStartOffset, context.getTailOffset(), buildMethodName + "()");
                   return;
                 }

                 // Create a template with tab stops for each parameter
                 // This allows the user to jump between parameter positions using a tab key
                 TemplateManager templateManager = TemplateManager.getInstance(context.getProject());
                 Template template = templateManager.createTemplate("", "");
                 template.setToReformat(false);

                 // Add each builder method with a variable for its parameter
                 // Using addVariable with isAlwaysStopAt=true ensures the cursor will stop at each parameter
                 // Also explicitly adding a variable segment to ensure the template system can find it
                 // This fixes the issue where the editor wasn't jumping to the first parameter position
                 int varIndex = 0;
                 for (String methodName : remainingBuilderMethods) {
                   template.addTextSegment(methodName + "(");
                   template.addVariable("param" + varIndex, "", "", true);
                   template.addVariableSegment("param" + varIndex);
                   varIndex++;
                   template.addTextSegment(").");
                 }

                 // Add the build method at the end
                 template.addTextSegment(BuilderHandler.getBuildMethodName(psiAnnotation) + "()");

                 // Replace the text and start the template
                 // This will insert the template and activate the first tab stop
                 context.getDocument().replaceString(contextStartOffset, context.getTailOffset(), "");
                 templateManager.startTemplate(context.getEditor(), template);
               }
             }

             @Override
             public void addCompletions(@NotNull CompletionParameters parameters,
                                        @NotNull ProcessingContext context,
                                        @NotNull CompletionResultSet resultSet) {
               final PsiMethodCallExpression methodCallExpression =
                 PsiTreeUtil.getPrevSiblingOfType(parameters.getPosition(), PsiMethodCallExpression.class);

               if (null != methodCallExpression) {
                 final PsiMethod psiMethod = methodCallExpression.resolveMethod();
                 if (psiMethod instanceof LombokLightMethodBuilder) {
                   final @Nullable Pair<PsiAnnotation, PsiNamedElement> elementPair = BuilderHelper.findBuilderAnnotation(psiMethod);
                   if (null != elementPair) {

                     final List<String> allBuilderMethods = BuilderHelper.getAllBuilderMethodNames(
                       (PsiModifierListOwner)elementPair.getSecond(), elementPair.getFirst(),
                       Predicates.alwaysTrue());
                     final String chainedMethods = BuilderHelper.renderChainedMethods(allBuilderMethods, elementPair.getFirst());

                     LookupElementBuilder lookupElementBuilder = LookupElementBuilder
                       .createWithIcon(elementPair.getSecond())
                       .withPresentableText(LombokBundle.message("complete.all.builder.methods.lombok.completion"))
                       .withBoldness(true)
                       .withTypeText(StringUtil.trimMiddle(chainedMethods, 50))
                       .withInsertHandler((context1, item) -> handleInsert(context1, item));
                     resultSet.addElement(lookupElementBuilder.withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE));
                   }
                 }
               }
             }
           }
    );
  }
}
