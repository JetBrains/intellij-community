package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IReparseableElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.lexer.PythonIndentingLexerForLazyElements;
import com.jetbrains.python.parsing.PyLazyParser;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIndentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class PyStatementListElementType extends IReparseableElementType implements ICompositeElementType {

  public PyStatementListElementType() {
    super("PyStatementList", PythonLanguage.INSTANCE);
  }

  private static final Logger LOG = Logger.getInstance(PyStatementListElementType.class);

  private static final Key<LanguageLevel> LANGUAGE_LEVEL_KEY = Key.create("LANGUAGE_LEVEL_FOR_REPARSEABLE_ELEMENT");
  private static final Key<Integer> BASE_INDENT_KEY = Key.create("FIRST_LINE_INDENT_FOR_REPARSEABLE_ELEMENT");

  // TODO: thresholds below may require further fine-tuning once more benchmark data is available

  /** Below this file size full reparse is cheap enough; 20K chars ≈ 500 lines of typical Python. */
  private static int minFileCharsThreshold() {
    return Math.max(0, Registry.intValue("python.statement.lists.incremental.reparse.min.file.chars", 20_000));
  }

  /** Hard cap on statement list size: protects EDT from per-keystroke lex over huge blocks. */
  private static int maxListCharsThreshold() {
    return Math.max(0, Registry.intValue("python.statement.lists.incremental.reparse.max.list.chars", 10_000));
  }

  /** Statement list size cap as % of file: keeps savings worth the per-commit overhead. */
  private static int maxRatioPercentThreshold() {
    return Math.max(0, Registry.intValue("python.statement.lists.incremental.reparse.max.ratio.percent", 10));
  }

  @Override
  public boolean isReparseable(@NotNull ASTNode currentNode,
                               @NotNull CharSequence newText,
                               @NotNull Language fileLanguage,
                               @NotNull Project project) {
    if (!Registry.is("python.statement.lists.incremental.reparse")) {
      return false;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Attempting to reparse lazy element of type " + this
                + "\nparent: " + currentNode.getTreeParent()
                + "\nold text: \n" + currentNode.getText()
                + "\n\nnew text: \n" + newText);
    }

    if (newText.isEmpty() || !fileLanguage.is(PythonLanguage.INSTANCE)) { // do not reparse Cython statement lists
      return false;
    }

    if (!isLargeEnoughForIncrementalReparse(currentNode, newText)) {
      return false;
    }

    // Error elements in this or any ancestor statement list mean the tree structure
    // may be wrong (e.g., after a cascading string literal break). In that case,
    // incremental reparse of an inner statement list can produce a tree inconsistent
    // with the outer, because the inner's text boundaries in the broken tree don't
    // match the correct code structure. Decline to let the platform reparse higher.
    if (anyAncestorHasErrors(currentNode)) {
      LOG.debug("Statement list or ancestor contains error elements, reparse is declined");
      return false;
    }

    boolean isAfterColonOnSameLine = isAfterColonOnSameLine((PyStatementListImpl)currentNode);
    int baseIndent = isAfterColonOnSameLine ? 0 : PyIndentUtil.getElementIndent(currentNode.getPsi()).length();

    currentNode.putUserData(BASE_INDENT_KEY, baseIndent);

    PythonIndentingLexerForLazyElements lexer = new PythonIndentingLexerForLazyElements(baseIndent);
    return checkIndentDedentBalanceWithLexer(newText, lexer, isAfterColonOnSameLine);
  }

  private static boolean isAfterColonOnSameLine(PsiElement currentNode) {
    PsiElement prevSibling = PyPsiUtils.getPrevNonWhitespaceSiblingOnSameLine(currentNode);
    return prevSibling != null && prevSibling.getNode().getElementType() == PyTokenTypes.COLON;
  }

  /**
   * Checks the balance between the number of INDENT and DEDENT tokens in the given text using the provided lexer.
   * Negative balance immediately indicates that given statement list is no longer reparseable.
   * Any positive balance or zero may be incorrect, but does not make a new statement list unparseable.
   *
   * @param text  the input text to check
   * @param lexer the lexer to use for tokenizing the text
   * @return true if the balance is positive or zero, otherwise - false
   */
  private static boolean checkIndentDedentBalanceWithLexer(@NotNull CharSequence text, @NotNull Lexer lexer, boolean isOnTheSameLine) {
    lexer.start(text);
    int balance = isOnTheSameLine ? 0 : -1;
    int tokenCount = 0;
    while (lexer.getTokenType() != null) {
      if (++tokenCount % 1000 == 0) {
        ProgressManager.checkCanceled();
      }
      if (lexer.getTokenType() == PyTokenTypes.INDENT) {
        balance++;
      }
      else if (lexer.getTokenType() == PyTokenTypes.DEDENT) {
        balance--;
      }
      if (balance < 0) {
        LOG.debug("Indent/Dedent balance is negative, incremental reparse declined");
        return false;
      }
      lexer.advance();
    }
    return true; // positive balance is safe
  }


  @Override
  public boolean isValidReparse(@NotNull ASTNode oldNode, @NotNull ASTNode newNode) {
    PsiFile file = oldNode.getPsi().getContainingFile();
    if (!(file instanceof PyFile)) return false;

    LanguageLevel languageLevel = LanguageLevel.forElement(file);
    // Reuse base indent cached by isReparseable on the same node
    Integer cachedIndent = oldNode.getUserData(BASE_INDENT_KEY);
    int baseIndent = cachedIndent != null ? cachedIndent : 0;

    newNode.putUserData(LANGUAGE_LEVEL_KEY, languageLevel);
    newNode.putUserData(BASE_INDENT_KEY, baseIndent);

    // Single pass: verify children exist and contain no error elements
    ASTNode child = newNode.getFirstChildNode();
    if (child == null) {
      return false;
    }
    while (child != null) {
      if (child.getElementType() == TokenType.ERROR_ELEMENT) {
        LOG.debug("Reparsed tree contains error elements, declining reparse");
        return false;
      }
      child = child.getTreeNext();
    }

    LOG.debug("Element of type " + this + " reparsed successfully");
    return true;
  }

  @Override
  protected ASTNode doParseContents(@NotNull ASTNode chameleon, @NotNull PsiElement psi) {
    PsiElement parentPsiElement = chameleon.getTreeParent().getPsi();
    assert parentPsiElement != null : "parent psi is null: " + chameleon;

    // BASE_INDENT_KEY is set by isReparseable during incremental reparse.
    // During full file parse, it's not set — fall back to 0 (file-level indent).
    Integer indent = chameleon.getUserData(BASE_INDENT_KEY);
    if (indent == null) {
      indent = 0;
    }

    LanguageLevel languageLevel = chameleon.getUserData(LANGUAGE_LEVEL_KEY);
    if (languageLevel == null) {
      languageLevel = LanguageLevel.getDefault();
    }

    Lexer lexer = new PythonIndentingLexerForLazyElements(indent);
    PsiBuilder builder = PsiBuilderFactory.getInstance()
      .createBuilder(parentPsiElement.getProject(), chameleon, lexer, PythonLanguage.INSTANCE, chameleon.getChars());

    return new PyLazyParser().parseLazyElement(this, builder, languageLevel);
  }

  @Override
  public boolean reuseCollapsedTokens() {
    return true;
  }

  @Override
  public ASTNode createNode(CharSequence text) {
    return new PyStatementListImpl(text);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new PyStatementListImpl(this, null);
  }

  /**
   * Walks up the tree from the given node, checking all ancestors (including the file)
   * for error elements among their direct children. When any ancestor has errors,
   * the tree structure may be wrong (e.g., nodes that belong inside a class or function
   * may have "spilled out" to a higher level), making incremental reparse unsafe.
   * <p>
   * Skips unparsed lazy nodes to avoid triggering their parsing.
   * The walk is cheap: typically 3–7 hops, each scanning only direct children.
   */
  private static boolean anyAncestorHasErrors(@NotNull ASTNode node) {
    for (ASTNode current = node; current != null; current = current.getTreeParent()) {
      if (current instanceof LazyParseableElement lazy && !lazy.isParsed()) continue;
      if (hasErrorElements(current)) return true;
    }
    return false;
  }

  /**
   * Checks if any direct AST child of the given node is an error element.
   * <p>
   * Only checks direct children (not recursive) because:
   * <ul>
   *   <li>Errors at deeper nesting levels (inside nested if/for/def) would be the same
   *       regardless of incremental vs full reparse, so they don't indicate a reparse problem.</li>
   *   <li>Recursion into nested lazy elements would trigger their parsing,
   *       defeating the purpose of lazy parsing.</li>
   * </ul>
   */
  private static boolean hasErrorElements(@NotNull ASTNode node) {
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      if (child.getElementType() == TokenType.ERROR_ELEMENT) {
        return true;
      }
      child = child.getTreeNext();
    }
    return false;
  }

  /** Returns {@code true} if the file is large enough and the statement list is small enough for incremental reparse to be beneficial. */
  private static boolean isLargeEnoughForIncrementalReparse(@NotNull ASTNode currentNode, @NotNull CharSequence newText) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;

    PsiFile containingFile = currentNode.getPsi().getContainingFile();
    if (containingFile == null) return true;

    return isLargeEnoughForIncrementalReparse(
      containingFile.getTextLength(),
      newText.length(),
      minFileCharsThreshold(),
      maxListCharsThreshold(),
      maxRatioPercentThreshold()
    );
  }

  /**
   * Pure heuristic: file ≥ {@code minFileChars} AND list ≤ min({@code maxListChars}, file * ratio%).
   * Ratio binds below the {@code maxListChars * 100 / maxRatioPercent} crossover (100K with defaults), abs cap above.
   */
  @VisibleForTesting
  public static boolean isLargeEnoughForIncrementalReparse(int fileLength,
                                                           int newTextLength,
                                                           int minFileChars,
                                                           int maxListChars,
                                                           int maxRatioPercent) {
    if (fileLength < minFileChars) return false;
    long maxAllowed = Math.min(maxListChars, (long)fileLength * maxRatioPercent / 100);
    return newTextLength <= maxAllowed;
  }

  @Override
  public String toString() {
    return "PyStatementList";
  }
}
