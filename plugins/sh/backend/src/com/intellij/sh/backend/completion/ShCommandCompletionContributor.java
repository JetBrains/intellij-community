// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.backend.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.lexer.ShTokenTypes;
import com.intellij.sh.psi.ShCommandsList;
import com.intellij.ui.IconManager;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.sh.backend.completion.ShCompletionUtil.*;

public class ShCommandCompletionContributor extends CompletionContributor implements DumbAware {
  private static final int BUILTIN_PRIORITY = -10;

  public ShCommandCompletionContributor() {
    extend(CompletionType.BASIC, elementPattern(), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        if (endsWithDot(parameters)) return;

        Collection<String> kws = new SmartList<>();
        PsiElement original = parameters.getOriginalPosition();
        if (original == null || !original.getText().contains("/")) {
          result.addAllElements(ContainerUtil.map(BUILTIN,
                                                  s -> PrioritizedLookupElement.withPriority(LookupElementBuilder
                                                                                               .create(s)
                                                                                               .withIcon(
                                                                                                 IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Function))
                                                                                               .withInsertHandler(
                                                                                                 AddSpaceInsertHandler.INSTANCE),
                                                                                             BUILTIN_PRIORITY)));

          kws = suggestKeywords(parameters.getPosition());
          for (String keywords : kws) {
            result.addElement(LookupElementBuilder.create(keywords).bold().withInsertHandler(AddSpaceInsertHandler.INSTANCE));
          }
        }

        kws.addAll(BUILTIN);
        Arrays.stream(ShTokenTypes.HUMAN_READABLE_KEYWORDS.getTypes()).map(IElementType::toString).forEach(kws::add);

        String prefix = CompletionUtil.findJavaIdentifierPrefix(parameters);
        if (prefix.isEmpty() && parameters.isAutoPopup()) {
          return;
        }

        CompletionResultSet resultSetWithPrefix = result.withPrefixMatcher(prefix);
        WordCompletionContributor.addWordCompletionVariants(resultSetWithPrefix, parameters, new HashSet<>(kws));
      }
    });
  }

  private static Collection<String> suggestKeywords(PsiElement position) {
    TextRange posRange = position.getTextRange();
    PsiFile posFile = position.getContainingFile();
    ShCommandsList parent = PsiTreeUtil.getTopmostParentOfType(position, ShCommandsList.class);
    TextRange range = new TextRange(parent == null ? 0 : parent.getTextRange().getStartOffset(), posRange.getStartOffset());
    String text = range.isEmpty() ? CompletionInitializationContext.DUMMY_IDENTIFIER : range.substring(posFile.getText());

    PsiFile file = PsiFileFactory.getInstance(posFile.getProject()).createFileFromText("a.sh", ShLanguage.INSTANCE, text, true, false);
    int completionOffset = posRange.getStartOffset() - range.getStartOffset();
    GeneratedParserUtilBase.CompletionState state = new GeneratedParserUtilBase.CompletionState(completionOffset) {
      @Override
      public String convertItem(Object o) {
        if (o instanceof IElementType[] && ((IElementType[]) o).length > 0) return kw2str(((IElementType[]) o)[0]);
        return o instanceof IElementType ? kw2str(((IElementType) o)) : null;
      }

      private static @Nullable String kw2str(IElementType o) {
        return ShTokenTypes.HUMAN_READABLE_KEYWORDS_WITHOUT_TEMPLATES.contains(o) ? o.toString() : null;
      }
    };
    file.putUserData(GeneratedParserUtilBase.COMPLETION_STATE_KEY, state);
    TreeUtil.ensureParsed(file.getNode());
    return state.items;
  }

  private static PsiElementPattern.Capture<PsiElement> elementPattern() {
    return psiElement().andNot(psiElement().andOr(insideForClause(), insideIfDeclaration(), insideWhileDeclaration(),
        insideUntilDeclaration(), insideFunctionDefinition(), insideSelectDeclaration(), insideCaseDeclaration(),
        insideCondition(), insideComment()));
  }

  private static final @NonNls List<String> BUILTIN =
    Arrays.asList("alias", "bg", "bind", "break", "builtin", "caller", "cd", "command", "compgen", "complete", "continue", "declare",
                  "dirs", "disown", "echo", "enable", "eval", "exec", "exit", "export", "false", "fc", "fg", "getopts", "hash", "help", "history",
                  "jobs", "kill", "let", "local", "logout", "popd", "printf", "pushd", "pwd", "read", "readonly", "return", "set", "shift", "shopt",
                  "source", "suspend", "test", "times", "trap", "true", "type", "typeset", "ulimit", "umask", "unalias", "unset", "wait");
}
