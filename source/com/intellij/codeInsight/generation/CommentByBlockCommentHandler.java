package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspToken;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.text.CharArrayUtil;

import java.util.HashMap;
import java.util.Map;

public class CommentByBlockCommentHandler implements CodeInsightActionHandler, BlockCommenter.BlockCommenterContext {
  private Project myProject;
  private Editor myEditor;
  private PsiFile myFile;
  private Document myDocument;

  private static final Map<FileType, BlockCommenter> customCommenters = new HashMap<FileType, BlockCommenter>(4);

  public static final void registerCommenter(FileType fileType, BlockCommenter blockCommenter) {
    customCommenters.put(fileType, blockCommenter);
  }

  public static BlockCommenter getCustomCommenter(FileType fileType) {
    return customCommenters.get(fileType);
  }

  public static BlockCommenter getCommenter(PsiFile file) {
    BlockCommenter blockCommenter = customCommenters.get(file.getFileType());
    if (blockCommenter!=null) return blockCommenter;

    if (isJavaFile(file)) {
      return new JavaBlockCommenter();
    } else if (file instanceof XmlFile) {
      return new XmlBlockCommenter();
    } else if (file instanceof JspFile) {
      return new JspBlockCommenter();
    }

    return null;
  }

  static {
    registerCommenter(StdFileTypes.JAVA,new JavaBlockCommenter());
    registerCommenter(StdFileTypes.JSP,new JspBlockCommenter());
    registerCommenter(StdFileTypes.XML,new XmlBlockCommenter());
    HtmlBlockCommenter blockCommenter = new HtmlBlockCommenter();
    registerCommenter(StdFileTypes.HTML,blockCommenter);
    registerCommenter(StdFileTypes.XHTML,blockCommenter);
  }

  static class JavaBlockCommenter implements BlockCommenter {
    public void commentRange(int start, int end, BlockCommenterContext context) {
      context.commentRange(start,end,"/*","*/");
    }

    public void uncommentRange(PsiElement element, int commentStart, BlockCommenterContext context) {
      if (element instanceof PsiComment && element.getText().startsWith("/*")) {
        context.uncommentRange(element.getTextRange(),"/*", "*/");
      } else {
        context.insertEmptyComment(commentStart, "/*", "*/");
      }
    }

    public Object clone() {
      try {
        return super.clone();
      }
      catch (CloneNotSupportedException e) {
        return null;
      }
    }
  }

  static class XmlBlockCommenter implements BlockCommenter {
    public void commentRange(int start, int end, BlockCommenterContext context) {
      context.commentRange(start, end, "<!--", "-->");
    }

    public void uncommentRange(PsiElement element, int commentStart, BlockCommenterContext context) {
      if (element.getParent() instanceof XmlComment) {
        context.uncommentRange(element.getParent().getTextRange(), "<!--", "-->");
      } else {
        context.insertEmptyComment(commentStart, "<!--", "-->");
      }
    }

    public Object clone() {
      try {
        return super.clone();
      }
      catch (CloneNotSupportedException e) {
        return null;
      }
    }
  }

  public static class HtmlBlockCommenter implements BlockCommenter {
    private static BlockCommenter ourStyleCommenter;
    private boolean myInitialized;
    private BlockCommenter myCommenterToUse;
    private static BlockCommenter ourScriptCommenter;

    private void initialize(PsiElement element, int start, int end, BlockCommenterContext context) {
      if (!myInitialized) {
        myInitialized = true;

        if (element==null) {
          element = PsiUtil.getElementInclusiveRange(
            context.getFile(),
            new TextRange(start,end)
          );
        }

        if (element!=null) {
          element = PsiTreeUtil.getParentOfType(element, XmlTag.class,false);

          if (element instanceof XmlTag) {
            final String name = ((XmlTag)element).getName();

            if (name.equalsIgnoreCase("style") &&
              ourStyleCommenter!=null
              ) {
              myCommenterToUse = (BlockCommenter)ourStyleCommenter.clone();
            } else if (name.equalsIgnoreCase("script") && ourScriptCommenter!=null) {
              myCommenterToUse = (BlockCommenter)ourScriptCommenter.clone();
            }
          }
        }

        if (myCommenterToUse==null) {
          myCommenterToUse = new XmlBlockCommenter();
        }
      }
    }

    public static final void setStyleCommenter(BlockCommenter _styleCommenter) {
      ourStyleCommenter = _styleCommenter;
    }

    public void commentRange(int start, int end, BlockCommenterContext context) {
      initialize(null, start, end, context);
      myCommenterToUse.commentRange(start, end, context);
    }

    public void uncommentRange(PsiElement element, int commentStart, BlockCommenterContext context) {
      initialize(element, -1, -1, context);
      myCommenterToUse.uncommentRange(element, commentStart, context);
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

    public static void setScriptCommenter(BlockCommenter scriptCommenter) {
      ourScriptCommenter = scriptCommenter;
    }
  }

  static class JspBlockCommenter implements BlockCommenter {

    public void commentRange(int start, int end, BlockCommenterContext context) {
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
      PsiElement element = context.getFile().findElementAt(start);

      if (isJavaCommentInsideJsp(element, end)) {
        context.commentRange(start, end, "/*", "*/");
      } else {
        context.commentRange(start, end, "<%--", "--%>");
      }
    }

    public void uncommentRange(PsiElement element, int commentStart, BlockCommenterContext context) {
      if (element instanceof JspToken && ((JspToken) element).getTokenType() == JspTokenType.JSP_COMMENT) {
        context.uncommentRange(element.getTextRange(), "<%--", "--%>");
      } else {
        if (element instanceof PsiComment && element.getText().startsWith("/*")) {
          context.uncommentRange(element.getTextRange(), "/*", "*/");
        } else {
          if (!isJavaCommentInsideJsp(element, commentStart)) {
            context.insertEmptyComment(commentStart, "%--", "--%");
          } else {
            context.insertEmptyComment(commentStart, "/*", "*/");
          }
        }
      }
    }

    public Object clone() {
      try {
        return super.clone();
      }
      catch (CloneNotSupportedException e) {
        return null;
      }
    }

    private boolean isJavaCommentInsideJsp(PsiElement element, int endOffset) {
      boolean javaComment = !(element instanceof JspToken);

      while (element != null && element.getTextRange().getEndOffset() <= endOffset) {
        if (element instanceof JspToken) {
          javaComment = false;
          break;
        }
        element = element.getNextSibling();
      }
      return javaComment;
    }
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    myProject = project;
    myEditor = editor;
    myFile = file;

    myDocument = editor.getDocument();

    if (!myFile.isWritable()) {
      myDocument.fireReadOnlyModificationAttempt();
      return;
    }
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.comment.block");
    final SelectionModel selectionModel = myEditor.getSelectionModel();
    BlockCommenter commenter = getCommenter(myFile);
    if (commenter == null) return;
    final BlockCommenter blockCommenter = (BlockCommenter)commenter.clone();

    if (selectionModel.hasSelection()) {
      int startOffset = selectionModel.getSelectionStart();
      int endOffset = selectionModel.getSelectionEnd();

      blockCommenter.commentRange(startOffset, endOffset, this);
    } else {
      int offset = editor.getCaretModel().getOffset();
      PsiDocumentManager.getInstance(project).commitDocument(myDocument);
      PsiElement element = file.findElementAt(offset);
      if (element == null && offset > 0) {
        element = file.findElementAt(offset - 1);
      }
      if (element == null) return;
      int commentStart = (element instanceof PsiWhiteSpace) ? offset : element.getTextRange().getStartOffset();
      blockCommenter.uncommentRange(element, commentStart, this);
    }
  }

  private static boolean isJavaFile(PsiFile file) {
    return file instanceof PsiJavaFile || file instanceof PsiCodeFragment;
  }

  public boolean startInWriteAction() {
    return true;
  }

  public void insertEmptyComment(int offset, String commentStart, String commentEnd) {
    myEditor.getDocument().insertString(offset, commentStart + commentEnd);
    myEditor.getCaretModel().moveToOffset(offset + commentStart.length());
  }


  public void commentRange(int startOffset, int endOffset, String commentPrefix, String commentSuffix) {
    CharSequence chars = myDocument.getCharsSequence();
    LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();

    if (startOffset == 0 || chars.charAt(startOffset - 1) == '\n' || chars.charAt(startOffset - 1) == '\r') {
      if (endOffset == myDocument.getTextLength() || chars.charAt(endOffset - 1) == '\n' || chars.charAt(endOffset - 1) == '\r') {
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
        CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myProject);
        String space;
        if (!settings.BLOCK_COMMENT_AT_FIRST_COLUMN) {
          final FileType fileType = myFile.getFileType();
          int line1 = myEditor.offsetToLogicalPosition(startOffset).line;
          int line2 = myEditor.offsetToLogicalPosition(endOffset - 1).line;
          Indent minIndent = CodeInsightUtil.getMinLineIndent(myProject, myDocument, line1, line2, fileType);
          if (minIndent == null) {
            minIndent = codeStyleManager.zeroIndent();
          }
          space = codeStyleManager.fillIndent(minIndent, fileType);
        } else {
          space = "";
        }
        myDocument.insertString(endOffset, space + commentSuffix + "\n");
        myDocument.insertString(startOffset, space + commentPrefix + "\n");
        myEditor.getSelectionModel().removeSelection();
        LogicalPosition pos = new LogicalPosition(caretPosition.line + 1, caretPosition.column);
        myEditor.getCaretModel().moveToLogicalPosition(pos);
        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        return;
      }
    }

    myDocument.insertString(endOffset, commentSuffix);
    myDocument.insertString(startOffset, commentPrefix);
    myEditor.getSelectionModel().removeSelection();
    LogicalPosition pos = new LogicalPosition(caretPosition.line, caretPosition.column + commentPrefix.length());
    myEditor.getCaretModel().moveToLogicalPosition(pos);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  public PsiFile getFile() {
    return myFile;
  }

  public Document getDocument() {
    return myDocument;
  }

  public Project getProject() {
    return myProject;
  }

  public Editor getEditor() {
    return myEditor;
  }

  public void uncommentRange(TextRange range, String commentPrefix, String commentSuffix) {
    CharSequence chars = myDocument.getCharsSequence();
    int startOffset = range.getStartOffset();
    boolean endsProperly = CharArrayUtil.regionMatches(chars, range.getEndOffset() - commentSuffix.length(), commentSuffix);

    int delOffset1 = startOffset;
    int delOffset2 = startOffset + commentPrefix.length();
    int offset1 = CharArrayUtil.shiftBackward(chars, delOffset1 - 1, " \t");
    if (offset1 < 0 || chars.charAt(offset1) == '\n' || chars.charAt(offset1) == '\r') {
      int offset2 = CharArrayUtil.shiftForward(chars, delOffset2, " \t");
      if (offset2 == myDocument.getTextLength() || chars.charAt(offset2) == '\r' || chars.charAt(offset2) == '\n') {
        delOffset1 = offset1 + 1;
        if (offset2 < myDocument.getTextLength()) {
          delOffset2 = offset2 + 1;
          if (chars.charAt(offset2) == '\r' && offset2 + 1 < myDocument.getTextLength() && chars.charAt(offset2 + 1) == '\n') {
            delOffset2++;
          }
        }
      }
    }

    myDocument.deleteString(delOffset1, delOffset2);
    chars = myDocument.getCharsSequence();

    if (endsProperly) {
      int shift = delOffset2 - delOffset1;
      int delOffset3 = range.getEndOffset() - shift - commentSuffix.length();
      int delOffset4 = range.getEndOffset() - shift;
      int offset3 = CharArrayUtil.shiftBackward(chars, delOffset3 - 1, " \t");
      if (offset3 < 0 || chars.charAt(offset3) == '\n' || chars.charAt(offset3) == '\r') {
        int offset4 = CharArrayUtil.shiftForward(chars, delOffset4, " \t");
        if (offset4 == myDocument.getTextLength() || chars.charAt(offset4) == '\r' || chars.charAt(offset4) == '\n') {
          delOffset3 = offset3 + 1;
          if (offset4 < myDocument.getTextLength()) {
            delOffset4 = offset4 + 1;
            if (chars.charAt(offset4) == '\r' && offset4 + 1 < myDocument.getTextLength() && chars.charAt(offset4 + 1) == '\n') {
              delOffset4++;
            }
          }
        }
      }
      myDocument.deleteString(delOffset3, delOffset4);
    }
  }
}