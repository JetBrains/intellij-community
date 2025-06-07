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
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.lexer.PythonIndentingLexer;
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

  @SuppressWarnings("LoggerInitializedWithForeignClass")
  private static final Logger LOG = Logger.getInstance(PyReparseableElementType.class);

  public static final Key<LanguageLevel> LANGUAGE_LEVEL_KEY = Key.create("LANGUAGE_LEVEL_FOR_REPARSEABLE_ELEMENT");
  public static final Key<Integer> BASE_INDENT_KEY = Key.create("FIRST_LINE_INDENT_FOR_REPARSEABLE_ELEMENT");

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

    // Error elements on top level of previous statement list may cause some errors to remain in the PSI tree after reparse
    boolean parentContainsErrors =
      ContainerUtil.findInstance(((PyStatementListImpl)currentNode).getChildren(), PsiErrorElement.class) != null;

    if (parentContainsErrors) {
      LOG.debug("Previous node contains PsiErrorElement, reparse is declined");
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
  public static boolean checkIndentDedentBalanceWithLexer(@NotNull CharSequence text, @NotNull Lexer lexer, boolean isOnTheSameLine) {
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

    ASTNode tmp = newNode.getFirstChildNode();
    if (tmp == null) {
      return false;
    }
    LOG.debug("Element of type " + this + " reparsed successfully");
    return true;
  }

  @Override
  protected ASTNode doParseContents(@NotNull ASTNode chameleon, @NotNull PsiElement psi) {
    PsiElement parentPsiElement = chameleon.getTreeParent().getPsi();
    assert parentPsiElement != null : "parent psi is null: " + chameleon;

    Integer indent = chameleon.getUserData(BASE_INDENT_KEY);
    assert indent != null;

    LanguageLevel languageLevel = chameleon.getUserData(LANGUAGE_LEVEL_KEY);
    if (languageLevel == null) {
      languageLevel = LanguageLevel.getDefault();
    }

    PythonIndentingLexer lexer = new PythonIndentingLexerForLazyElements(indent.intValue());

    LOG.debug("Performing lazy reparse for element of type " + this);
    final PsiBuilder builder = createBuilder(parentPsiElement, chameleon, lexer);
    final PyLazyParser parser = new PyLazyParser();
    parser.setLanguageLevel(languageLevel);
    return parser.parseLazyElement(this, builder, languageLevel, PyLazyParser::parseStatementList);
  }

  private static @NotNull PsiBuilder createBuilder(@NotNull PsiElement parentPsi, @NotNull ASTNode chameleon, @NotNull Lexer lexer) {
    Language languageForParser = PythonLanguage.INSTANCE;
    return PsiBuilderFactory.getInstance().createBuilder(parentPsi.getProject(), chameleon, lexer, languageForParser, chameleon.getChars());
  }

  @Override
  public ASTNode createNode(CharSequence text) {
    return new PyStatementListImpl(text);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new PyStatementListImpl(this, null);
  }

  @Override
  public String toString() {
    return "PyStatementList";
  }
}
