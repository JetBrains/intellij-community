package com.intellij.spellchecker.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.checker.Checker;
import com.intellij.spellchecker.checker.CheckerFactory;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


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


  private static Set<String> elementTypes = new HashSet<String>();
  private static Set<Language> languages = new HashSet<Language>();
  private static Map<String, Checker> checkers = new HashMap<String, Checker>();

  static {
    elementTypes.add("CLASS");
    elementTypes.add("FIELD");
    elementTypes.add("METHOD");
    elementTypes.add("LOCAL_VARIABLE");
    elementTypes.add("STRING_LITERAL");
    elementTypes.add("C_STYLE_COMMENT");
    elementTypes.add("END_OF_LINE_COMMENT");
    elementTypes.add("DOC_COMMENT");
    elementTypes.add("XML_ATTRIBUTE_VALUE");
    elementTypes.add("XML_TEXT");
    elementTypes.add("XML_COMMENT");
    elementTypes.add("PLAIN_TEXT");

  }

  static {
    languages.add(StdLanguages.JAVA);
    languages.add(StdLanguages.XML);
    languages.add(StdLanguages.TEXT);
  }


  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {


    return new PsiElementVisitor() {

      @Override
      public void visitElement(PsiElement element) {


        if (element.getLanguage() == StdLanguages.JSP || element.getLanguage() == StdLanguages.JSPX) {
          return;
        }

        IElementType type = (element.getNode() != null) ? element.getNode().getElementType() : null;

        if (type == null) {
          return;
        }

        boolean check = languages.contains(type.getLanguage()) && (elementTypes.contains(type.toString()));
        if (!check) {
          return;
        }

        Checker checker = CheckerFactory.getInstance().createChecker(element, holder, isOnTheFly);
        if (checker == null) {
          return;
        }

        final List<ProblemDescriptor> problems = checker.checkElement(element);
        if (problems.isEmpty()) {
          return;
        }
        checker.addDescriptors(problems);
      }
    };
  }
}
