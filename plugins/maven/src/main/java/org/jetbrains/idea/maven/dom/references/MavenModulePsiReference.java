/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.references;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MavenModulePsiReference extends MavenPsiReference implements LocalQuickFixProvider {
  public MavenModulePsiReference(PsiElement element, String text, TextRange range) {
    super(element, text, range);
  }

  public PsiElement resolve() {
    VirtualFile baseDir = myPsiFile.getVirtualFile().getParent();
    if (baseDir == null) return null;

    String path = FileUtil.toSystemIndependentName(myText);
    VirtualFile file =  baseDir.findFileByRelativePath(path);

    if (file == null || file.isDirectory()) {
      String relPath = FileUtil.toSystemIndependentName(path + "/" + MavenConstants.POM_XML);
      file = baseDir.findFileByRelativePath(relPath);
    }

    if (file == null) return null;

    return getPsiFile(file);
  }

  @NotNull
  public Object[] getVariants() {
    List<DomFileElement<MavenDomProjectModel>> files = MavenDomUtil.collectProjectModels(getProject());

    List<Object> result = new ArrayList<>();

    for (DomFileElement<MavenDomProjectModel> eachDomFile : files) {
      VirtualFile eachVFile = eachDomFile.getOriginalFile().getVirtualFile();
      if (Comparing.equal(eachVFile, myVirtualFile)) continue;

      PsiFile psiFile = eachDomFile.getFile();
      String modulePath = calcRelativeModulePath(myVirtualFile, eachVFile);

      result.add(LookupElementBuilder.create(psiFile, modulePath).withPresentableText(modulePath));
    }

    return result.toArray();
  }

  public static String calcRelativeModulePath(VirtualFile parentPom, VirtualFile modulePom) {
    String result = MavenDomUtil.calcRelativePath(parentPom.getParent(), modulePom);
    if (!result.endsWith("/" + MavenConstants.POM_XML)) return result;

    int to = result.length() - ("/" + MavenConstants.POM_XML).length();
    return result.substring(0, to);
  }

  private PsiFile getPsiFile(VirtualFile file) {
    return PsiManager.getInstance(getProject()).findFile(file);
  }

  private Project getProject() {
    return myPsiFile.getProject();
  }

  public LocalQuickFix[] getQuickFixes() {
    if (myText.length() == 0 || resolve() != null) return LocalQuickFix.EMPTY_ARRAY;
    return new LocalQuickFix[]{new CreateModuleFix(true, myText, myPsiFile), new CreateModuleFix(false, myText, myPsiFile)};
  }

  private static class CreateModuleFix extends LocalQuickFixOnPsiElement {

    private final boolean myWithParent;
    private final String myModulePath;

    private CreateModuleFix(boolean withParent, String modulePath, PsiFile psiFile) {
      super(psiFile);
      myWithParent = withParent;
      myModulePath = modulePath;
    }

    @NotNull
    @Override
    public String getText() {
      return myWithParent ? MavenDomBundle.message("fix.create.module.with.parent") : MavenDomBundle.message("fix.create.module");
    }

    @NotNull
    public String getFamilyName() {
      return MavenDomBundle.message("inspection.group");
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      try {
        PsiFile psiFile = (PsiFile)startElement;
        VirtualFile modulePom = createModulePom(psiFile.getVirtualFile());
        MavenId id = MavenDomUtil.describe(psiFile);

        String groupId = id.getGroupId() == null ? "groupId" : id.getGroupId();
        String artifactId = modulePom.getParent().getName();
        String version = id.getVersion() == null ? "version" : id.getVersion();
        MavenUtil.runOrApplyMavenProjectFileTemplate(project,
                                                     modulePom,
                                                     new MavenId(groupId, artifactId, version),
                                                     myWithParent ? id : null,
                                                     psiFile.getVirtualFile(),
                                                     true);
      }
      catch (IOException e) {
        MavenUtil.showError(project, "Cannot create a module", e);
      }
    }

    private VirtualFile createModulePom(VirtualFile virtualFile) throws IOException {
      VirtualFile baseDir = virtualFile.getParent();
      String modulePath = PathUtil.getCanonicalPath(baseDir.getPath() + "/" + myModulePath);
      String pomFileName = MavenConstants.POM_XML;

      if (!new File(FileUtil.toSystemDependentName(modulePath)).isDirectory()) {
        String fileName = PathUtil.getFileName(modulePath);
        if (MavenUtil.isPomFileName(fileName) || MavenUtil.isPotentialPomFile(fileName)) {
          modulePath = PathUtil.getParentPath(modulePath);
          pomFileName = fileName;
        }
      }

      VirtualFile moduleDir = VfsUtil.createDirectories(modulePath);
      return moduleDir.createChildData(this, pomFileName);
    }
  }
}
