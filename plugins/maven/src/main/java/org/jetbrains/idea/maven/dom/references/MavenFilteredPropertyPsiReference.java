package org.jetbrains.idea.maven.dom.references;

import com.intellij.lang.properties.psi.Property;
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

public class MavenFilteredPropertyPsiReference extends MavenPropertyPsiReference {
  public MavenFilteredPropertyPsiReference(MavenProject mavenProject, PsiElement element, String text, TextRange range) {
    super(mavenProject, element, text, range);
  }

  @Override
  protected PsiElement doResolve() {
    PsiElement result = super.doResolve();
    if (result != null) return result;

    for (String each : myMavenProject.getFilters()) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(each);
      if (file == null) continue;
      Property property = MavenDomUtil.findProperty(myProject, file, myText);
      if (property != null) return property;
    }
    
    return null;
  }

  @Override
  protected void collectVariants(List<Object> result) {
    super.collectVariants(result);

    for (String each : myMavenProject.getFilters()) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(each);
      if (file == null) continue;
      collectPropertiesFileVariants(MavenDomUtil.getPropertiesFile(myProject, file), "", result);
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