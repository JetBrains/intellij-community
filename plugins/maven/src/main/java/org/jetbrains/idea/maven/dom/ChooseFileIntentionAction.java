package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.Dependency;

public class ChooseFileIntentionAction implements IntentionAction {
  private FileChooserFactory myTestFileChooserFactory;

  public void setTestFileChooserFactory(FileChooserFactory factory) {
    myTestFileChooserFactory = factory;
  }

  @NotNull
  public String getFamilyName() {
    return MavenDomBundle.message("inspection.group");
  }

  @NotNull
  public String getText() {
    return MavenDomBundle.message("intention.choose.file");
  }

  public boolean startInWriteAction() {
    return false;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!MavenDomUtil.isPomFile(file)) return false;
    Dependency dep = getDependency(file, editor);
    return dep != null && "system".equals(dep.getScope().getStringValue());
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final Dependency dep = getDependency(file, editor);
    PsiFile currentValue = dep.getSystemPath().getValue();
    FileChooserDialog dialog =
        getFileChooserFactory().createFileChooser(new FileChooserDescriptor(true, false, true, true, false, false),
                                                           project);
    VirtualFile[] files = dialog.choose(currentValue == null ? null : currentValue.getVirtualFile(), project);
    if (files.length == 0) return;

    final PsiFile selectedFile = PsiManager.getInstance(project).findFile(files[0]);
    if (selectedFile == null) return;

    new WriteCommandAction(project) {
      protected void run(Result result) throws Throwable {
        dep.getSystemPath().setValue(selectedFile);
      }
    }.execute();
  }

  private FileChooserFactory getFileChooserFactory() {
    if (myTestFileChooserFactory != null) return myTestFileChooserFactory;
    return FileChooserFactory.getInstance();
  }

  private Dependency getDependency(PsiFile file, Editor editor) {
    PsiElement el = PsiUtilBase.getElementAtOffset(file, editor.getCaretModel().getOffset());
    if (el == null) return null;

    XmlTag tag = PsiTreeUtil.getParentOfType(el, XmlTag.class, false);
    if (tag == null) return null;

    DomElement dom = DomManager.getDomManager(el.getProject()).getDomElement(tag);
    if (dom == null) return null;

    return dom.getParentOfType(Dependency.class, false);
  }
}
