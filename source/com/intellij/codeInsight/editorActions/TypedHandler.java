package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.JspParsingUtil;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.jsp.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;

import java.util.HashMap;
import java.util.Map;

public class TypedHandler implements TypedActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.TypedHandler");

  private TypedActionHandler myOriginalHandler;

  public interface QuoteHandler {
    boolean isClosingQuote(HighlighterIterator iterator, int offset);
    boolean isOpeningQuote(HighlighterIterator iterator, int offset);
    boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset);
    boolean isInsideLiteral(HighlighterIterator iterator);
  }

  private static final Map<FileType,QuoteHandler> quoteHandlers = new HashMap<FileType, QuoteHandler>();

  public static QuoteHandler getQuoteHandler(FileType fileType) {
    return quoteHandlers.get(fileType);
  }

  public static void registerQuoteHandler(FileType fileType, QuoteHandler quoteHandler) {
    quoteHandlers.put(fileType, quoteHandler);
  }

  static {
    registerQuoteHandler(StdFileTypes.JAVA, new JavaQuoteHandler());
    registerQuoteHandler(StdFileTypes.XML, new XmlQuoteHandler());
    HtmlQuoteHandler quoteHandler = new HtmlQuoteHandler();
    registerQuoteHandler(StdFileTypes.HTML, quoteHandler);
    registerQuoteHandler(StdFileTypes.XHTML, quoteHandler);
    registerQuoteHandler(StdFileTypes.JSP, new HtmlQuoteHandler(new JavaQuoteHandler()));
  }

  public static class HtmlQuoteHandler implements QuoteHandler {
    private static QuoteHandler ourStyleQuoteHandler;
    private QuoteHandler myBaseQuoteHandler;
    private static QuoteHandler ourScriptQuoteHandler;

    public HtmlQuoteHandler() {
      this(new XmlQuoteHandler());
    }

    public HtmlQuoteHandler(QuoteHandler _baseHandler) {
      myBaseQuoteHandler = _baseHandler;
    }

    public static void setStyleQuoteHandler(QuoteHandler quoteHandler) {
      ourStyleQuoteHandler = quoteHandler;
    }

    public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
      if (myBaseQuoteHandler.isClosingQuote(iterator, offset)) return true;

      if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.isClosingQuote(iterator, offset)) {
        return true;
      }

      if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.isClosingQuote(iterator, offset)) {
        return true;
      }
      return false;
    }

    public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
      if (myBaseQuoteHandler.isOpeningQuote(iterator, offset)) return true;

      if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.isOpeningQuote(iterator, offset)) {
        return true;
      }

      if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.isOpeningQuote(iterator, offset)) {
        return true;
      }

      return false;
    }

    public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
      if (myBaseQuoteHandler.hasNonClosedLiteral(editor,iterator, offset)) return true;

      if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.hasNonClosedLiteral(editor,iterator, offset)) {
        return true;
      }

      if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.hasNonClosedLiteral(editor,iterator, offset)) {
        return true;
      }

      return false;
    }

    public boolean isInsideLiteral(HighlighterIterator iterator) {
      if (myBaseQuoteHandler.isInsideLiteral(iterator)) return true;

      if(ourStyleQuoteHandler!=null && ourStyleQuoteHandler.isInsideLiteral(iterator)) {
        return true;
      }

      if(ourScriptQuoteHandler!=null && ourScriptQuoteHandler.isInsideLiteral(iterator)) {
        return true;
      }

      return false;
    }

    public static void setScriptQuoteHandler(QuoteHandler scriptQuoteHandler) {
      ourScriptQuoteHandler = scriptQuoteHandler;
    }
  }

  static class XmlQuoteHandler implements QuoteHandler {
    public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
      return iterator.getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER;
    }

    public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
      return iterator.getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;
    }

    public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
      return true;
    }

    public boolean isInsideLiteral(HighlighterIterator iterator) {
      return false;
    }
  }

  static class JavaQuoteHandler implements QuoteHandler {
    public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
      final IElementType tokenType = iterator.getTokenType();

      if (tokenType == JavaTokenType.STRING_LITERAL ||
          tokenType == JavaTokenType.CHARACTER_LITERAL){
        int start = iterator.getStart();
        int end = iterator.getEnd();
        return end - start >= 1 && offset == end - 1;
      }
      return false;
    }

    public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
      final IElementType tokenType = iterator.getTokenType();

      if (tokenType == JavaTokenType.STRING_LITERAL || tokenType == JavaTokenType.CHARACTER_LITERAL){
        int start = iterator.getStart();
        return offset == start;
      }
      return false;
    }

    public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
      try {
        Document doc = editor.getDocument();
        CharSequence chars = doc.getCharsSequence();
        int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));

        while (!iterator.atEnd() && iterator.getStart() < lineEnd) {
          IElementType tokenType = iterator.getTokenType();
          if (tokenType == JavaTokenType.STRING_LITERAL || tokenType == JavaTokenType.CHARACTER_LITERAL) {
            if (iterator.getStart() >= iterator.getEnd() - 1 ||
                chars.charAt(iterator.getEnd() - 1) != '\"' && chars.charAt(iterator.getEnd() - 1) != '\'') {
              return true;
            }
          }
          iterator.advance();
        }
        return false;
      }
      finally {
        while(iterator.getStart() != offset) iterator.retreat();
      }
    }

    public boolean isInsideLiteral(HighlighterIterator iterator) {
      IElementType tokenType = iterator.getTokenType();
      return tokenType == JavaTokenType.STRING_LITERAL || tokenType == JavaTokenType.CHARACTER_LITERAL;
    }
  }

  public TypedHandler(TypedActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, char charTyped, DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null || editor.isColumnMode()){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, charTyped, dataContext);
      }
      return;
    }

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    if (file == null){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, charTyped, dataContext);
      }
      return;
    }

    if (editor.isViewer()) return;

    if (!editor.getDocument().isWritable()) {
      editor.getDocument().fireReadOnlyModificationAttempt();
      return;
    }

    AutoPopupController autoPopupController = AutoPopupController.getInstance(project);

    if (charTyped == '.') {
      autoPopupController.autoPopupMemberLookup(editor);
    }

    if (charTyped == '#') {
      autoPopupController.autoPopupMemberLookup(editor);
    }

    if (charTyped == '@' && file instanceof PsiJavaFile) {
      autoPopupController.autoPopupJavadocLookup(editor);
    }

    if (charTyped == '<' && file instanceof XmlFile) {
      autoPopupController.autoPopupXmlLookup(editor);
    }

    if (charTyped == ' ' && file instanceof XmlFile) {
      autoPopupController.autoPopupXmlLookup(editor);
    }

    if (charTyped == '('){
      autoPopupController.autoPopupParameterInfo(editor, null);
    }

    if (!editor.isInsertMode()){
      myOriginalHandler.execute(editor, charTyped, dataContext);
      return;
    }

    if (editor.getSelectionModel().hasSelection()){
      EditorModificationUtil.deleteSelectedText(editor);
    }

    final VirtualFile virtualFile = file.getVirtualFile();
    FileType fileType;
    FileType originalFileType = null;

    if (virtualFile != null){
      originalFileType = fileType = FileTypeManager.getInstance().getFileTypeByFile(virtualFile);
      if (fileType == StdFileTypes.ASPECT) fileType = StdFileTypes.JAVA;
    }
    else{
      fileType = file instanceof PsiCodeFragment ? StdFileTypes.JAVA : null;
    }

    if ('>' == charTyped){
      if (StdFileTypes.XML == fileType || StdFileTypes.HTML == fileType || StdFileTypes.XHTML == fileType ||
          StdFileTypes.JSPX == fileType){
        handleXmlGreater(project, editor, fileType);
      } else if (originalFileType == StdFileTypes.JSP) {
        handleJspGreater(project, editor);
      }
    }
    else if (')' == charTyped){
      if (handleRParen(editor, fileType, ')', '(')) return;
    }
    else if (']' == charTyped){
      if (handleRParen(editor, fileType, ']', '[')) return;
    }
    else if (';' == charTyped) {
      if (handleSemicolon(editor, fileType)) return;
    }
    else if ('"' == charTyped || '\'' == charTyped){
      if (handleQuote(editor, fileType, charTyped, dataContext)) return;
    }

    myOriginalHandler.execute(editor, charTyped, dataContext);

    if ('(' == charTyped && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET){
      handleAfterLParen(editor, fileType, '(');
    }
    else if ('[' == charTyped && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET){
      handleAfterLParen(editor, fileType, '[');
    }
    else if ('}' == charTyped){
      indentClosingBrace(project, editor);
    }
    else if ('{' == charTyped){
      indentOpenedBrace(project, editor);
    }
    else if ('/' == charTyped){
      if (file instanceof XmlFile){
        handleXmlSlash(project, editor);
      } else if (originalFileType == StdFileTypes.JSP) {
        handleJspSlash(project, editor);
      }
    } else if ('=' == charTyped) {
      if (originalFileType == StdFileTypes.JSP) {
        handleJspEqual(project, editor);
      }
    }
  }

  private void handleJspEqual(Project project, Editor editor) {
    final CharSequence chars = editor.getDocument().getCharsSequence();
    int current = editor.getCaretModel().getOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    JspFile file = (JspFile)PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    PsiElement element = file.findElementAt(current);
    if (element == null) {
      element = file.findElementAt(editor.getDocument().getTextLength() - 1);
      if (element == null) return;
    }
    if (current >= 3 && chars.charAt(current-3) == '<' && chars.charAt(current-2)=='%')  {
      while (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }

      JspExpression expression = PsiTreeUtil.getParentOfType(element,JspExpression.class);

      if (expression!=null) {
        int ptr = current;
        while(ptr < chars.length() && Character.isWhitespace(chars.charAt(ptr))) ++ptr;

        if (ptr + 1 < chars.length() && (chars.charAt(ptr)=='%' && chars.charAt(ptr+1)=='>') ) {
          // we already have %>
        } else {
          editor.getDocument().insertString(current,"%>");
        }
      }
    }
  }

  private boolean handleSemicolon(Editor editor, FileType fileType) {
    if (fileType != StdFileTypes.JAVA) return false;
    int offset = editor.getCaretModel().getOffset();
    if (offset == editor.getDocument().getTextLength()) return false;

    char charAt = editor.getDocument().getCharsSequence().charAt(offset);
    if (charAt != ';') return false;

    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  private void handleXmlSlash(Project project, Editor editor){
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    XmlFile file = (XmlFile)PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);

    if (!(element instanceof PsiWhiteSpace)) return;
    while(element instanceof PsiWhiteSpace) element = element.getPrevSibling();

    if (!(element instanceof XmlToken)) return;
    XmlToken token = (XmlToken)element;
    if (!"/".equals(token.getText())) return;

    PsiElement prevSibling = element.getPrevSibling();
    if (prevSibling!=null) {
      element = prevSibling;
    } else {
      PsiElement parent = element.getParent();
      if(parent instanceof XmlText) {
        element = parent.getPrevSibling();
      }
    }

    while(element instanceof PsiWhiteSpace) element = element.getPrevSibling();
    if (!(element instanceof XmlTag)) return;

    XmlTag tag = (XmlTag)element;
    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) != null) return;
    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) != null) return;

    EditorModificationUtil.insertStringAtCaret(editor, ">");
  }

  private void handleJspGreater(Project project, Editor editor){
    final JspAction action = findClosingJspAction(project, editor);

    if (action == null || action.getName().length()==0) {
      handleStartOfHtmlTag(project,editor);
      return;
    }

    if (validateWellformedJspAction(editor,action)) {
      editor.getDocument().insertString(editor.getCaretModel().getOffset(), "</" + action.getQualifiedName() + ">");
    }
  }

  private boolean validateWellformedJspAction(Editor editor,JspAction tag) {
    JspToken actionEnd = JspUtil.getTokenOfType(tag, JspTokenType.JSP_EMPTY_ACTION_END);
    if (actionEnd != null) return false;

    int tagOffset = tag.getTextRange().getStartOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(tagOffset);
    if (BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), StdFileTypes.JSP, iterator, true)) {
      final PsiElement[] elements = tag.getChildren();

      if (elements.length > 2 &&
          elements[0] instanceof JspToken &&
          ((JspToken)elements[0]).getTokenType() == JspTokenType.JSP_ACTION_START &&
          elements[1] instanceof JspToken &&
          ((JspToken)elements[1]).getTokenType() == JspTokenType.JSP_ACTION_NAME
         ) {
        int nextPtr = 2;
        if (elements.length > nextPtr &&
            elements[nextPtr] instanceof JspToken &&
            ((JspToken)elements[nextPtr]).getTokenType() == JspTokenType.JSP_ACTION_WHITE_SPACE
            ) {
          nextPtr++;
        }

        while(elements.length > nextPtr &&
              elements[nextPtr] instanceof JspAttribute
             ) {
          nextPtr++;
          if (elements.length > nextPtr &&
            elements[nextPtr] instanceof JspToken &&
            ((JspToken)elements[nextPtr]).getTokenType() == JspTokenType.JSP_ACTION_WHITE_SPACE
            ) {
            nextPtr++;
          }
        }

        if (nextPtr < elements.length && elements[nextPtr].getText().equals("<")) {
          editor.getDocument().insertString(editor.getCaretModel().getOffset(), "</" + elements[0].getText().substring(1) + elements[1].getText() + ">");
        }
      }
      // <c:out <c:out> </c:out>
      return false;
    }

    // check for incomplete local tag <a:aa <caret> </a:aa>
    // this would mean bad_char (<) bad_char (/) attribute_name (a:aa) token sequence
    PsiElement element = tag.findElementAt(editor.getCaretModel().getOffset()-tag.getStartOffsetInParent());

    if (element!=null) {
      element = element.getNextSibling();
      if (element instanceof JspToken &&
        ((JspToken)element).getTokenType() == JspTokenType.JSP_BAD_CHARACTER &&
        element.getText().equals("<")
        ) {
      element = element.getNextSibling();

      if (element instanceof JspToken &&
          ((JspToken)element).getTokenType() == JspTokenType.JSP_BAD_CHARACTER &&
          element.getText().equals("/")
         ) {
        element = element.getNextSibling();

        if (element instanceof JspToken &&
            ((JspToken)element).getTokenType() == JspTokenType.JSP_ACTION_ATTRIBUTE_NAME &&
             element.getText().equals(tag.getQualifiedName())
            ) {
          return false;
        }
      }
      }
    }

    return true;
  }

  private void handleStartOfHtmlTag(Project project, Editor editor) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    JspFile file = (JspFile)PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);

    if (element == null) {
      element = file.findElementAt(editor.getDocument().getTextLength() - 1);
      if (element == null) return;
    }
    if (element instanceof JspToken &&
        ((JspToken)element).getTokenType() == JspTokenType.JSP_TEMPLATE_DATA
        ) {
      int offsetWithinParent = offset - element.getParent().getTextOffset() - element.getStartOffsetInParent();
      int index=offsetWithinParent-1;
      String text = element.getText();

      index = JspParsingUtil.eatAttributes(index, text);
      index = JspParsingUtil.eatWhiteSpace(index, text);
      int tagEnd = index+1;

      index = JspParsingUtil.eatTagName(index, text);

      if (index>=0 && text.charAt(index)=='<') {
        String tagName = text.substring(index+1,tagEnd);
        if (tagName.length()==0) return;

        // check if the nearest is not the end of that tag
        int i;
        for(i=offsetWithinParent;i < text.length() && Character.isWhitespace(text.charAt(i));++i);
        final String toInsert = "</"+tagName+">";
        if (!text.startsWith(toInsert,i)) {

          final XmlNSDescriptor descriptor = file.getManager().getJspElementFactory().getXHTMLDescriptor();
          if (descriptor!=null) {
            try{
              final XmlTag tagFromText = file.getManager().getElementFactory().createTagFromText("<"+ tagName + " xmlns=\"" + XmlUtil.XHTML_URI + "\"/>");
              if(tagFromText != null){
                final XmlElementDescriptor elementDescriptor = descriptor.getElementDescriptor(tagFromText);

                if (elementDescriptor!=null && elementDescriptor.getContentType() != XmlElementDescriptor.CONTENT_TYPE_EMPTY) {
                  editor.getDocument().insertString(offset, toInsert);
                }
              }
            }
            catch(IncorrectOperationException ioe){}
          }
        }
      }
    }
  }

  private JspAction findClosingJspAction(Project project, Editor editor) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    JspFile file = (JspFile)PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);

    if (element == null) {
      element = file.findElementAt(editor.getDocument().getTextLength() - 1);
      if (element == null) return null;
    } else {
      if (!(element instanceof JspToken) ||
        ((JspToken)element).getTokenType()!=JspTokenType.JSP_ACTION_WHITE_SPACE) return null;
    }

    while( ( (element instanceof JspToken) &&
             ((JspToken)element).getTokenType()==JspTokenType.JSP_ACTION_WHITE_SPACE
           ) ||
           element instanceof JspAttribute
          ) {
      element = element.getPrevSibling();
    }

    if ( element instanceof JspToken) {
      if (((JspToken)element).getTokenType()==JspTokenType.JSP_ACTION_NAME &&
          ((JspToken)element.getPrevSibling()).getTokenType() == JspTokenType.JSP_ACTION_END_TAG_START
          ) {
        return null;
      }
      element = element.getParent();
      if (element instanceof JspAttribute) {
        element = element.getParent();
      }
    }

    if (!(element instanceof JspAction)) return null;
    return (JspAction)element;
  }

  private void handleJspSlash(Project project, Editor editor){
    JspAction action = findClosingJspAction(project,editor);

    if (action==null) return;

    if (validateWellformedJspAction(editor,action)) {
      EditorModificationUtil.insertStringAtCaret(editor, ">");
    }
  }

  private void handleXmlGreater(Project project, Editor editor, FileType fileType){
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    XmlFile file = (XmlFile)PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    final int offset = editor.getCaretModel().getOffset();

    PsiElement element;

    if (offset < editor.getDocument().getTextLength()) {
      element = file.findElementAt(offset);
      if (!(element instanceof PsiWhiteSpace)) return;
      PsiElement parent = element.getParent();
      if (parent instanceof XmlText) {
        final String text = parent.getText();
        // check /
        final int index = offset - parent.getTextOffset() - 1;

        if (index >= 0 && text.charAt(index)=='/') {
          return; // already seen /
        }
        element = parent.getPrevSibling();
      } else if (parent instanceof XmlTag && !(element.getPrevSibling() instanceof XmlTag)) {
        element = parent;
      }
    }
    else {
      element = file.findElementAt(editor.getDocument().getTextLength() - 1);
      if (element == null) return;
      element = element.getParent();
    }

    while(element instanceof PsiWhiteSpace) element = element.getPrevSibling();
    if (element == null) return;
    if (!(element instanceof XmlTag)) {
      if (element instanceof XmlTokenImpl &&
          element.getPrevSibling() !=null &&
          element.getPrevSibling().getText().equals("<")
         ) {
        // tag is started and there is another text in the end
        editor.getDocument().insertString(offset, "</" + element.getText() + ">");
      }
      return;
    }

    XmlTag tag = (XmlTag)element;
    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) != null) return;
    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) != null) return;

    final String name = tag.getName();
    if (tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(name)) return;
    if ("".equals(name)) return;

    int tagOffset = tag.getTextRange().getStartOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(tagOffset);
    if (BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), fileType, iterator, true)) return;

    editor.getDocument().insertString(offset, "</" + name + ">");
  }

  private void handleAfterLParen(Editor editor, FileType fileType, char lparenChar){
    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);

    iterator.retreat();
    BraceMatchingUtil.BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
    IElementType braceTokenType = braceMatcher.getTokenType(lparenChar, iterator);
    if (iterator.atEnd() || iterator.getTokenType() != braceTokenType) return;
    iterator.advance();

    IElementType tokenType = !iterator.atEnd() ? iterator.getTokenType() : null;
    if (tokenType instanceof IJavaElementType) {
      if (!TokenType.WHITE_SPACE_OR_COMMENT_BIT_SET.isInSet(tokenType)
          && tokenType != JavaTokenType.SEMICOLON
          && tokenType != JavaTokenType.COMMA
          && tokenType != JavaTokenType.RPARENTH
          && tokenType != JavaTokenType.RBRACKET
          && tokenType != JavaTokenType.RBRACE
      ) {
        return;
      }
    }

    iterator.retreat();

    int lparenOffset = BraceMatchingUtil.findLeftmostLParen(iterator, braceTokenType, editor.getDocument().getCharsSequence(),fileType);
    if (lparenOffset < 0) lparenOffset = 0;

    iterator = ((EditorEx)editor).getHighlighter().createIterator(lparenOffset);
    boolean matched = BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), fileType, iterator, true);

    if (!matched){
      String text;
      if (lparenChar == '(') {
        text = ")";
      }
      else if (lparenChar == '[') {
        text = "]";
      }
      else {
        LOG.assertTrue(false);

        return;
      }
      editor.getDocument().insertString(offset, text);
    }
  }

  private boolean handleRParen(Editor editor, FileType fileType, char rightParen, char leftParen){
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();

    if (offset == editor.getDocument().getTextLength()) return false;

    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    BraceMatchingUtil.BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
    if (iterator.atEnd() || braceMatcher.getTokenType(rightParen,iterator) != iterator.getTokenType()) {
      return false;
    }

    iterator.retreat();

    int lparenthOffset = BraceMatchingUtil.findLeftmostLParen(iterator, braceMatcher.getTokenType(leftParen, iterator),  editor.getDocument().getCharsSequence(),fileType);
    if (lparenthOffset < 0) return false;

    iterator = ((EditorEx) editor).getHighlighter().createIterator(lparenthOffset);
    boolean matched = BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), fileType, iterator, true);

    if (!matched) return false;

    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  private boolean handleQuote(Editor editor, FileType fileType, char quote, DataContext dataContext) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) return false;
    final QuoteHandler quoteHandler = quoteHandlers.get(fileType);
    if (quoteHandler == null) return false;

    int offset = editor.getCaretModel().getOffset();
    if (offset == editor.getDocument().getTextLength()) return false;

    CharSequence chars = editor.getDocument().getCharsSequence();
    int length = editor.getDocument().getTextLength();
    if (isTypingEscapeQuote(editor, quoteHandler, offset)) return false;

    if (offset < length && chars.charAt(offset) == quote){
      if (isClosingQuote(editor, quoteHandler, offset)){
        editor.getCaretModel().moveToOffset(offset + 1);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        return true;
      }
    }

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);

    if (!iterator.atEnd()){
      IElementType tokenType = iterator.getTokenType();
      if (fileType == StdFileTypes.JAVA || fileType == StdFileTypes.JSP){
        if (tokenType instanceof IJavaElementType){
          if (!TokenType.WHITE_SPACE_OR_COMMENT_BIT_SET.isInSet(tokenType)
              && tokenType != JavaTokenType.SEMICOLON
              && tokenType != JavaTokenType.COMMA
              && tokenType != JavaTokenType.RPARENTH
              && tokenType != JavaTokenType.RBRACKET
              && tokenType != JavaTokenType.RBRACE
              && tokenType != JavaTokenType.STRING_LITERAL
              && tokenType != JavaTokenType.CHARACTER_LITERAL
          ) {
            return false;
          }
        }
      }
    }

    myOriginalHandler.execute(editor, quote, dataContext);
    offset = editor.getCaretModel().getOffset();

    if (isOpeningQuote(editor, quoteHandler, offset - 1) &&
        hasNonClosedLiterals(editor, quoteHandler, offset - 1)) {
      editor.getDocument().insertString(offset, "" + quote);
    }

    return true;
  }

  private boolean isClosingQuote(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isClosingQuote(iterator,offset);
  }

  private boolean isOpeningQuote(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isOpeningQuote(iterator, offset);
  }

  private boolean hasNonClosedLiterals(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) {
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.hasNonClosedLiteral(editor, iterator, offset);
  }

  private boolean isTypingEscapeQuote(Editor editor, QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftBackward(chars, offset - 1, "\\");
    int slashCount = (offset - 1) - offset1;
    if ((slashCount % 2) == 0) return false;
    return isInsideLiteral(editor, quoteHandler, offset);
  }

  private boolean isInsideLiteral(Editor editor, QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset - 1);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isInsideLiteral(iterator);
  }

  private void indentClosingBrace(final Project project, final Editor editor){
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (!settings.AUTOINDENT_CLOSING_BRACE) return;

    final int offset = editor.getCaretModel().getOffset() - 1;
    final Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    if (offset < 0 || chars.charAt(offset) != '}') return;
    int spaceStart = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
    if (spaceStart < 0 || chars.charAt(spaceStart) == '\n' || chars.charAt(spaceStart) == '\r'){
      PsiDocumentManager.getInstance(project).commitDocument(document);

      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null || !file.isWritable()) return;
      PsiElement element = file.findElementAt(offset);
      if (element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE){
        final Runnable action = new Runnable() {
          public void run(){
            try{
              int newOffset = CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
              editor.getCaretModel().moveToOffset(newOffset + 1);
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
              editor.getSelectionModel().removeSelection();
            }
            catch(IncorrectOperationException e){
              LOG.error(e);
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }
  }

  private void indentOpenedBrace(final Project project, final Editor editor){
    final int offset = editor.getCaretModel().getOffset() - 1;
    final Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    if (offset < 0 || chars.charAt(offset) != '{') return;

    int spaceStart = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
    if (spaceStart < 0 || chars.charAt(spaceStart) == '\n' || chars.charAt(spaceStart) == '\r'){
      PsiDocumentManager.getInstance(project).commitDocument(document);

      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null || !file.isWritable()) return;
      PsiElement element = file.findElementAt(offset);
      if (element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.LBRACE){
        final Runnable action = new Runnable() {
          public void run(){
            try{
              int newOffset = CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
              editor.getCaretModel().moveToOffset(newOffset + 1);
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
              editor.getSelectionModel().removeSelection();
            }
            catch(IncorrectOperationException e){
              LOG.error(e);
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }
  }
}

