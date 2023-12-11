// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.lang.Language;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringUiService;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.PyDefUseUtil;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public final class PyInlineLocalHandler extends InlineActionHandler {
  private static final Logger LOG = Logger.getInstance(PyInlineLocalHandler.class.getName());

  private static final Pair<PyStatement, Boolean> EMPTY_DEF_RESULT = Pair.create(null, false);
  private static final String HELP_ID = "refactoring.inlineVariable";

  public static PyInlineLocalHandler getInstance() {
    return InlineActionHandler.EP_NAME.findExtensionOrFail(PyInlineLocalHandler.class);
  }

  @Override
  public boolean isEnabledForLanguage(Language l) {
    return l instanceof PythonLanguage;
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    return element instanceof PyTargetExpression;
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    if (editor == null) {
      return;
    }
    final PsiReference psiReference = TargetElementUtilBase.findReferenceWithoutExpectedCaret(editor);
    PyReferenceExpression refExpr = null;
    if (psiReference != null) {
      final PsiElement refElement = psiReference.getElement();
      if (refElement instanceof PyReferenceExpression) {
        refExpr = (PyReferenceExpression)refElement;
      }
    }
    invoke(project, editor, (PyTargetExpression)element, refExpr);
  }

  private static boolean stringContentCanBeInlinedIntoFString(@NotNull PyStringElement inlinedStringElement, 
                                                              @NotNull PyFormattedStringElement targetFString) {
    if (LanguageLevel.forElement(targetFString).isAtLeast(LanguageLevel.PYTHON312)) return true;
    String content = inlinedStringElement.getContent();
    if (targetFString.isTripleQuoted()) {
      return !content.contains("\\");
    }
    return !content.contains("'") && !content.contains("\"") && !content.contains("\\");
  }

  @NotNull
  private static List<PyStringElement> getStringElements(@NotNull PyExpression expression) {
    List<PyStringElement> result = new ArrayList<>();
    for (var stringLiteralExpr : PsiTreeUtil.findChildrenOfAnyType(expression, false, PyStringLiteralExpression.class)) {
      result.addAll(stringLiteralExpr.getStringElements());
    }
    return result;
  }

  @NotNull
  private static PyExpression replaceQuotesInExpression(@NotNull PyExpression expression, char desiredQuote) {
    var expressionCopy = (PyExpression)expression.copy();
    var valueStringElements = getStringElements(expressionCopy);
    for (var valueStringElement : valueStringElements) {
      char actualQuote = valueStringElement.getQuote().charAt(0);
      PyStringElement elementToReplace = valueStringElement;
      if (actualQuote != desiredQuote) {
        elementToReplace = PyQuotesUtil.createCopyWithConvertedQuotes(valueStringElement);
      }
      valueStringElement.replace(elementToReplace);
    }
    return expressionCopy;
  }

  private static boolean checkPossibleInlineElement(@NotNull PsiElement element, @NotNull PyExpression value,
                                                    @NotNull Project project, @NotNull Editor editor,
                                                    @NotNull Map<PsiElement, PsiElement> simpleReplacements,
                                                    @NotNull Map<PyFStringFragment, PyStringElement> fStringFragmentsReplacements) {
    PyFormattedStringElement targetFString = PsiTreeUtil.getParentOfType(element, PyFormattedStringElement.class, true, PyStatement.class);
    if (targetFString == null) {
      simpleReplacements.put(element, value);
      return true;
    }

    boolean fStringCanContainArbitraryStrings = LanguageLevel.forElement(element).isAtLeast(LanguageLevel.PYTHON312);
    if (!fStringCanContainArbitraryStrings && !targetFString.isTripleQuoted() && value.textContains('\n')) {
      CommonRefactoringUtil.showErrorHint(project, editor, PyPsiBundle.message("refactoring.inline.can.not.multiline.string.to.f.string"),
                                          getRefactoringName(), HELP_ID);
      return false;
    }

    boolean intoNestedFString = PsiTreeUtil.getParentOfType(targetFString, PyFormattedStringElement.class, true, PyStatement.class) != null;
    List<PyStringElement> valueStringElements = getStringElements(value);
    boolean entireValueIsSingleNonInterpolatedString = value instanceof PyStringLiteralExpression && valueStringElements.size() == 1;
    if (entireValueIsSingleNonInterpolatedString && element.getParent() instanceof PyFStringFragment fStringFragment
        && fStringFragment.getTypeConversion() == null && fStringFragment.getFormatPart() == null
        && !(value.textContains('\n') && !targetFString.isTripleQuoted())) {
      if (intoNestedFString && !stringContentCanBeInlinedIntoFString(valueStringElements.get(0), targetFString)) {
        CommonRefactoringUtil.showErrorHint(project, editor,
                                            PyPsiBundle.message("refactoring.inline.can.not.string.with.backslashes.or.quotes.to.f.string"),
                                            getRefactoringName(), HELP_ID);
        return false;
      }

      var valueReplacedQuotes = replaceQuotesInExpression(value, targetFString.getQuote().charAt(0));
      var stringElements = getStringElements(valueReplacedQuotes);
      if (stringElements.size() == 1) {
        fStringFragmentsReplacements.put(fStringFragment, stringElements.get(0));
        return true;
      }
    }

    if (!fStringCanContainArbitraryStrings && intoNestedFString) {
      CommonRefactoringUtil.showErrorHint(project, editor, PyPsiBundle.message("refactoring.inline.can.not.string.to.nested.f.string"),
                                          getRefactoringName(), HELP_ID);
      return false;
    }

    for (PyStringElement valueStringElement : valueStringElements) {
      if (!stringContentCanBeInlinedIntoFString(valueStringElement, targetFString)) {
        String message = PyPsiBundle.message("refactoring.inline.can.not.string.with.backslashes.or.quotes.to.f.string");
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
        return false;
      }
    }

    char newQuote = PyStringLiteralUtil.flipQuote(targetFString.getQuote().charAt(0));
    var quoteSafeValue = fStringCanContainArbitraryStrings ? value.copy() : replaceQuotesInExpression(value, newQuote);
    simpleReplacements.put(element, quoteSafeValue);
    return true;
  }

  private static void makeFStringFragmentsReplacements(@NotNull Map<PyFStringFragment, PyStringElement> fStringFragmentsReplacements) {
    var fString2Replacements = new MultiMap<PyFormattedStringElement, Pair<PyFStringFragment, String>>();

    for (var entry: fStringFragmentsReplacements.entrySet()) {
      PyFStringFragment fStringFragment = entry.getKey();
      PyFormattedStringElement fString = (PyFormattedStringElement)fStringFragment.getParent();
      String valueProperQuotes = entry.getValue().getContent();
      fString2Replacements.putValue(fString, Pair.create(fStringFragment, valueProperQuotes));
    }

    var fStrings = new ArrayList<>(fString2Replacements.keySet());
    fStrings.sort(Comparator.comparingInt(it -> -it.getTextOffset()));

    for (var fString: fStrings) {
      var replacements = fString2Replacements.get(fString);
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(fString.getProject());

      var replacementsSegments = ContainerUtil.sorted(ContainerUtil.map(replacements, it -> Pair.create(it.first.getTextRangeInParent(), it.second)),
      Comparator.comparingInt(it -> -it.first.getStartOffset()));

      StringBuilder elementStringBuilder = new StringBuilder(fString.getText());
      for (var segment : replacementsSegments) {
        elementStringBuilder.replace(segment.first.getStartOffset(), segment.first.getEndOffset(), segment.second);
      }

      String resultString = elementStringBuilder.toString();

      PyStringElement elementToReplace = (PyStringElement)elementGenerator.createStringLiteralAlreadyEscaped(resultString).getFirstChild();
      fString.replace(elementToReplace);
    }
  }

  private static void invoke(@NotNull final Project project,
                             @NotNull final Editor editor,
                             @NotNull final PyTargetExpression local,
                             @Nullable PyReferenceExpression refExpr) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, local)) return;

    final HighlightManager highlightManager = HighlightManager.getInstance(project);

    final String localName = local.getName();
    final ScopeOwner containerBlock = getContext(local);
    LOG.assertTrue(containerBlock != null);


    final Pair<PyStatement, Boolean> defPair = getAssignmentToInline(containerBlock, refExpr, local, project);
    final PyStatement def = defPair.first;
    if (def == null || getValue(def) == null) {
      final String key = defPair.second ? "variable.has.no.dominating.definition" : "variable.has.no.initializer";
      final String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(key, localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
      return;
    }

    if (def instanceof PyAssignmentStatement && ((PyAssignmentStatement)def).getTargets().length > 1) {
      highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{def}, EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, true, null);
      final String message =
        RefactoringBundle.getCannotRefactorMessage(PyPsiBundle.message("refactoring.inline.local.multiassignment", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
      return;
    }

    final PsiElement[] refsToInline = PyDefUseUtil.getPostRefs(containerBlock, local, getObject(def));
    if (refsToInline.length == 0) {
      final String message = RefactoringBundle.message("variable.is.never.used", localName);
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
      return;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      highlightManager.addOccurrenceHighlights(editor, refsToInline, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
      final int occurrencesCount = refsToInline.length;
      final String occurrencesString = RefactoringBundle.message("occurrences.string", occurrencesCount);
      final String question = RefactoringBundle.message("inline.local.variable.prompt", localName) + " " + occurrencesString;
      boolean result = RefactoringUiService.getInstance().showRefactoringMessageDialog(getRefactoringName(), question, HELP_ID, "OptionPane.questionIcon", true, project);
      if (!result) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }

    final PsiFile workingFile = local.getContainingFile();
    for (PsiElement ref : refsToInline) {
      final PsiFile otherFile = ref.getContainingFile();
      if (!otherFile.equals(workingFile)) {
        final String message = RefactoringBundle.message("variable.is.referenced.in.multiple.files", localName);
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
        return;
      }
    }

    for (final PsiElement ref : refsToInline) {
      final List<PsiElement> elems = new ArrayList<>();
      final List<Instruction> latestDefs = PyDefUseUtil.getLatestDefs(containerBlock, local.getName(), ref, false, false);
      for (Instruction i : latestDefs) {
        elems.add(i.getElement());
      }
      final PsiElement[] defs = elems.toArray(PsiElement.EMPTY_ARRAY);
      boolean isSameDefinition = true;
      for (PsiElement otherDef : defs) {
        isSameDefinition &= isSameDefinition(def, otherDef);
      }
      if (!isSameDefinition) {
        highlightManager.addOccurrenceHighlights(editor, defs, EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, true, null);
        highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{ref}, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
        final String message = RefactoringBundle.getCannotRefactorMessage(
          RefactoringBundle.message("variable.is.accessed.for.writing.and.used.with.inlined", localName));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }


    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(local);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
          .refactoringStarted(getRefactoringId(), afterData);

        final List<PsiElement> exprs = new ArrayList<>();
        final PyExpression value = prepareValue(def, localName, project);
        final PsiElement lastChild = def.getLastChild();
        if (lastChild != null && lastChild.getNode().getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
          final PsiElement parent = def.getParent();
          if (parent != null) parent.addBefore(lastChild, def);
        }

        // Determine possibility to inline all refs
        // Return if at least one ref is impossible to inline
        var simpleReplacements = new HashMap<PsiElement, PsiElement>();
        var fStringFragmentsReplacements = new HashMap<PyFStringFragment, PyStringElement>();
        for (var refToInline: refsToInline) {
          if (!checkPossibleInlineElement(refToInline, value, project, editor, simpleReplacements, fStringFragmentsReplacements)) {
            return;
          }
        }

        final LanguageLevel level = LanguageLevel.forElement(value);
        for (var entry : simpleReplacements.entrySet()) {
          PsiElement replElement = entry.getKey();
          PsiElement replValue = entry.getValue();
          if (PyReplaceExpressionUtil.isNeedParenthesis((PyExpression)replElement, (PyExpression)replValue)) {
            replValue = PyElementGenerator.getInstance(project).createExpressionFromText(level, "(" + replValue.getText() + ")");
          }
          exprs.add(replElement.replace(replValue));
        }
        makeFStringFragmentsReplacements(fStringFragmentsReplacements);

        final PsiElement next = def.getNextSibling();
        if (next instanceof PsiWhiteSpace) {
          PyPsiUtils.removeElements(next);
        }
        PyPsiUtils.removeElements(def);

        final List<TextRange> ranges = ContainerUtil.mapNotNull(exprs, element -> {
          final PyStatement parentalStatement = PsiTreeUtil.getParentOfType(element, PyStatement.class, false);
          return parentalStatement != null ? parentalStatement.getTextRange() : null;
        });
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        CodeStyleManager.getInstance(project).reformatText(workingFile, ranges);

        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          highlightManager.addOccurrenceHighlights(editor, exprs.toArray(PsiElement.EMPTY_ARRAY), EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
          WindowManager.getInstance().getStatusBar(project)
            .setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        }
      }
      finally {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(local);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
          .refactoringDone(getRefactoringId(), afterData);
      }
    }), RefactoringBundle.message("inline.command", localName), null);
  }

  private static boolean isSameDefinition(PyStatement def, PsiElement otherDef) {
    if (otherDef instanceof PyTargetExpression) otherDef = otherDef.getParent();
    return otherDef == def;
  }

  private static ScopeOwner getContext(PyTargetExpression local) {
    ScopeOwner context = PsiTreeUtil.getParentOfType(local, PyFunction.class);
    if (context == null) {
      context = PsiTreeUtil.getParentOfType(local, PyClass.class);
    }
    if (context == null) {
      context = (PyFile)local.getContainingFile();
    }
    return context;
  }

  private static Pair<PyStatement, Boolean> getAssignmentToInline(ScopeOwner containerBlock, PyReferenceExpression expr,
                                                                  PyTargetExpression local, Project project) {
    if (expr != null) {
      try {
        final List<Instruction> candidates = PyDefUseUtil.getLatestDefs(containerBlock, local.getName(), expr, true, true);
        if (candidates.size() == 1) {
          final PyStatement expression = getAssignmentByLeftPart((PyElement)candidates.get(0).getElement());
          return Pair.create(expression, false);
        }
        return Pair.create(null, candidates.size() > 0);
      }
      catch (PyDefUseUtil.InstructionNotFoundException ignored) {
      }
    }
    final Query<PsiReference> query = ReferencesSearch.search(local, GlobalSearchScope.allScope(project), false);
    final PsiReference first = query.findFirst();

    final PyElement lValue = first != null ? (PyElement)first.resolve() : null;
    return lValue != null ? Pair.create(getAssignmentByLeftPart(lValue), false) : EMPTY_DEF_RESULT;
  }

  @Nullable
  private static PyStatement getAssignmentByLeftPart(PyElement candidate) {
    final PsiElement parent = candidate.getParent();
    return parent instanceof PyAssignmentStatement || parent instanceof PyAugAssignmentStatement ? (PyStatement)parent : null;
  }

  @Nullable
  private static PyExpression getValue(@Nullable PyStatement def) {
    if (def == null) return null;
    if (def instanceof PyAssignmentStatement) {
      return ((PyAssignmentStatement)def).getAssignedValue();
    }
    return ((PyAugAssignmentStatement)def).getValue();
  }

  @Nullable
  private static PyExpression getObject(@Nullable PyStatement def) {
    if (def == null) return null;
    if (def instanceof PyAssignmentStatement) {
      return ((PyAssignmentStatement)def).getTargets()[0];
    }
    return ((PyAugAssignmentStatement)def).getTarget();
  }

  @NotNull
  private static PyExpression prepareValue(@NotNull PyStatement def, @NotNull String localName, @NotNull Project project) {
    final PyExpression value = getValue(def);
    assert value != null;
    if (def instanceof PyAugAssignmentStatement expression) {
      final PsiElement operation = expression.getOperation();
      assert operation != null;
      final String op = operation.getText().replace('=', ' ');
      final LanguageLevel level = LanguageLevel.forElement(value);
      return PyElementGenerator.getInstance(project).createExpressionFromText(level, localName + " " + op + value.getText() + ")");
    }
    return value;
  }

  public static String getRefactoringId() {
    return "refactoring.python.inline.local";
  }

  private static @Nls(capitalization = Nls.Capitalization.Title) String getRefactoringName() {
    return RefactoringBundle.message("inline.variable.title");
  }
}