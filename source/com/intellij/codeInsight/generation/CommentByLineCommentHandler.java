package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.FoldingModelImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.text.CharArrayUtil;

import java.util.HashMap;
import java.util.Map;

public class CommentByLineCommentHandler implements CodeInsightActionHandler, LineCommenter.LineCommenterContext {
  private Project myProject;
  private PsiFile myFile;
  private Editor myEditor;
  private Document myDocument;
  private int myStartOffset;
  private int myEndOffset;
  private int myLine1;
  private int myLine2;
  private int[] myStartOffsets;
  private int[] myEndOffsets;
  private boolean myAllLineComments;
  //private CodeInsightSettings myCodeInsightSettings;
  private CodeStyleManager myCodeStyleManager;

  private static final Map<FileType,LineCommenter> customCommenters = new HashMap<FileType, LineCommenter>(4);

  public static final void registerCommenter(FileType fileType, LineCommenter commenter) {
    customCommenters.put(fileType, commenter);
  }

  static {
    registerCommenter(StdFileTypes.XML,new XmlLineCommenter());
    HtmlLineCommenter commenter = new HtmlLineCommenter();
    registerCommenter(StdFileTypes.HTML,commenter);
    registerCommenter(StdFileTypes.XHTML,commenter);
    registerCommenter(StdFileTypes.JAVA, new JavaLineCommenter());
    registerCommenter(StdFileTypes.JSP, new JspLineCommenter());
  }

  public static LineCommenter getCustomCommenter(FileType fileType) {
    return customCommenters.get(fileType);
  }

  public static LineCommenter getCommenter(PsiFile file) {
    LineCommenter customCommenter = customCommenters.get(file.getFileType());

    if (customCommenter!=null) {
      return customCommenter;
    } else if (isJavaFile(file)) {
      return new JavaLineCommenter();
    }
    else if (file instanceof XmlFile) {
      return new XmlLineCommenter();
    }
    else if (file instanceof JspFile) {
      return new JspLineCommenter();
    } else {
      return null;
    }
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    myProject = project;
    myFile = file;
    myEditor = editor;
    myDocument = myEditor.getDocument();

    if (!myFile.isWritable()) {
      myDocument.fireReadOnlyModificationAttempt();
      return;
    }

    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.comment.line");

    //myCodeInsightSettings = (CodeInsightSettings)ApplicationManager.getApplication().getComponent(CodeInsightSettings.class);
    myCodeStyleManager = CodeStyleManager.getInstance(myProject);

    final SelectionModel selectionModel = myEditor.getSelectionModel();

    boolean hasSelection = selectionModel.hasSelection();
    myStartOffset = selectionModel.getSelectionStart();
    myEndOffset = selectionModel.getSelectionEnd();

    if (myDocument.getTextLength() == 0) return;

    int lastLineEnd = myDocument.getLineEndOffset(myDocument.getLineNumber(myEndOffset));
    FoldRegion collapsedAt = ((FoldingModelImpl) myEditor.getFoldingModel()).getCollapsedRegionAtOffset(lastLineEnd);
    if (collapsedAt != null) {
      myEndOffset = Math.max(myEndOffset, collapsedAt.getEndOffset());
    }

    boolean wholeLinesSelected = !hasSelection ||
        (myStartOffset == myDocument.getLineStartOffset(myDocument.getLineNumber(myStartOffset)) &&
        myEndOffset == myDocument.getLineEndOffset(myDocument.getLineNumber(myEndOffset - 1)) + 1);

    doComment();

    if (!hasSelection) {
      editor.getCaretModel().moveCaretRelatively(0, 1, false, false, true);
    }
    else {
      if (wholeLinesSelected) {
        selectionModel.setSelection(myStartOffset, selectionModel.getSelectionEnd());
      }
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

  private void doComment() {
    myLine1 = myDocument.getLineNumber(myStartOffset);
    myLine2 = myDocument.getLineNumber(myEndOffset);

    if (myLine2 > myLine1 && myDocument.getLineStartOffset(myLine2) == myEndOffset) {
      myLine2--;
    }

    myStartOffsets = new int[myLine2 - myLine1 + 1];
    myEndOffsets = new int[myLine2 - myLine1 + 1];
    myAllLineComments = true;

    CharSequence chars = myDocument.getCharsSequence();
    LineCommenter lineCommenter = customCommenters.get(myFile.getFileType());
    if (lineCommenter==null) return;
    LineCommenter customCommenter = (LineCommenter)lineCommenter.clone();

    for (int line = myLine1; line <= myLine2; line++) {
      int offset1 = getCommentStart(line,customCommenter);
      myStartOffsets[line - myLine1] = offset1;
      if (offset1 == -1) {
        myAllLineComments = false;
        break;
      }

      int offset = myDocument.getLineEndOffset(line) + ((DocumentEx) myDocument).getLineSeparatorLength(line);
      offset = CharArrayUtil.shiftBackward(chars, offset - 1, "\n\r") + 1;

      int offset2 = customCommenter.getCommentEnd(offset,this);
      myEndOffsets[line - myLine1] = offset2;

      if (offset2 == -1) {
        myAllLineComments = false;
        break;
      }
    }

    if (!myAllLineComments) {
      new CommentPerformer(customCommenter).perform();
    }
    else {
      for (int line = myLine2; line >= myLine1; line--) {
        int offset1 = myStartOffsets[line - myLine1];
        int offset2 = myEndOffsets[line - myLine1];
        customCommenter.doUncomment(offset1,offset2,this);
      }
    }
  }

  private Indent computeMinIndent(int line1, int line2, CharSequence chars, CodeStyleManager codeStyleManager, FileType fileType, LineCommenter commenter) {
    Indent minIndent = CodeInsightUtil.getMinLineIndent(myProject, myDocument, line1, line2, fileType);
    if (line1 > 0) {
      int commentOffset = getCommentStart(line1 - 1,commenter);
      if (commentOffset >= 0) {
        int lineStart = myDocument.getLineStartOffset(line1 - 1);
        String space = chars.subSequence(lineStart, commentOffset).toString();
        Indent indent = codeStyleManager.getIndent(space, fileType);
        minIndent = minIndent != null ? indent.min(minIndent) : indent;
      }
    }
    if (minIndent == null) {
      minIndent = codeStyleManager.zeroIndent();
    }
    return minIndent;
  }


  private int getCommentStart(int line, LineCommenter commenter ) {
    int offset = myDocument.getLineStartOffset(line);
    CharSequence chars = myDocument.getCharsSequence();
    offset = CharArrayUtil.shiftForward(chars, offset, " \t");

    return commenter.getCommentStart(offset,this);
  }

  private static boolean isJavaFile(PsiFile file) {
    return file instanceof PsiJavaFile || file instanceof PsiCodeFragment;
  }

  public CharSequence getChars() {
    return myDocument.getCharsSequence();
  }

  public Document getDocument() {
    return myDocument;
  }

  public Project getProject() {
    return myProject;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public int getStartLine() {
    return myLine1;
  }

  public int getEndLine() {
    return myLine2;
  }

  private class CommentPerformer {
    LineCommenter myCommenter;

    public CommentPerformer(LineCommenter commenter) {
      myCommenter = commenter;
    }

    public void perform() {
      if (CodeStyleSettingsManager.getSettings(myProject).LINE_COMMENT_AT_FIRST_COLUMN) {
        new DefaultCommentPerformer(myCommenter).perform();
      }
      else {
        new IndentCommentPerformer(myCommenter).perform();
      }
    }
  }

  private class DefaultCommentPerformer {
    LineCommenter myCommenter;

    public DefaultCommentPerformer(LineCommenter commenter) {
      myCommenter = commenter;
    }

    public void perform() {
      for (int line = myLine2; line >= myLine1; line--) {
        int offset = myDocument.getLineStartOffset(line);
        myCommenter.doComment(offset, line, CommentByLineCommentHandler.this);
      }
    }
  }

  private class IndentCommentPerformer {
    LineCommenter myCommenter;

    public IndentCommentPerformer(LineCommenter commenter) {
      myCommenter = commenter;
    }

    public void perform() {
      CharSequence chars = myDocument.getCharsSequence();
      final FileType fileType = myFile.getFileType();
      Indent minIndent = computeMinIndent(myLine1, myLine2, chars, myCodeStyleManager, fileType,myCommenter);

      for (int line = myLine2; line >= myLine1; line--) {
        int lineStart = myDocument.getLineStartOffset(line);
        int offset = lineStart;
        StringBuffer buffer = new StringBuffer();
        while (true) {
          String space = buffer.toString();
          Indent indent = myCodeStyleManager.getIndent(space, fileType);
          if (indent.isGreaterThan(minIndent) || indent.equals(minIndent)) break;
          char c = chars.charAt(offset);
          if (c != ' ' && c != '\t') {
            String newSpace = myCodeStyleManager.fillIndent(minIndent, fileType);
            myDocument.replaceString(lineStart, offset, newSpace);
            offset = lineStart + newSpace.length();
            break;
          }
          buffer.append(c);
          offset++;
        }

        myCommenter.doComment(offset, line, CommentByLineCommentHandler.this);
      }
    }
  }

  public static class HtmlLineCommenter implements LineCommenter {
    private static LineCommenter ourStyleCommenter;
    private boolean myInitialized;
    private LineCommenter myCommenterToUse;
    private static LineCommenter ourScriptCommenter;

    private void initialize(LineCommenterContext context) {
      if (!myInitialized) {
        PsiElement elementInclusiveRange = PsiUtil.getElementInclusiveRange(
          context.getFile(),
          new TextRange(
            context.getDocument().getLineStartOffset(context.getStartLine()),
            context.getDocument().getLineEndOffset(context.getEndLine())
          )
        );
        elementInclusiveRange = PsiTreeUtil.getParentOfType(elementInclusiveRange,XmlTag.class,false);

        if (elementInclusiveRange instanceof XmlTag) {
          String tagName = ((XmlTag)elementInclusiveRange).getName();

          if (tagName.equalsIgnoreCase("style") &&
            ourStyleCommenter!=null
            ) {
            myCommenterToUse = (LineCommenter)ourStyleCommenter.clone();
          } else if (tagName.equalsIgnoreCase("script") &&
            ourScriptCommenter!=null
            ) {
            myCommenterToUse = (LineCommenter)ourScriptCommenter.clone();
          }
        }

        if (myCommenterToUse == null) {
          myCommenterToUse = new XmlLineCommenter();
        }
        myInitialized = true;
      }
    }

    public static final void setStyleCommenter(LineCommenter _styleCommenter) {
      ourStyleCommenter = _styleCommenter;
    }

    public void doComment(int offset, int line, LineCommenterContext context) {
      initialize(context);
      myCommenterToUse.doComment(offset, line, context);
    }

    public int getCommentEnd(int offset, LineCommenterContext context) {
      initialize(context);
      return myCommenterToUse.getCommentEnd(offset, context);
    }

    public int getCommentStart(int offset, LineCommenterContext context) {
      initialize(context);
      return myCommenterToUse.getCommentStart(offset, context);
    }

    public void doUncomment(int offset1, int offset2, LineCommenterContext context) {
      initialize(context);
      myCommenterToUse.doUncomment(offset1, offset2, context);
    }

    public Object clone() {
      try {
        return super.clone();
      }
      catch (CloneNotSupportedException e) {
        e.printStackTrace();
        return null;
      }
    }

    public static void setScriptCommenter(LineCommenter scriptCommenter) {
      ourScriptCommenter = scriptCommenter;
    }
  }

  private static class XmlLineCommenter implements LineCommenter {
    public void doComment(int offset, int line, LineCommenterContext context) {
      final Document myDocument = context.getDocument();
      myDocument.insertString(offset, "<!--");
      myDocument.insertString(myDocument.getLineEndOffset(line), "-->");
    }

    public int getCommentEnd(int offset, LineCommenterContext context) {
      offset -= "-->".length();
      if (offset < 0) return -1;

      if (!CharArrayUtil.regionMatches(context.getChars(), offset, "-->")) return -1;

      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
      PsiElement element = context.getFile().findElementAt(offset);
      if (element instanceof XmlToken && element.getTextRange().getStartOffset() == offset) {
        XmlToken token = (XmlToken)element;

        if (token.getTokenType() == XmlTokenType.XML_COMMENT_END) {

          return offset;
        }
      }

      return -1;
    }

    public int getCommentStart(int offset, LineCommenterContext context) {
      if (offset > context.getDocument().getTextLength() - "<!--".length()) return -1;
      if (!CharArrayUtil.regionMatches(context.getChars(), offset, "<!--")) return -1;

      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
      PsiElement element = context.getFile().findElementAt(offset);
      if (element instanceof XmlToken && element.getTextRange().getStartOffset() == offset) {
        XmlToken token = (XmlToken)element;

        if (token.getTokenType() == XmlTokenType.XML_COMMENT_START) {
          return offset;
        }
      }

      return offset;
    }

    public void doUncomment(int offset1, int offset2, LineCommenterContext context) {
      context.getDocument().deleteString(offset2, offset2 + 3);
      context.getDocument().deleteString(offset1, offset1 + 4);
    }

    public Object clone() {
      try {
        return super.clone();
      } catch(CloneNotSupportedException ex) {
        return null;
      }
    }
  }

  private static class JavaLineCommenter implements LineCommenter {
    public void doComment(int offset, int line, LineCommenterContext context) {
      context.getDocument().insertString(offset, "//");
    }

    public int getCommentEnd(int offset, LineCommenterContext context) {
      return offset;
    }

    public void doUncomment(int offset, int offset2, LineCommenterContext context) {
      context.getDocument().deleteString(offset, offset + 2);
    }

    public Object clone() {
      try {
        return super.clone();
      } catch(CloneNotSupportedException ex) {
        return null;
      }
    }

    public int getCommentStart(int offset, LineCommenterContext context) {
      if (offset > context.getDocument().getTextLength() - 2) return -1;
      if (!CharArrayUtil.regionMatches(context.getChars(), offset, "//")) return -1;

      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
      PsiElement element = context.getFile().findElementAt(offset);

      if (element instanceof PsiComment && element.getTextRange().getStartOffset() == offset) {
        return offset;
      }
      return -1;
    }
  }

  private static class JspLineCommenter implements LineCommenter {
    private boolean myJavaComment;
    private boolean initialized;

    public JspLineCommenter() {
    }

    public void doComment(int offset, int line, LineCommenterContext context) {
      final Document myDocument = context.getDocument();

      if (!initialized) {
        PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();

        myJavaComment = true;
        for (int line1 = context.getStartLine(); line1 <= context.getEndLine(); line1++) {
          int offset1 = myDocument.getLineStartOffset(line1);
          final PsiElement elementAt = context.getFile().findElementAt(offset1);
          if (elementAt instanceof JspToken) {
            myJavaComment = false;
          }
        }

        initialized = true;
      }

      if (myJavaComment) {
        myDocument.insertString(offset, "//");
      }
      else {
        myDocument.insertString(offset, "<%--");
        myDocument.insertString(myDocument.getLineEndOffset(line), "--%>");
      }
    }

    public void doUncomment(int offset1, int offset2, LineCommenterContext context) {
      final Document myDocument = context.getDocument();

      if (CharArrayUtil.regionMatches(myDocument.getCharsSequence(), offset1, "//")) {
        myDocument.deleteString(offset1, offset1 + "//".length());
      }
      else {
        myDocument.deleteString(offset2, offset2 + "--%>".length());
        myDocument.deleteString(offset1, offset1 + "<%--".length());
      }
    }

    public int getCommentStart(int offset, LineCommenterContext context) {
      final Document myDocument = context.getDocument();
      if (offset > myDocument.getTextLength() - "//".length()) return -1;

      if (CharArrayUtil.regionMatches(myDocument.getCharsSequence(), offset, "//")) {
        PsiDocumentManager.getInstance(context.getProject()).commitDocument(myDocument);
        PsiElement element = context.getFile().findElementAt(offset);
        if (element instanceof PsiComment && element.getTextRange().getStartOffset() == offset) {
          return offset;
        }
      }
      else {
        if (offset > myDocument.getTextLength() - "<%--".length()) return -1;
        if (CharArrayUtil.regionMatches(myDocument.getCharsSequence(), offset, "<%--")) return offset;
      }

      return -1;
    }

    public int getCommentEnd(int offset, LineCommenterContext context) {
      if (offset < 0) return -1;
      if (!CharArrayUtil.regionMatches(context.getChars(), offset - "--%>".length(), "--%>")) return offset;
      return offset - "--%>".length();
    }

    public Object clone() {
      try {
        return super.clone();
      } catch(CloneNotSupportedException ex) {
        return null;
      }
    }
  }
}