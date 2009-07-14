package com.intellij.spellchecker.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.spellchecker.CheckArea;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.TextSplitter;
import com.intellij.spellchecker.quickfixes.AcceptWordAsCorrect;
import com.intellij.spellchecker.quickfixes.ChangeTo;
import com.intellij.spellchecker.quickfixes.RenameTo;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collection;


public class SpellCheckingInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return SpellCheckerBundle.message("spelling");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return SpellCheckerBundle.message("spellchecking.inspection.name");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "SpellCheckingInspection";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return SpellCheckerManager.getHighlightDisplayLevel();
  }

  private static boolean initComplete;
  private static Map<Language, SpellcheckingStrategy> factories = new HashMap<Language, SpellcheckingStrategy>();

  private static void init() {
    if (initComplete) return;
    final SpellcheckingStrategy[] spellcheckingStrategies = Extensions.getExtensions(SpellcheckingStrategy.EP_NAME);
    if (spellcheckingStrategies != null) {
      for (SpellcheckingStrategy spellcheckingStrategy : spellcheckingStrategies) {
        final Language language = spellcheckingStrategy.getLanguage();
        if (language != Language.ANY) {
          factories.put(language, spellcheckingStrategy);
        }
      }
    }
    initComplete = true;
  }

  private static SpellcheckingStrategy getFactoryByLanguage(@NotNull Language lang) {
    return factories.containsKey(lang) ? factories.get(lang) : factories.get(PlainTextLanguage.INSTANCE);
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {


    return new PsiElementVisitor() {

      @Override
      public void visitElement(PsiElement element) {
        if (!initComplete) {
          init();
        }
        final SpellcheckingStrategy factoryByLanguage = getFactoryByLanguage(element.getLanguage());
        final Tokenizer tokenizer = factoryByLanguage.getTokenizer(element);

        @SuppressWarnings({"unchecked"}) Token[] tokens = tokenizer.tokenize(element);
        if (tokens == null) {
          return;
        }
        for (Token token : tokens) {
          inspect(token, holder, isOnTheFly, getNamesValidators());
        }
      }
    };
  }

  private static void inspect(Token token, ProblemsHolder holder, boolean isOnTheFly, NamesValidator... validators) {
    List<CheckArea> areaList = TextSplitter.splitText(token.getText());
    if (areaList == null) {
      return;
    }
    for (CheckArea area : areaList) {
      boolean ignored = area.isIgnored();
      boolean keyword = isKeyword(validators, token.getElement(), area.getWord());
      if (!ignored && !keyword) {
        inspect(area, token, holder, isOnTheFly);
      }
    }
  }


  private static void inspect(@NotNull CheckArea area, @NotNull Token token, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    SpellCheckerManager manager = SpellCheckerManager.getInstance(token.getElement().getProject());

    final TextRange textRange = area.getTextRange();
    final String word = area.getWord();

    if (textRange == null || word == null) {
      return;
    }

    if (manager.hasProblem(word)) {
      List<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>();
      if (isOnTheFly) {
        if (!token.isUseRename()) {
          fixes.add(new ChangeTo(textRange, word, token.getElement().getProject()));
        }
        else {
          fixes.add(new RenameTo());
        }
      }

      fixes.add(new AcceptWordAsCorrect(word));

      holder.registerProblem(createProblemDescriptor(token, holder, textRange, word, fixes));
    }

  }

  private static ProblemDescriptor createProblemDescriptor(Token token,
                                                           ProblemsHolder holder,
                                                           TextRange textRange,
                                                           String word,
                                                           Collection<LocalQuickFix> fixes) {
    final String defaultDescription = SpellCheckerBundle.message("word.0.1.is.misspelled", word, token.getElement().getLanguage());
    final String tokenDescription = token.getDescription();
    final String description = tokenDescription == null ? defaultDescription : tokenDescription;
    final TextRange highlightRange = TextRange.from(token.getOffset() + textRange.getStartOffset(), textRange.getLength());
    final LocalQuickFix[] quickFixes = fixes.size() > 0 ? fixes.toArray(new LocalQuickFix[fixes.size()]) : null;
    return holder.getManager()
      .createProblemDescriptor(token.getElement(), highlightRange, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickFixes);
  }

  @Nullable
  public static NamesValidator[] getNamesValidators() {
    final Object[] extensions = Extensions.getExtensions("com.intellij.lang.namesValidator");
    NamesValidator[] validators = null;
    if (extensions != null) {
      List<NamesValidator> validatorList = new ArrayList<NamesValidator>();
      for (Object extension : extensions) {
        if (extension instanceof LanguageExtensionPoint && ((LanguageExtensionPoint)extension).getInstance() instanceof NamesValidator) {
          validatorList.add((NamesValidator)((LanguageExtensionPoint)extension).getInstance());
        }
      }
      if (validatorList.size() > 0) {
        validators = new NamesValidator[validatorList.size()];
        validatorList.toArray(validators);
      }
    }
    return validators;
  }

  private static boolean isKeyword(NamesValidator[] validators, PsiElement element, String word) {
    if (validators == null) {
      return false;
    }
    for (NamesValidator validator : validators) {
      if (validator.isKeyword(word, element.getProject())) {
        return true;
      }
    }
    return false;
  }
}
