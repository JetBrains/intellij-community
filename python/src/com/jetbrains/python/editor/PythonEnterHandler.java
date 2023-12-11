// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.docstrings.*;
import com.jetbrains.python.formatter.PyWhiteSpaceFormattingStrategy;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;


public final class PythonEnterHandler extends EnterHandlerDelegateAdapter {
  private int myPostprocessShift = 0;

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
    final PsiElement prevElement = offset > 0 ? file.findElementAt(offset - 1) : null;
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    if (codeInsightSettings.JAVADOC_STUB_ON_ENTER) {
      PsiElement comment = element;
      if (comment == null && offset != 0) {
        comment = prevElement;
      }
      int expectedStringStart = editor.getCaretModel().getOffset() - 3; // """ or '''
      if (comment != null) {
        final DocstringState state = canGenerateDocstring(comment, expectedStringStart, doc);
        if (state != DocstringState.NONE) {
          insertDocStringStub(editor, comment, state);
          return Result.Continue;
        }
      }
    }

    if (element == null) {
      return Result.Continue;
    }

    final ASTNode node = element.getNode();
    PsiElement elementParent = element.getParent();
    final IElementType nodeType = node.getElementType();
    if (nodeType == PyTokenTypes.LPAR) elementParent = elementParent.getParent();
    if (elementParent instanceof PyParenthesizedExpression || elementParent instanceof PyGeneratorExpression) return Result.Continue;

    final PyStringElement stringElement = PsiTreeUtil.getParentOfType(element, PyStringElement.class, false);
    if (stringElement == null && prevElement == element) {
      return Result.Continue;
    }
    
    if (stringElement != null && (!stringElement.isFormatted() ||
                                  nodeType == PyTokenTypes.FSTRING_TEXT ||
                                  // Caret should be right before the opening brace of an f-string fragment 
                                  nodeType == PyTokenTypes.FSTRING_FRAGMENT_START || 
                                  nodeType == PyTokenTypes.FSTRING_END)) {
      if (stringElement.isTripleQuoted() || nodeType == PyTokenTypes.DOCSTRING) {
        return Result.Continue;
      }
      if (prevElement != null && PsiTreeUtil.isAncestor(stringElement, prevElement, false)) {
        
        if (stringElement.getTextOffset() + stringElement.getPrefixLength() >= offset) {
          return Result.Continue;
        }

        final String pref = stringElement.getPrefix();
        final String quote = stringElement.getQuote();

        // Don't split in the middle of an escape sequence
        final boolean afterBackslash = "\\".equals(doc.getText(TextRange.from(offset - 1, 1)));
        final boolean isEscapedQuote = quote.equals(doc.getText(TextRange.from(offset, 1))) && afterBackslash;
        final boolean isEscapedBackslash = "\\".equals(doc.getText(TextRange.from(offset - 2, 1))) && afterBackslash;
        if (afterBackslash && !isEscapedQuote && !isEscapedBackslash) return Result.Continue;

        myPostprocessShift = pref.length() + quote.length();

        boolean parenthesiseOnEnter = PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER;
        final String delimiter = parenthesiseOnEnter ? "" : " \\";

        // TODO check for a non-parenthesized sequence pattern here
        if (PsiTreeUtil.getParentOfType(stringElement, PyEditorHandlerConfig.IMPLICIT_WRAP_CLASSES) != null) {
          doc.insertString(offset, quote + pref + quote);
          caretOffset.set(caretOffset.get() + 1);
        }
        else {
          int insertionOffset = offset;
          if (isEscapedQuote) {
            // Preserve the escaped quote, split after it
            caretOffset.set(caretOffset.get() + 1);
            insertionOffset++;
          }
          if (parenthesiseOnEnter) {
            parenthesise(stringElement, doc);
            caretOffset.set(caretOffset.get() + 1);
            insertionOffset++;
          }
          doc.insertString(insertionOffset, quote + delimiter + pref + quote);
          caretOffset.set(caretOffset.get() + delimiter.length() + 1);
        }
        return Result.Continue;
      }
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
    if (PyWhiteSpaceFormattingStrategy.needInsertBackslash(file, offset, autoWrapInProgress)) {
      PsiElement atCaret = file.findElementAt(offset);
      if (atCaret != null) {
        // Set of statements which can be parenthesised is a subset of statements which can be split by backslash
        if (PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER) {
          PsiElement elementToWrap = findElementToParenthesise(atCaret);
          if (elementToWrap != null) {
            parenthesise(elementToWrap, doc);
            caretOffset.set(caretOffset.get() + 1);
            return Result.Continue;
          }
        }
        doc.insertString(offset, "\\");
        caretOffset.set(caretOffset.get() + 1);
      }
    }
    return Result.Continue;
  }

  @Nullable
  private static PsiElement findElementToParenthesise(@NotNull PsiElement nodeAtCaret) {
    PsiElement wrappable = nodeAtCaret;

    while (true) {
      PsiElement next = PsiTreeUtil.getParentOfType(wrappable, PyEditorHandlerConfig.CLASSES_TO_PARENTHESISE_ON_ENTER);
      if (next == null) {
        break;
      }
      wrappable = next;
    }

    return wrappable != nodeAtCaret ? wrappable : null;
  }

  private static void parenthesise(@NotNull PsiElement wrappable, @NotNull Document doc) {
    TextRange rangeToParenthesise;
    if (wrappable instanceof PyFromImportStatement fromImportStatement) {
      rangeToParenthesise = getRangeForPsiElementArray(fromImportStatement.getImportElements());
    }
    else if (wrappable instanceof PyWithStatement withStatement) {
      rangeToParenthesise = getRangeForPsiElementArray(withStatement.getWithItems());
    }
    else {
      rangeToParenthesise = wrappable.getTextRange();
    }
    if (rangeToParenthesise != null) {
      doc.insertString(rangeToParenthesise.getEndOffset(), ")");
      doc.insertString(rangeToParenthesise.getStartOffset(), "(");
    }
  }

  @Nullable
  private static TextRange getRangeForPsiElementArray(PsiElement[] items) {
    if (!ArrayUtil.isEmpty(items)) {
      PsiElement first = ArrayUtil.getFirstElement(items);
      PsiElement last = ArrayUtil.getLastElement(items);
      if (first != null && last != null) {
        return first.getTextRange().union(last.getTextRange());
      }
    }
    return null;
  }

  private static void insertDocStringStub(Editor editor, PsiElement element, DocstringState state) {
    PyDocStringOwner docOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
    if (docOwner != null) {
      final int caretOffset = editor.getCaretModel().getOffset();
      final Document document = editor.getDocument();
      final String quotes = document.getText(TextRange.from(caretOffset - 3, 3));
      final String docString = PyDocstringGenerator.forDocStringOwner(docOwner)
        .withInferredParameters(true)
        .withQuotes(quotes)
        .forceNewMode()
        .buildDocString();
      if (state == DocstringState.INCOMPLETE) {
        document.insertString(caretOffset, docString.substring(3));
      }
      else if (state == DocstringState.EMPTY) {
        document.replaceString(caretOffset, caretOffset + 3, docString.substring(3));
      }
    }
  }

  @Override
  public Result postProcessEnter(@NotNull PsiFile file,
                                 @NotNull Editor editor,
                                 @NotNull DataContext dataContext) {
    if (!(file instanceof PyFile)) {
      return Result.Continue;
    }
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
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
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
            document.insertString(offset, GoogleCodeStyleDocStringBuilder.getDefaultSectionIndent(file));
            editor.getCaretModel().moveCaretRelatively(2, 0, false, false, false);
          }
        }
      }
    }
  }
  
  enum DocstringState {
    NONE,
    INCOMPLETE,
    EMPTY
  }

  @NotNull
  public static DocstringState canGenerateDocstring(@NotNull PsiElement element, int firstQuoteOffset, @NotNull Document document) {
    if (firstQuoteOffset < 0 || firstQuoteOffset > document.getTextLength() - 3) {
      return DocstringState.NONE;
    }
    final String quotes = document.getText(TextRange.from(firstQuoteOffset, 3));
    if (!quotes.equals("\"\"\"") && !quotes.equals("'''")) {
      return DocstringState.NONE;
    }
    final PyStringLiteralExpression pyString = DocStringUtil.getParentDefinitionDocString(element);
    if (pyString != null) {

      String nodeText = element.getText();
      final int prefixLength = PyStringLiteralUtil.getPrefixLength(nodeText);
      nodeText = nodeText.substring(prefixLength);

      final String literalText = pyString.getText();
      if (literalText.endsWith(nodeText) && nodeText.startsWith(quotes)) {
        if (firstQuoteOffset == pyString.getTextOffset() + prefixLength) {
          PsiErrorElement error = PsiTreeUtil.getNextSiblingOfType(pyString, PsiErrorElement.class);
          if (error == null) {
            error = PsiTreeUtil.getNextSiblingOfType(pyString.getParent(), PsiErrorElement.class);
          }
          if (error != null) {
            return DocstringState.INCOMPLETE;
          }
  
          if (nodeText.equals(quotes + quotes)) {
            return DocstringState.EMPTY;
          }
  
          if (nodeText.length() < 6 || !nodeText.endsWith(quotes)) {
            return DocstringState.INCOMPLETE;
          }
          // Sometimes if incomplete docstring is followed by another declaration with a docstring, it might be treated
          // as complete docstring, because we can't understand that closing quotes actually belong to another docstring.
          final String docstringIndent = PyIndentUtil.getLineIndent(document, document.getLineNumber(firstQuoteOffset));
          for (String line : LineTokenizer.tokenizeIntoList(nodeText, false)) {
            final String lineIndent = PyIndentUtil.getLineIndent(line);
            final String lineContent = line.substring(lineIndent.length());
            if ((lineContent.startsWith("def ") || lineContent.startsWith("class ")) &&
                docstringIndent.length() > lineIndent.length() && docstringIndent.startsWith(lineIndent)) {
              return DocstringState.INCOMPLETE;
            }
          }
        }
      }
    }
    return DocstringState.NONE;
  }
}
