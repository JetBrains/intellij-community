package com.jetbrains.rest.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.rest.RestTokenTypes;
import com.jetbrains.rest.RestUtil;
import com.jetbrains.rest.psi.RestReferenceTarget;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * User : catherine
 */
public class DirectiveCompletionContributor extends CompletionContributor {
  public static final PsiElementPattern.Capture<PsiElement> DIRECTIVE_PATTERN = psiElement().afterSibling(or(psiElement().
    withElementType(RestTokenTypes.WHITESPACE).afterSibling(psiElement(RestReferenceTarget.class)),
       psiElement().withElementType(RestTokenTypes.EXPLISIT_MARKUP_START)));

  public DirectiveCompletionContributor() {
    extend(CompletionType.BASIC, DIRECTIVE_PATTERN,
       new CompletionProvider<CompletionParameters>() {
         @Override
         protected void addCompletions(@NotNull CompletionParameters parameters,
                                       ProcessingContext context,
                                       @NotNull CompletionResultSet result) {
           for (String tag : RestUtil.getDirectives()) {
             result.addElement(LookupElementBuilder.create(tag));
           }
           Sdk sdk = ProjectRootManager.getInstance(parameters.getPosition().getProject()).getProjectSdk();
           if (sdk != null) {
             String sphinx = RestUtil.findRunner(sdk.getHomePath(), "sphinx-quickstart"+ (SystemInfo.isWindows ? ".exe" : ""));
             if (sphinx != null && !sphinx.isEmpty()) {
               for (String tag : RestUtil.SPHINX_DIRECTIVES) {
                 result.addElement(LookupElementBuilder.create(tag));
               }
             }
           }
         }
       }
       );
  }
}
