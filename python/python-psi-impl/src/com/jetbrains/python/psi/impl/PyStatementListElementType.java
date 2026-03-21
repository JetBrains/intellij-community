package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.ICompositeElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.lexer.PythonIndentingLexerForLazyElements;
import com.jetbrains.python.parsing.PyLazyParser;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyReparseableElementType;
import org.jetbrains.annotations.NotNull;

public class PyStatementListElementType extends PyReparseableElementType implements ICompositeElementType {

  public PyStatementListElementType() {
    super("PyStatementList");
  }

  private static final Logger LOG = Logger.getInstance(PyStatementListElementType.class);

  private static final Key<LanguageLevel> LANGUAGE_LEVEL_KEY = Key.create("LANGUAGE_LEVEL_FOR_REPARSEABLE_ELEMENT");
  private static final Key<Integer> BASE_INDENT_KEY = Key.create("FIRST_LINE_INDENT_FOR_REPARSEABLE_ELEMENT");

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

    // Error elements in the previous statement list may cause some errors to remain in the PSI tree after reparse
    if (hasErrorElements(currentNode)) {
      LOG.debug("Previous node contains error elements, reparse is declined");
      return false;
    }

    boolean isAfterColonOnSameLine = isAfterColonOnSameLine((PyStatementListImpl)currentNode);

    String firstLineIndent = isAfterColonOnSameLine ? "" : PyIndentUtil.getElementIndent(currentNode.getPsi());

    PythonIndentingLexerForLazyElements lexer = new PythonIndentingLexerForLazyElements(firstLineIndent.length());
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
    while (lexer.getTokenType() != null) {
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
    String firstLineIndent = isAfterColonOnSameLine(oldNode.getPsi()) ? "" : PyIndentUtil.getElementIndent(oldNode.getPsi());
    if (!(file instanceof PyFile)) return false;

    LanguageLevel languageLevel = LanguageLevel.forElement(file);

    newNode.putUserData(LANGUAGE_LEVEL_KEY, languageLevel);
    newNode.putUserData(BASE_INDENT_KEY, firstLineIndent.length());

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

    Integer indent = chameleon.getUserData(BASE_INDENT_KEY);
    if (indent == null) {
      LOG.error("BASE_INDENT_KEY is missing on chameleon node, falling back to 0");
      indent = 0;
    }

    LanguageLevel languageLevel = chameleon.getUserData(LANGUAGE_LEVEL_KEY);
    if (languageLevel == null) {
      languageLevel = LanguageLevel.getDefault();
    }

    Lexer lexer = new PythonIndentingLexerForLazyElements(indent);
    PsiBuilder builder = PsiBuilderFactory.getInstance()
      .createBuilder(parentPsiElement.getProject(), chameleon, lexer, PythonLanguage.INSTANCE, chameleon.getChars());

    PyLazyParser parser = new PyLazyParser();
    parser.setLanguageLevel(languageLevel);
    return parser.parseLazyElement(this, builder, languageLevel, PyLazyParser::parseStatementList);
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

  @Override
  public String toString() {
    return "PyStatementList";
  }
}
