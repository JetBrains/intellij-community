/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.dom.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;

import java.util.function.Supplier;

public class ChooseFileIntentionAction implements IntentionAction {
  private Supplier<VirtualFile[]> myFileChooser = null;

  @Override
  @NotNull
  public String getFamilyName() {
    return MavenDomBundle.message("inspection.group");
  }

  @Override
  @NotNull
  public String getText() {
    return MavenDomBundle.message("intention.choose.file");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!MavenDomUtil.isMavenFile(file)) return false;
    MavenDomDependency dep = getDependency(file, editor);
    return dep != null && "system".equals(dep.getScope().getStringValue());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final MavenDomDependency dep = getDependency(file, editor);

    final VirtualFile[] files;
    if (myFileChooser == null) {
      final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, true, false, false);
      final PsiFile currentValue = dep != null ? dep.getSystemPath().getValue() : null;
      final VirtualFile toSelect = currentValue == null ? null : currentValue.getVirtualFile();
      files = FileChooser.chooseFiles(descriptor, project, toSelect);
    }
    else {
      files = myFileChooser.get();
    }
    if (files == null || files.length == 0) return;

    final PsiFile selectedFile = PsiManager.getInstance(project).findFile(files[0]);
    if (selectedFile == null) return;

    if (dep != null) {
      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
      WriteCommandAction.writeCommandAction(project).run(() -> dep.getSystemPath().setValue(selectedFile));
    }
  }

  @TestOnly
  public void setFileChooser(@Nullable final Supplier<VirtualFile[]> fileChooser) {
    myFileChooser = fileChooser;
  }

  @Nullable
  private static MavenDomDependency getDependency(PsiFile file, Editor editor) {
    PsiElement el = PsiUtilCore.getElementAtOffset(file, editor.getCaretModel().getOffset());

    XmlTag tag = PsiTreeUtil.getParentOfType(el, XmlTag.class, false);
    if (tag == null) return null;

    DomElement dom = DomManager.getDomManager(el.getProject()).getDomElement(tag);
    if (dom == null) return null;

    return dom.getParentOfType(MavenDomDependency.class, false);
  }
}
