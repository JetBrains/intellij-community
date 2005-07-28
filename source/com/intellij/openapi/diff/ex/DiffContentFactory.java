package com.intellij.openapi.diff.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.FragmentContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public class DiffContentFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.ex.DiffContentFactory");

  private DiffContentFactory() {}

  @Nullable
  public static DiffContent fromPsiElement(PsiElement psiElement) {
    if (psiElement instanceof PsiFile) {
      return DiffContent.fromFile(psiElement.getProject(), ((PsiFile)psiElement).getVirtualFile());
    } else if (psiElement instanceof PsiDirectory) {
      return DiffContent.fromFile(psiElement.getProject(), ((PsiDirectory)psiElement).getVirtualFile());
    }
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) {
      String text = psiElement.getText();
      return text != null ? new SimpleContent(text) : null;
    }
    DiffContent wholeFileContent = DiffContent.fromFile(psiElement.getProject(), containingFile.getVirtualFile());
    if (wholeFileContent == null || wholeFileContent.getDocument() == null) return null;
    Project project = psiElement.getProject();
    return new FragmentContent(wholeFileContent, psiElement.getTextRange(), project);
  }

  @Nullable
  public static SimpleDiffRequest comparePsiElements(PsiElement psiElement1, PsiElement psiElement2, String title) {
    if (!psiElement1.isValid() || !psiElement2.isValid()) return null;
    Project project = psiElement1.getProject();
    LOG.assertTrue(project == psiElement2.getProject());
    DiffContent content1 = fromPsiElement(psiElement1);
    DiffContent content2 = fromPsiElement(psiElement2);
    if (content1 == null || content2 == null) return null;
    SimpleDiffRequest diffRequest = new SimpleDiffRequest(project, title);
    diffRequest.setContents(content1, content2);
    return diffRequest;
  }
}