package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.ide.DataManager;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.Highlighter;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspToken;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;

public class EnterHandler extends EditorWriteActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.EnterHandler");

  private EditorActionHandler myOriginalHandler;

  public EnterHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return myOriginalHandler.isEnabled(editor, dataContext);
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    /*
    if (!settings.SMART_ENTER_ACTION) {
      myOriginalHandler.execute(editor, dataContext);
      return;
    }
    */

    Project project = (Project)DataManager.getInstance().getDataContext(editor.getComponent()).getData(
      DataConstants.PROJECT);
    if (project == null) {
      myOriginalHandler.execute(editor, dataContext);
      return;
    }
    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (file == null) {
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    int caretAdvance = 0;

    CommandProcessor.getInstance().setCurrentCommandName("Typing");

    EditorModificationUtil.deleteSelectedText(editor);

    int caretOffset = editor.getCaretModel().getOffset();
    CharSequence text = document.getCharsSequence();
    int length = document.getTextLength();
    if (caretOffset < length && text.charAt(caretOffset) != '\n') {
      int offset1 = CharArrayUtil.shiftBackward(text, caretOffset, " \t");
      if (offset1 < 0 || text.charAt(offset1) == '\n') {
        int offset2 = CharArrayUtil.shiftForward(text, offset1 + 1, " \t");
        boolean isEmptyLine = offset2 >= length || text.charAt(offset2) == '\n';
        if (!isEmptyLine) { // we are in leading spaces of a non-empty line
          myOriginalHandler.execute(editor, dataContext);
          return;
        }
      }
    }

    boolean forceIndent = false;

    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiElement psiAtOffset = file.findElementAt(caretOffset);
    if (psiAtOffset instanceof PsiJavaToken && psiAtOffset.getTextOffset() < caretOffset) {
      PsiJavaToken token = (PsiJavaToken)psiAtOffset;
      if (token.getTokenType() == JavaTokenType.STRING_LITERAL) {
        TextRange range = token.getTextRange();
        final StringLiteralLexer lexer = new StringLiteralLexer('\"');
        char[] chars = CharArrayUtil.fromSequence(text);
        lexer.start(chars, range.getStartOffset(), range.getEndOffset());
        while (lexer.getTokenType() != null) {
          if (lexer.getTokenStart() < caretOffset && caretOffset < lexer.getTokenEnd()) {
            if (lexer.getTokenType() == StringEscapesTokenTypes.INVALID_STRING_ESCAPE_TOKEN ||
                lexer.getTokenType() == StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN) {
              caretOffset = lexer.getTokenEnd();
            }
            break;
          }
          lexer.advance();
        }

        document.insertString(caretOffset, "\" + \"");
        text = document.getCharsSequence();
        caretOffset += "\" +".length();
        caretAdvance = 1;
        if (CodeStyleSettingsManager.getSettings(project).BINARY_OPERATION_SIGN_ON_NEXT_LINE) {
          caretOffset -= 1;
          caretAdvance = 3;
        }
        forceIndent = true;
      }
      else if (token.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        int offset = CharArrayUtil.shiftForward(text, caretOffset, " \t");
        if (offset < document.getTextLength() && text.charAt(offset) != '\n') {
          document.insertString(caretOffset, "// ");
          text = document.getCharsSequence();
        }
      }
    }

    if (settings.INSERT_BRACE_ON_ENTER && isAfterUnmatchedLBrace(editor, caretOffset, file.getFileType())) {
      int offset = CharArrayUtil.shiftForward(text, caretOffset, " \t");
      if (offset < document.getTextLength()) {
        char c = text.charAt(offset);
        if (c != ')' && c != ']' && c != ';' && c != ',' && c != '%') {
          offset = CharArrayUtil.shiftForwardUntil(text, caretOffset, "\n");
        }
      }
      offset = Math.min(offset, document.getTextLength());

      document.insertString(offset, "\n}");
      PsiDocumentManager.getInstance(project).commitDocument(document);
      try {
        CodeStyleManager.getInstance(project).adjustLineIndent(file, offset + 1);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      text = document.getCharsSequence();
      forceIndent = true;
    }

    if (settings.INSERT_SCRIPTLET_END_ON_ENTER && isAfterUnmatchedScriplet(editor, caretOffset)) {
      document.insertString(caretOffset, "%>");
      myOriginalHandler.execute(editor, dataContext);
      text = document.getCharsSequence();
      forceIndent = true;
    }

    if (settings.SMART_INDENT_ON_ENTER) {
      // special case: enter inside "()" or "{}"
      if (caretOffset > 0 && caretOffset < text.length() &&
          ((text.charAt(caretOffset - 1) == '(' && text.charAt(caretOffset) == ')')
           || (text.charAt(caretOffset - 1) == '{' && text.charAt(caretOffset) == '}'))
      ) {
        myOriginalHandler.execute(editor, dataContext);
        PsiDocumentManager.getInstance(project).commitDocument(document);
        try {
          CodeStyleManager.getInstance(project).adjustLineIndent(file, editor.getCaretModel().getOffset());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        text = document.getCharsSequence();
      }
    }

    if (file instanceof XmlFile && isBetweenXmlTags(editor, caretOffset) ||
        file instanceof JspFile && isBetweenJspTags(editor, caretOffset)
        ) {
      myOriginalHandler.execute(editor, dataContext);
      text = document.getCharsSequence();
      forceIndent = true;
    }

    boolean isFirstColumn = caretOffset == 0 || text.charAt(caretOffset - 1) == '\n';
    final boolean insertSpace = !isFirstColumn
                                &&
                                !(caretOffset >= document.getTextLength() || text.charAt(caretOffset) == ' ' ||
                                  text.charAt(caretOffset) == '\t');
    // to prevent keeping some elements (e.g. comments) at first column
    if (settings.SMART_INDENT_ON_ENTER || forceIndent) {
      String toInsert = insertSpace && CodeStyleSettingsManager.getSettings(project).INSERT_FIRST_SPACE_IN_LINE ? "\n " : "\n";
      document.insertString(caretOffset, toInsert);
      caretOffset += 1;
      if (insertSpace && file instanceof JspFile) {
        PsiDocumentManager.getInstance(project).commitDocument(document);
        PsiElement elementAt = file.findElementAt(caretOffset);
        if (elementAt instanceof JspToken && ((JspToken)elementAt).getTokenType() == JspTokenType.JSP_TEMPLATE_DATA) {
          if (insertSpace) document.deleteString(caretOffset, caretOffset + 1);
        }
      }
    }
    else {
      myOriginalHandler.execute(editor, dataContext);
      caretOffset = editor.getCaretModel().getOffset();
    }


    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final DoEnterAction action = new DoEnterAction(
      file,
      editor,
      document,
      caretOffset,
      !insertSpace,
      caretAdvance);
    action.setForceIndent(forceIndent);
    action.run();
  }

  private boolean isAfterUnmatchedScriplet(Editor editor, int offset) {
    CharSequence chars = editor.getDocument().getCharsSequence();

    if (!(offset >= 3 && chars.charAt(offset - 1) == '!' && chars.charAt(offset - 2) == '%' && chars.charAt(offset - 3) == '<') &&
        !(offset >= 2 && chars.charAt(offset - 1) == '%' && chars.charAt(offset - 2) == '<')) {
      return false;
    }

    Highlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 2);
    if (iterator.getTokenType() != JspTokenType.JSP_SCRIPTLET_START
        && iterator.getTokenType() != JspTokenType.JSP_DECLARATION_START) {
      return false;
    }

    iterator = highlighter.createIterator(offset);
    while (!iterator.atEnd()) {
      IElementType tokenType = iterator.getTokenType();

      if (tokenType == JspTokenType.JSP_SCRIPTLET_START || tokenType == JspTokenType.JSP_DECLARATION_START) {
        return true;
      }
      if (tokenType == JspTokenType.JSP_SCRIPTLET_END || tokenType == JspTokenType.JSP_DECLARATION_END) {
        return false;
      }
      iterator.advance();
    }

    return true;
  }

  private boolean isDocCommentComplete(PsiDocComment comment) {
    String commentText = comment.getText();
    if (!commentText.endsWith("*/")) return false;

    Lexer lexer = new JavaLexer(comment.getManager().getEffectiveLanguageLevel());
    lexer.start(commentText.toCharArray(), "/**".length(), commentText.length());
    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) {
        return false;
      }
      if (tokenType == JavaTokenType.STRING_LITERAL || tokenType == JavaTokenType.CHARACTER_LITERAL) {
        String text = commentText.substring(lexer.getTokenStart(), lexer.getTokenEnd());
        if (text.endsWith("*/")) return true;
      }
      if (lexer.getTokenEnd() == commentText.length()) {
        return (lexer.getTokenEnd() - lexer.getTokenStart() == 1);
      }
      if (tokenType == JavaTokenType.DOC_COMMENT || tokenType == JavaTokenType.C_STYLE_COMMENT) {
        return false;
      }
      lexer.advance();
    }
  }

  public static boolean isAfterUnmatchedLBrace(Editor editor, int offset, FileType fileType) {
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    if (chars.charAt(offset - 1) != '{') return false;

    Highlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    BraceMatchingUtil.BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);

    if (!braceMatcher.isLBraceToken(iterator, chars, fileType) ||
        !braceMatcher.isStructuralBrace(iterator, chars, fileType)
        ) {
      return false;
    }

    iterator = highlighter.createIterator(0);
    int balance = 0;
    while (!iterator.atEnd()) {
      if (braceMatcher.isStructuralBrace(iterator,chars, fileType)) {
        if (braceMatcher.isLBraceToken(iterator,chars, fileType)) {
          balance++;
        }
        else if (braceMatcher.isRBraceToken(iterator,chars, fileType)) {
          balance--;
        }
      }
      iterator.advance();
    }
    return balance > 0;
  }

  private boolean isBetweenXmlTags(Editor editor, int offset) {
    return isBetweenTags(editor,offset,XmlTokenType.XML_TAG_END,XmlTokenType.XML_END_TAG_START);
  }

  private boolean isBetweenJspTags(Editor editor, int offset) {
    return isBetweenTags(editor,offset,JspTokenType.JSP_ACTION_END,JspTokenType.JSP_ACTION_END_TAG_START) ||
           isBetweenXmlTags(editor,offset);
  }

  private boolean isBetweenTags(Editor editor, int offset, IElementType first, IElementType second) {
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    if (chars.charAt(offset - 1) != '>') return false;

    Highlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    if (iterator.getTokenType() != first) return false;
    iterator.advance();
    return !iterator.atEnd() && iterator.getTokenType() == second;
  }

  private class DoEnterAction implements Runnable {
    private PsiFile myFile;
    private int myOffset;
    private Document myDocument;
    private boolean myInsertSpace;
    private final Editor myEditor;
    private int myCaretAdvance;

    private boolean myForceIndent = false;

    public DoEnterAction(
      PsiFile file,
      Editor view,
      Document document,
      int offset,
      boolean insertSpace,
      int caretAdvance) {
      myEditor = view;
      myFile = file;
      myOffset = offset;
      myDocument = document;
      myInsertSpace = insertSpace;
      myCaretAdvance = caretAdvance;
    }

    public void setForceIndent(boolean forceIndent) {
      myForceIndent = forceIndent;
    }

    public void run() {
      try {
        final CharSequence chars = myDocument.getCharsSequence();

        int offset = CharArrayUtil.shiftBackwardUntil(chars, myOffset - 1, "\n") - 1;
        offset = CharArrayUtil.shiftBackwardUntil(chars, offset, "\n") + 1;
        int lineStart = CharArrayUtil.shiftForward(chars, offset, " \t");

        boolean docStart = CharArrayUtil.regionMatches(chars, lineStart, "/**");
        boolean docAsterisk = CharArrayUtil.regionMatches(chars, lineStart, "*");
        boolean slashSlash = CharArrayUtil.regionMatches(chars, lineStart, "//") &&
                             chars.charAt(CharArrayUtil.shiftForward(chars, myOffset, " \t")) != '\n';

        if (docStart) {
          PsiElement element = myFile.findElementAt(lineStart);
          if (element.getText().equals("/**") && element.getParent() instanceof PsiDocComment) {
            PsiDocComment comment = (PsiDocComment)element.getParent();
            int commentEnd = comment.getTextRange().getEndOffset();
            if (myOffset >= commentEnd) {
              docStart = false;
            }
            else {
              if (isDocCommentComplete(comment)) {
                if (myOffset >= commentEnd) {
                  docAsterisk = false;
                  docStart = false;
                }
                else {
                  docAsterisk = true;
                  docStart = false;
                }
              }
              else {
                generateJavadoc();
              }
            }
          }
          else {
            docStart = false;
          }
        }

        if (docAsterisk) {
          docAsterisk = insertDocAsterisk(lineStart, docAsterisk);
        }

        if (CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER || myForceIndent || docStart || docAsterisk ||
            slashSlash) {
          myOffset = CodeStyleManager.getInstance(myFile.getProject()).adjustLineIndent(myFile, myOffset);
        }

        if (docAsterisk || docStart || slashSlash) {
          if (myInsertSpace) {
            if (myOffset == myDocument.getTextLength()) {
              myDocument.insertString(myOffset, " ");
            }
            myDocument.insertString(myOffset + 1, " ");
          }

          final char c = myDocument.getCharsSequence().charAt(myOffset);
          if (c != '\n') {
            myOffset += 1;
          }
        }

        if ((docAsterisk || slashSlash) && !docStart) {
          myCaretAdvance = slashSlash ? 2 : 1;
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

      myEditor.getCaretModel().moveToOffset(myOffset);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      myEditor.getSelectionModel().removeSelection();
      if (myCaretAdvance != 0) {
        LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();
        LogicalPosition pos = new LogicalPosition(caretPosition.line, caretPosition.column + myCaretAdvance);
        myEditor.getCaretModel().moveToLogicalPosition(pos);
      }
    }

    private void generateJavadoc() throws IncorrectOperationException {
      CodeInsightSettings settings = CodeInsightSettings.getInstance();
      final String lineSeparator = "\n";

      StringBuffer buffer = new StringBuffer();
      buffer.append("*");
      buffer.append(lineSeparator);
      buffer.append("*/");

      myDocument.insertString(myOffset, buffer.toString());

      final Project project = myFile.getProject();
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      CodeStyleManager.getInstance(project).adjustLineIndent(myFile, myOffset + buffer.length() - 2);

      PsiDocComment comment = PsiTreeUtil.getParentOfType(myFile.findElementAt(myOffset),
                                                                         PsiDocComment.class);
      final PsiElement context = comment.getParent();

      if (settings.JAVADOC_STUB_ON_ENTER) {
        if (context instanceof PsiMethod) {
          PsiMethod psiMethod = (PsiMethod)context;
          if (psiMethod.getDocComment() != comment) return;

          buffer = new StringBuffer();

          final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            buffer.append("* @param ");
            buffer.append(parameter.getName());
            buffer.append(lineSeparator);
          }

          if (psiMethod.getReturnType() != null && psiMethod.getReturnType() != PsiType.VOID) {
            buffer.append("* @return");
            buffer.append(lineSeparator);
          }

          final PsiJavaCodeReferenceElement[] references = psiMethod.getThrowsList().getReferenceElements();
          for (int i = 0; i < references.length; i++) {
            PsiJavaCodeReferenceElement reference = references[i];
            buffer.append("* @throws ");
            buffer.append(reference.getText());
            buffer.append(lineSeparator);
          }

          if (buffer.length() != 0) {
            myOffset = CharArrayUtil.shiftForwardUntil(myDocument.getCharsSequence(), myOffset, lineSeparator);
            myOffset = CharArrayUtil.shiftForward(myDocument.getCharsSequence(), myOffset, lineSeparator);
            myDocument.insertString(myOffset, buffer.toString());
          }
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();
        comment = PsiTreeUtil.getParentOfType(myFile.findElementAt(myOffset), PsiDocComment.class);
      }

      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myFile.getProject());
      comment = (PsiDocComment)codeStyleManager.reformat(comment);
      PsiElement next = comment.getNextSibling();
      if (!(next instanceof PsiWhiteSpace) || -1 == (((PsiWhiteSpace)next).getText().indexOf(lineSeparator))) {
        int lineBreakOffset = comment.getTextRange().getEndOffset();
        myDocument.insertString(lineBreakOffset, lineSeparator);
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        codeStyleManager.adjustLineIndent(myFile, lineBreakOffset + 1);
        comment = PsiTreeUtil.getParentOfType(myFile.findElementAt(myOffset), PsiDocComment.class);
      }

      myOffset = comment.getTextRange().getStartOffset();
      myOffset = CharArrayUtil.shiftForwardUntil(myDocument.getCharsSequence(), myOffset, lineSeparator);
      myOffset = CharArrayUtil.shiftForward(myDocument.getCharsSequence(), myOffset, lineSeparator);
      myOffset = CharArrayUtil.shiftForwardUntil(myDocument.getCharsSequence(), myOffset, lineSeparator);
      myDocument.insertString(myOffset, " ");
      myOffset++;
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }

    private boolean insertDocAsterisk(int lineStart, boolean docAsterisk) {
      PsiElement element = myFile.findElementAt(lineStart);

      if ((element.getText().equals("*") || element.getText().equals("/**"))) {
        PsiDocComment comment = null;
        if (element.getParent() instanceof PsiDocComment) {
          comment = (PsiDocComment)element.getParent();
        }
        else if (element.getParent() instanceof PsiDocTag && element.getParent().getParent() instanceof PsiDocComment) {
          comment = (PsiDocComment)element.getParent().getParent();
        }
        if (comment != null) {
          int commentEnd = comment.getTextRange().getEndOffset();
          if (myOffset >= commentEnd) {
            docAsterisk = false;
          }
          else {
            myDocument.insertString(myOffset, "*");
            PsiDocumentManager.getInstance(myFile.getProject()).commitAllDocuments();
          }
        }
        else {
          docAsterisk = false;
        }
      }
      else {
        docAsterisk = false;
      }
      return docAsterisk;
    }
  }
}
