package com.jetbrains.python.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.AutoHardWrapHandler;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actions.SplitLineAction;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PythonEnterHandler extends EnterHandlerDelegateAdapter {
  private static final Class[] IMPLICIT_WRAP_CLASSES = new Class[]{
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
    PyFunction.class,
    PySliceExpression.class,
    PySubscriptionExpression.class,
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
      file = InjectedLanguageUtil.getTopLevelFile(file);
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
    Document doc = editor.getDocument();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(doc);
    final PsiElement element = file.findElementAt(offset);
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    if (codeInsightSettings.JAVADOC_STUB_ON_ENTER) {
      PsiElement comment = element;
      if (comment == null && offset != 0) {
        comment = file.findElementAt(offset - 1);
      }
      int expectedStringStart = editor.getCaretModel().getOffset() - 3; // """ or '''
      if (PythonDocCommentUtil.atDocCommentStart(comment, expectedStringStart)) {
        insertDocStringStub(editor, comment);
        return Result.Continue;
      }
    }

    if (element == null) {
      return Result.Continue;
    }

    final PsiElement elementParent = element.getParent();
    if (elementParent instanceof PyParenthesizedExpression) return Result.Continue;

    if (offset > 0 && !(PyTokenTypes.STRING_NODES.contains(element.getNode().getElementType()))) {
      final PsiElement prevElement = file.findElementAt(offset - 1);
      if (prevElement == element) return Result.Continue;
    }

    if (PyTokenTypes.TRIPLE_NODES.contains(element.getNode().getElementType()) ||
        element.getNode().getElementType() == PyTokenTypes.DOCSTRING) {
      return Result.Continue;
    }

    final PyStringLiteralExpression string = PsiTreeUtil.findElementOfClassAtOffset(file, offset+1, PyStringLiteralExpression.class, false);
    if (string != null && string.getTextOffset() < offset && !(element.getNode() instanceof PsiWhiteSpace)) {
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

      final PsiElement parent = string.getParent();
      final StringBuilder replacementString = new StringBuilder();
      if (parent instanceof PyListLiteralExpression || parent instanceof PyParenthesizedExpression ||
          parent instanceof PyBinaryExpression || parent instanceof PySetLiteralExpression ||
          parent instanceof PyKeyValueExpression || parent instanceof PyNamedParameter || parent instanceof PyArgumentList) {
        replacementString.append(quote + pref + quote);
        doc.insertString(offset, replacementString);
        caretOffset.set(caretOffset.get() + 1);
        return Result.Continue;
      }
      else {
        if (isEscapedQuote) {
          replacementString.append(quote);
          caretOffset.set(caretOffset.get() + 1);
        }
        replacementString.append(quote + " \\" + pref);
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
    if (offset > 0) {
      final PsiElement beforeCaret = file.findElementAt(offset - 1);
      if (beforeCaret instanceof PsiWhiteSpace && beforeCaret.getText().indexOf('\\') > 0) {
        // we've got a backslash at EOL already, don't need another one
        return Result.Continue;
      }
    }
    PsiElement statementBefore = findStatementBeforeCaret(file, offset);
    PsiElement statementAfter = findStatementAfterCaret(file, offset);
    if (statementBefore != statementAfter) {  // Enter pressed at statement break
      return Result.Continue;
    }
    if (statementBefore == null) {  // empty file
      return Result.Continue;
    }

    if (PsiTreeUtil.hasErrorElements(statementBefore)) {
      final Boolean autoWrapping =
        DataManager.getInstance().loadFromDataContext(dataContext, AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY);
      if (autoWrapping == null) {
        // code is already bad, don't mess it up even further
        return Result.Continue;
      }
      // if we're in middle of typing, it's expected that we will have error elements
    }

    if (inFromImportParentheses(statementBefore, offset)) {
      return Result.Continue;
    }

    PsiElement wrappableBefore = findWrappable(file, offset, true);
    PsiElement wrappableAfter = findWrappable(file, offset, false);
    if (!(wrappableBefore instanceof PsiComment)) {
      while (wrappableBefore != null) {
        PsiElement next = PsiTreeUtil.getParentOfType(wrappableBefore, IMPLICIT_WRAP_CLASSES);
        if (next == null) {
          break;
        }
        wrappableBefore = next;
      }
    }
    if (!(wrappableAfter instanceof PsiComment)) {
      while (wrappableAfter != null) {
        PsiElement next = PsiTreeUtil.getParentOfType(wrappableAfter, IMPLICIT_WRAP_CLASSES);
        if (next == null) {
          break;
        }
        wrappableAfter = next;
      }
    }
    if (wrappableBefore instanceof PsiComment || wrappableAfter instanceof PsiComment) {
      return Result.Continue;
    }
    if (wrappableAfter == null || wrappableBefore != wrappableAfter) {
      doc.insertString(offset, "\\");
      caretOffset.set(caretOffset.get() + 1);
    }
    return Result.Continue;
  }

  private static void insertDocStringStub(Editor editor, PsiElement element) {
    PythonDocumentationProvider provider = new PythonDocumentationProvider();
    PyFunction fun = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (fun != null) {
      String docStub = provider.generateDocumentationContentStub(fun, false);
      docStub += element.getParent().getText().substring(0,3);
      if (docStub != null && docStub.length() != 0) {
        editor.getDocument().insertString(editor.getCaretModel().getOffset(), docStub);
        return;
      }
    }
    PyElement klass = PsiTreeUtil.getParentOfType(element, PyClass.class, PyFile.class);
    if (klass != null) {
      editor.getDocument().insertString(editor.getCaretModel().getOffset(),
                      PythonDocCommentUtil.generateDocForClass(klass, element.getParent().getText().substring(0, 3)));
    }
  }

  @Nullable
  private static PsiElement findWrappable(PsiFile file, int offset, boolean before) {
    PsiElement wrappable = before
                                 ? findBeforeCaret(file, offset, IMPLICIT_WRAP_CLASSES)
                                 : findAfterCaret(file, offset, IMPLICIT_WRAP_CLASSES);
    if (wrappable == null) {
      PsiElement emptyTuple = before
                              ? findBeforeCaret(file, offset, PyTupleExpression.class)
                              : findAfterCaret(file, offset, PyTupleExpression.class);
      if (emptyTuple != null && emptyTuple.getNode().getFirstChildNode().getElementType() == PyTokenTypes.LPAR) {
        wrappable = emptyTuple;
      }
    }
    return wrappable;
  }

  @Nullable
  private static PsiElement findStatementBeforeCaret(PsiFile file, int offset) {
    return findBeforeCaret(file, offset, PyStatement.class);
  }

  @Nullable
  private static PsiElement findStatementAfterCaret(PsiFile file, int offset) {
    return findAfterCaret(file, offset, PyStatement.class);
  }

  @Nullable
  private static PsiElement findBeforeCaret(PsiFile file, int offset, Class<? extends PsiElement>... classes) {
    while(offset > 0) {
      offset--;
      final PsiElement element = file.findElementAt(offset);
      if (element != null && !(element instanceof PsiWhiteSpace)) {
        return getNonStrictParentOfType(element, classes);
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement findAfterCaret(PsiFile file, int offset, Class<? extends PsiElement>... classes) {
    while(offset < file.getTextLength()) {
      final PsiElement element = file.findElementAt(offset);
      if (element != null && !(element instanceof PsiWhiteSpace)) {
        return getNonStrictParentOfType(element, classes);
      }
      offset++;
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
    if (leftParen != null && offset > leftParen.getTextRange().getEndOffset()) {
      return true;
    }
    return false;
  }
}
