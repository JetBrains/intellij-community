package com.intellij.psi.impl.source.codeStyle.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
public class CommentFormatter {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.psi.impl.source.codeStyle.javadoc.CommentFormatter");

  private CodeStyleSettings mySettings;
  private JDParser myParser;
  private Project myProject;

  public CommentFormatter(Project project) {
    mySettings = CodeStyleSettingsManager.getSettings(project);
    myParser = new JDParser(mySettings);
    myProject = project;
  }

  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  public JDParser getParser() {
    return myParser;
  }

  public ASTNode formatComment(ASTNode child) {
    if (!getSettings().ENABLE_JAVADOC_FORMATTING) return child;
    PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(child);

    if (psiElement instanceof PsiDocComment && psiElement.getParent() instanceof PsiDocCommentOwner && psiElement == ((PsiDocCommentOwner)psiElement.getParent()).getDocComment()) {
      PsiDocCommentOwner parent = (PsiDocCommentOwner)psiElement.getParent();
      processElementComment(parent);
      return SourceTreeToPsiMap.psiElementToTree(parent.getDocComment());
    }

    return child;
  }

  public void process(ASTNode element) {
    if (!getSettings().ENABLE_JAVADOC_FORMATTING) return;

    PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
    processElementComment(psiElement);
  }

  private void processElementComment(PsiElement psiElement) {
    if (psiElement instanceof PsiClass) {
      String newCommentText = formatClassComment((PsiClass)psiElement);
      replaceDocComment(newCommentText, (PsiDocCommentOwner)psiElement);
    }
    else if (psiElement instanceof PsiMethod) {
      String newCommentText = formatMethodComment((PsiMethod)psiElement);
      replaceDocComment(newCommentText, (PsiDocCommentOwner)psiElement);
    }
    else if (psiElement instanceof PsiField) {
      String newCommentText = formatFieldComment((PsiField)psiElement);
      replaceDocComment(newCommentText, (PsiDocCommentOwner)psiElement);
    }
  }

  private void replaceDocComment(String newCommentText, final PsiDocCommentOwner psiDocCommentOwner) {
    final PsiDocComment oldComment = psiDocCommentOwner.getDocComment();
    if (newCommentText != null) newCommentText = stripSpaces(newCommentText);
    if (newCommentText == null || oldComment == null || newCommentText.equals(oldComment.getText())) {
      return;
    }
    try {
      PsiComment newComment = PsiManager.getInstance(myProject).getElementFactory().createCommentFromText(
        newCommentText, null);
      final ASTNode oldNode = oldComment.getNode();
      final ASTNode newNode = newComment.getNode();
      assert oldNode != null && newNode != null;
      final ASTNode parent = oldNode.getTreeParent();
      parent.replaceChild(oldNode, newNode); //important to replace with tree operation to avoid resolve and repository update
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static String stripSpaces(String text) {
    String[] lines = LineTokenizer.tokenize(text.toCharArray(), false);
    StringBuffer buf = new StringBuffer(text.length());
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) buf.append('\n');
      buf.append(rTrim(lines[i]));
    }
    return buf.toString();
  }

  private static String rTrim(String text) {
    int idx = text.length();
    while (idx > 0) {
      if (!Character.isWhitespace(text.charAt(idx-1))) break;
      idx--;
    }
    return text.substring(0, idx);
  }

  private String formatClassComment(PsiClass psiClass) {
    final String info = getOrigCommentInfo(psiClass);
    if (info == null) return null;

    JDComment comment = getParser().parse(info, new JDClassComment(this));
    return comment.generate(getIndent(psiClass));
  }

  private String formatMethodComment(PsiMethod psiMethod) {
    final String info = getOrigCommentInfo(psiMethod);
    if (info == null) return null;

    JDComment comment = getParser().parse(info, new JDMethodComment(this));
    return comment.generate(getIndent(psiMethod));
  }

  private String formatFieldComment(PsiField psiField) {
    final String info = getOrigCommentInfo(psiField);
    if (info == null) return null;

    JDComment comment = getParser().parse(info, new JDComment(this));
    return comment.generate(getIndent(psiField));
  }

  /**
   * Returns the original comment info of the specified element or null
   * 
   * @param element the specified element
   * @return text chunk
   */
  private static String getOrigCommentInfo(PsiDocCommentOwner element) {
    StringBuffer sb = new StringBuffer();
    PsiElement e = element.getFirstChild();
    if (!(e instanceof PsiComment)) {
      // no comments for this element
      return null;
    }
    else {
      boolean first = true;
      for (; ;) {
        if (e instanceof PsiDocComment) {
          PsiComment cm = (PsiComment)e;
          String text = cm.getText();
          if (text.startsWith("//")) {
            if (!first) sb.append('\n');
            sb.append(text.substring(2).trim());
          }
          else if (text.startsWith("/*")) {
            if (text.charAt(2) == '*') {
              text = text.substring(3, Math.max(3, text.length() - 2));
            }
            else {
              text = text.substring(2, Math.max(2, text.length() - 2));
            }
            sb.append(text);
          }
        }
        else if (!(e instanceof PsiWhiteSpace) && !(e instanceof PsiComment)) {
          break;
        }
        first = false;
        e = e.getNextSibling();
      }

      return sb.toString();
    }
  }

  /**
   * For the specified element returns its indentation
   * 
   * @param element the specified element
   * @return indentation as string
   */
  private static String getIndent(PsiElement element) {
    PsiElement e = element.getFirstChild();
    PsiWhiteSpace lastWS = null;
    for (; ; e = e.getNextSibling()) {
      if (e instanceof PsiWhiteSpace) {
        lastWS = (PsiWhiteSpace)e;
      }
      else if (e instanceof PsiComment) {
        lastWS = null;
      }
      else {
        break;
      }
    }

    e = lastWS == null ? element.getPrevSibling() : lastWS;
    if (!(e instanceof PsiWhiteSpace)) return "";
    PsiWhiteSpace ws = (PsiWhiteSpace)e;
    String t = ws.getText();
    int l = t.length();
    int i = l;
    while (--i >= 0) {
      char ch = t.charAt(i);
      if (ch == '\n' || ch == '\r') break;
    }
    if (i < 0) return t;
    i++;
    if (i == l) return "";
    return t.substring(i);
  }
}
