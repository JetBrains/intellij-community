/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.AutoHardWrapHandler;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actions.SplitLineAction;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.docstrings.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;

/**
 * @author yole
 */
public class PythonEnterHandler extends EnterHandlerDelegateAdapter {
  private int myPostprocessShift = 0;

  public static final Class[] IMPLICIT_WRAP_CLASSES = new Class[] {
    PyListLiteralExpression.class,
    PySetLiteralExpression.class,
    PyDictLiteralExpression.class,
    PyDictLiteralExpression.class,
    PyParenthesizedExpression.class,
    PyArgumentList.class,
    PyParameterList.class
  };

  private static final Class[] WRAPPABLE_CLASSES = new Class[]{
    PsiComment.class,
    PyParenthesizedExpression.class,
    PyListCompExpression.class,
    PyDictCompExpression.class,
    PySetCompExpression.class,
    PyDictLiteralExpression.class,
    PySetLiteralExpression.class,
    PyListLiteralExpression.class,
    PyArgumentList.class,
    PyParameterList.class,
    PyDecoratorList.class,
    PySliceExpression.class,
    PySubscriptionExpression.class,
    PyGeneratorExpression.class
  };

  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffset,
                                @NotNull Ref<Integer> caretAdvance,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    int offset = caretOffset.get();
    if (editor instanceof EditorWindow) {
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
      editor = InjectedLanguageUtil.getTopLevelEditor(editor);
      offset = editor.getCaretModel().getOffset();
    }
    if (!(file instanceof PyFile)) {
      return Result.Continue;
    }
    final Boolean isSplitLine = DataManager.getInstance().loadFromDataContext(dataContext, SplitLineAction.SPLIT_LINE_KEY);
    if (isSplitLine != null) {
      return Result.Continue;
    }
    final Document doc = editor.getDocument();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(doc);
    final PsiElement element = file.findElementAt(offset);
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    if (codeInsightSettings.JAVADOC_STUB_ON_ENTER) {
      PsiElement comment = element;
      if (comment == null && offset != 0) {
        comment = file.findElementAt(offset - 1);
      }
      int expectedStringStart = editor.getCaretModel().getOffset() - 3; // """ or '''
      if (comment != null && atDocCommentStart(comment, expectedStringStart, doc)) {
        insertDocStringStub(editor, comment);
        return Result.Continue;
      }
    }

    if (element == null) {
      return Result.Continue;
    }

    PsiElement elementParent = element.getParent();
    if (element.getNode().getElementType() == PyTokenTypes.LPAR) elementParent = elementParent.getParent();
    if (elementParent instanceof PyParenthesizedExpression || elementParent instanceof PyGeneratorExpression) return Result.Continue;

    if (offset > 0 && !(PyTokenTypes.STRING_NODES.contains(element.getNode().getElementType()))) {
      final PsiElement prevElement = file.findElementAt(offset - 1);
      if (prevElement == element) return Result.Continue;
    }

    if (PyTokenTypes.TRIPLE_NODES.contains(element.getNode().getElementType()) ||
        element.getNode().getElementType() == PyTokenTypes.DOCSTRING) {
      return Result.Continue;
    }

    final PsiElement prevElement = file.findElementAt(offset - 1);
    PyStringLiteralExpression string = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PyStringLiteralExpression.class, false);

    if (string != null && prevElement != null && PyTokenTypes.STRING_NODES.contains(prevElement.getNode().getElementType())
        && string.getTextOffset() < offset && !(element.getNode() instanceof PsiWhiteSpace)) {
      final String stringText = element.getText();
      final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(stringText);
      if (string.getTextOffset() + prefixLength >= offset) {
        return Result.Continue;
      }
      final String pref = element.getText().substring(0, prefixLength);
      final String quote = element.getText().substring(prefixLength, prefixLength + 1);
      final boolean nextIsBackslash = "\\".equals(doc.getText(TextRange.create(offset - 1, offset)));
      final boolean isEscapedQuote = quote.equals(doc.getText(TextRange.create(offset, offset + 1))) && nextIsBackslash;
      final boolean isEscapedBackslash = "\\".equals(doc.getText(TextRange.create(offset-2, offset - 1))) && nextIsBackslash;
      if (nextIsBackslash && !isEscapedQuote && !isEscapedBackslash) return Result.Continue;

      final StringBuilder replacementString = new StringBuilder();
      myPostprocessShift = prefixLength + quote.length();

      if (PsiTreeUtil.getParentOfType(string, IMPLICIT_WRAP_CLASSES) != null) {
        replacementString.append(quote).append(pref).append(quote);
        doc.insertString(offset, replacementString);
        caretOffset.set(caretOffset.get() + 1);
        return Result.Continue;
      }
      else {
        if (isEscapedQuote) {
          replacementString.append(quote);
          caretOffset.set(caretOffset.get() + 1);
        }
        replacementString.append(quote).append(" \\").append(pref);
        if (!isEscapedQuote)
          replacementString.append(quote);
        doc.insertString(offset, replacementString.toString());
        caretOffset.set(caretOffset.get() + 3);
        return Result.Continue;
      }
    }


    if (!PyCodeInsightSettings.getInstance().INSERT_BACKSLASH_ON_WRAP) {
      return Result.Continue;
    }
    return checkInsertBackslash(file, caretOffset, dataContext, offset, doc);
  }

  private static Result checkInsertBackslash(PsiFile file,
                                             Ref<Integer> caretOffset,
                                             DataContext dataContext,
                                             int offset,
                                             Document doc) {
    boolean autoWrapInProgress = DataManager.getInstance().loadFromDataContext(dataContext,
                                                                               AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY) != null;
    if (needInsertBackslash(file, offset, autoWrapInProgress)) {
      doc.insertString(offset, "\\");
      caretOffset.set(caretOffset.get() + 1);
    }
    return Result.Continue;
  }

  public static boolean needInsertBackslash(PsiFile file, int offset, boolean autoWrapInProgress) {
    if (offset > 0) {
      final PsiElement beforeCaret = file.findElementAt(offset - 1);
      if (beforeCaret instanceof PsiWhiteSpace && beforeCaret.getText().indexOf('\\') >= 0) {
        // we've got a backslash at EOL already, don't need another one
        return false;
      }
    }
    PsiElement atCaret = file.findElementAt(offset);
    if (atCaret == null) {
      return false;
    }
    ASTNode nodeAtCaret = atCaret.getNode();
    return needInsertBackslash(nodeAtCaret, autoWrapInProgress);
  }

  public static boolean needInsertBackslash(ASTNode nodeAtCaret, boolean autoWrapInProgress) {
    PsiElement statementBefore = findStatementBeforeCaret(nodeAtCaret);
    PsiElement statementAfter = findStatementAfterCaret(nodeAtCaret);
    if (statementBefore != statementAfter) {  // Enter pressed at statement break
      return false;
    }
    if (statementBefore == null) {  // empty file
      return false;
    }

    if (PsiTreeUtil.hasErrorElements(statementBefore)) {
      if (!autoWrapInProgress) {
        // code is already bad, don't mess it up even further
        return false;
      }
      // if we're in middle of typing, it's expected that we will have error elements
    }

    if (inFromImportParentheses(statementBefore, nodeAtCaret.getTextRange().getStartOffset())) {
      return false;
    }

    PsiElement wrappableBefore = findWrappable(nodeAtCaret, true);
    PsiElement wrappableAfter = findWrappable(nodeAtCaret, false);
    if (!(wrappableBefore instanceof PsiComment)) {
      while (wrappableBefore != null) {
        PsiElement next = PsiTreeUtil.getParentOfType(wrappableBefore, WRAPPABLE_CLASSES);
        if (next == null) {
          break;
        }
        wrappableBefore = next;
      }
    }
    if (!(wrappableAfter instanceof PsiComment)) {
      while (wrappableAfter != null) {
        PsiElement next = PsiTreeUtil.getParentOfType(wrappableAfter, WRAPPABLE_CLASSES);
        if (next == null) {
          break;
        }
        wrappableAfter = next;
      }
    }
    if (wrappableBefore instanceof PsiComment || wrappableAfter instanceof PsiComment) {
      return false;
    }
    if (wrappableAfter == null) {
      return !(wrappableBefore instanceof PyDecoratorList);
    }
    return wrappableBefore != wrappableAfter;
  }

  private static void insertDocStringStub(Editor editor, PsiElement element) {
    PyDocStringOwner docOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
    if (docOwner != null) {
      final int caretOffset = editor.getCaretModel().getOffset();
      final String quotes = editor.getDocument().getText(TextRange.from(caretOffset - 3, 3));
      final String docString = PyDocstringGenerator.forDocStringOwner(docOwner)
        .withInferredParameters(true)
        .withQuotes(quotes)
        .forceNewMode()
        .buildDocString();
      editor.getDocument().replaceString(caretOffset - 3, caretOffset, docString);
    }
  }

  @Nullable
  private static PsiElement findWrappable(ASTNode nodeAtCaret, boolean before) {
    PsiElement wrappable = before
                                 ? findBeforeCaret(nodeAtCaret, WRAPPABLE_CLASSES)
                                 : findAfterCaret(nodeAtCaret, WRAPPABLE_CLASSES);
    if (wrappable == null) {
      PsiElement emptyTuple = before
                              ? findBeforeCaret(nodeAtCaret, PyTupleExpression.class)
                              : findAfterCaret(nodeAtCaret, PyTupleExpression.class);
      if (emptyTuple != null && emptyTuple.getNode().getFirstChildNode().getElementType() == PyTokenTypes.LPAR) {
        wrappable = emptyTuple;
      }
    }
    return wrappable;
  }

  @Nullable
  private static PsiElement findStatementBeforeCaret(ASTNode node) {
    return findBeforeCaret(node, PyStatement.class);
  }

  @Nullable
  private static PsiElement findStatementAfterCaret(ASTNode node) {
    return findAfterCaret(node, PyStatement.class);
  }

  private static PsiElement findBeforeCaret(ASTNode atCaret, Class<? extends PsiElement>... classes) {
    while (atCaret != null) {
      atCaret = TreeUtil.prevLeaf(atCaret);
      if (atCaret != null && atCaret.getElementType() != TokenType.WHITE_SPACE) {
        return getNonStrictParentOfType(atCaret.getPsi(), classes);
      }
    }
    return null;
  }

  private static PsiElement findAfterCaret(ASTNode atCaret, Class<? extends PsiElement>... classes) {
    while (atCaret != null) {
      if (atCaret.getElementType() != TokenType.WHITE_SPACE) {
        return getNonStrictParentOfType(atCaret.getPsi(), classes);
      }
      atCaret = TreeUtil.nextLeaf(atCaret);
    }
    return null;
  }

  @Nullable
  private static <T extends PsiElement> T getNonStrictParentOfType(@NotNull PsiElement element, @NotNull Class<? extends T>... classes) {
    PsiElement run = element;
    while (run != null) {
      for (Class<? extends T> aClass : classes) {
        if (aClass.isInstance(run)) return (T)run;
      }
      if (run instanceof PsiFile || run instanceof PyStatementList) break;
      run = run.getParent();
    }

    return null;
  }

  private static boolean inFromImportParentheses(PsiElement statement, int offset) {
    if (!(statement instanceof PyFromImportStatement)) {
      return false;
    }
    PyFromImportStatement fromImportStatement = (PyFromImportStatement)statement;
    PsiElement leftParen = fromImportStatement.getLeftParen();
    if (leftParen != null && offset >= leftParen.getTextRange().getEndOffset()) {
      return true;
    }
    return false;
  }

  @Override
  public Result postProcessEnter(@NotNull PsiFile file,
                                 @NotNull Editor editor,
                                 @NotNull DataContext dataContext) {
    if (myPostprocessShift > 0) {
      editor.getCaretModel().moveCaretRelatively(myPostprocessShift, 0, false, false, false);
      myPostprocessShift = 0;
      return Result.Continue;
    }
    addGoogleDocStringSectionIndent(file, editor, editor.getCaretModel().getOffset());
    return super.postProcessEnter(file, editor, dataContext);
  }

  private static void addGoogleDocStringSectionIndent(@NotNull PsiFile file, @NotNull Editor editor, int offset) {
    final Document document = editor.getDocument();
    final PsiElement element = file.findElementAt(offset);
    if (element != null) {
      // Insert additional indentation after section header in Google code style docstrings
      final PyStringLiteralExpression pyString = DocStringUtil.getParentDefinitionDocString(element);
      if (pyString != null) {
        final String docStringText = pyString.getText();
        final DocStringFormat format = DocStringUtil.guessDocStringFormat(docStringText, pyString);
        if (format == DocStringFormat.GOOGLE && offset + 1 < document.getTextLength()) {
          final int lineNum = document.getLineNumber(offset);
          final TextRange lineRange = TextRange.create(document.getLineStartOffset(lineNum - 1), document.getLineEndOffset(lineNum - 1));
          final Matcher matcher = GoogleCodeStyleDocString.SECTION_HEADER.matcher(document.getText(lineRange));
          if (matcher.matches() && SectionBasedDocString.isValidSectionTitle(matcher.group(1))) {
            document.insertString(offset, GoogleCodeStyleDocStringBuilder.getDefaultSectionIndent(file.getProject()));
            editor.getCaretModel().moveCaretRelatively(2, 0, false, false, false);
          }
        }
      }
    }
  }

  public static boolean atDocCommentStart(@NotNull PsiElement element, int firstQuoteOffset, @NotNull Document document) {
    if (firstQuoteOffset < 0 || firstQuoteOffset > document.getTextLength() - 3) {
      return false;
    }
    final String quotes = document.getText(TextRange.from(firstQuoteOffset, 3));
    if (!quotes.equals("\"\"\"") && !quotes.equals("'''")) {
      return false;
    }
    final PyStringLiteralExpression pyString = DocStringUtil.getParentDefinitionDocString(element);
    if (pyString != null) {
      String nodeText = element.getText();
      final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(nodeText);
      nodeText = nodeText.substring(prefixLength);
      final String literalText = pyString.getText();
      if (literalText.endsWith(nodeText) && nodeText.startsWith(quotes)) {
        if (firstQuoteOffset == pyString.getTextOffset() + prefixLength) {
          PsiErrorElement error = PsiTreeUtil.getNextSiblingOfType(pyString, PsiErrorElement.class);
          if (error != null) {
            return true;
          }
          error = PsiTreeUtil.getNextSiblingOfType(pyString.getParent(), PsiErrorElement.class);
          if (error != null) {
            return true;
          }

          if (nodeText.length() < 6 || !nodeText.endsWith(quotes)) {
            return true;
          }
          // Sometimes if incomplete docstring is followed by another declaration with a docstring, it might be treated
          // as complete docstring, because we can't understand that closing quotes actually belong to another docstring.
          final String docstringIndent = PyIndentUtil.getLineIndent(document, document.getLineNumber(firstQuoteOffset));
          for (String line : LineTokenizer.tokenizeIntoList(nodeText, false)) {
            final String lineIndent = (String)PyIndentUtil.getLineIndent(line);
            final String lineContent = line.substring(lineIndent.length());
            if ((lineContent.startsWith("def ") || lineContent.startsWith("class ")) &&
                docstringIndent.length() > lineIndent.length() && docstringIndent.startsWith(lineIndent)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}
