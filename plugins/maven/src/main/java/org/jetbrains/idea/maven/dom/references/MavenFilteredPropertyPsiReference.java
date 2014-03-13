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

import com.intellij.lang.properties.IProperty;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.List;
import java.util.Set;

public class MavenFilteredPropertyPsiReference extends MavenPropertyPsiReference {
  public MavenFilteredPropertyPsiReference(MavenProject mavenProject, PsiElement element, String text, TextRange range) {
    super(mavenProject, element, text, range, true);
  }

  @Override
  protected PsiElement doResolve() {
    PsiElement result = super.doResolve();
    if (result != null) return result;

    for (String each : myMavenProject.getFilterPropertiesFiles()) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(each);
      if (file == null) continue;
      IProperty property = MavenDomUtil.findProperty(myProject, file, myText);
      if (property != null) return property.getPsiElement();
    }

    return null;
  }

  @Override
  protected void collectVariants(List<Object> result, Set<String> variants) {
    super.collectVariants(result, variants);

    for (String each : myMavenProject.getFilterPropertiesFiles()) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(each);
      if (file == null) continue;
      collectPropertiesFileVariants(MavenDomUtil.getPropertiesFile(myProject, file), null, result, variants);
    }
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    String newText = myRange.replace(myElement.getText(), newElementName);
    PsiFile psiFile = myElement.getContainingFile();
    String newFileText = myElement.getTextRange().replace(psiFile.getText(), newText);
    PsiFile f = PsiFileFactory.getInstance(myProject).createFileFromText("__" + psiFile.getName(), psiFile.getLanguage(), newFileText);
    PsiElement el = f.findElementAt(myElement.getTextOffset());
    return myElement.replace(el);
  }
}