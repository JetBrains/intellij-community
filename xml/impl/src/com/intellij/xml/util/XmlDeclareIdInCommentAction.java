package com.intellij.xml.util;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class XmlDeclareIdInCommentAction implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.XmlDeclareIdInCommentAction");

  private String myId;

  public XmlDeclareIdInCommentAction(@NotNull final String id) {
    myId = id;
  }

  @NotNull
  public String getName() {
    return XmlErrorMessages.message("declare.id.in.comment.quickfix");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Nullable
  public static String getImplicitlyDeclaredId(@NotNull final PsiComment comment) {
    final String text = getUncommentedText(comment);
    if (text == null) return null;

    if (text.startsWith("@declare id=\"")) {
      final String result = text.substring("@declare id=\"".length() - 1);
      return StringUtil.unquoteString(result);
    }

    return null;
  }

  @Nullable
  private static String getUncommentedText(@NotNull final PsiComment comment) {
    final PsiFile psiFile = comment.getContainingFile();
    final Language language = psiFile.getViewProvider().getBaseLanguage();
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
    if (commenter != null) {
      String text = comment.getText();

      final String prefix = commenter.getBlockCommentPrefix();
      if (prefix != null && text.startsWith(prefix)) {
        text = text.substring(prefix.length());
        final String suffix = commenter.getBlockCommentSuffix();
        if (suffix != null && text.length() > suffix.length()) {
          return text.substring(0, text.length() - suffix.length()).trim();
        }
      }
    }

    return null;
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    final PsiFile psiFile = psiElement.getContainingFile();
    if (!CodeInsightUtilBase.prepareFileForWrite(psiFile)) return;

    final XmlTag tag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);
    if (tag == null) return;

    final Language language = psiFile.getViewProvider().getBaseLanguage();
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
    if (commenter == null) return;

    final PsiFile tempFile = PsiFileFactory.getInstance(project).createFileFromText("dummy", language, commenter.getBlockCommentPrefix() +
                                                                                                       "@declare id=\"" +
                                                                                                       myId +
                                                                                                       "\"" +
                                                                                                       commenter.getBlockCommentSuffix() +
                                                                                                       "\n");

    final PsiElement parent = tag.getParent();
    if (parent != null) {
      parent.getNode().addChild(tempFile.getChildren()[0].getChildren()[0].getNode(), tag.getNode());
      parent.getNode().addChild(tempFile.getChildren()[0].getChildren()[0].getNode(), tag.getNode());
    }
  }
}
