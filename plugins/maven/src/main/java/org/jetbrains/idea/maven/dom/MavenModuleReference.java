package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.beans.MavenModel;
import org.jetbrains.idea.maven.project.Constants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenModuleReference implements PsiReference {
  private PsiElement myElement;
  private VirtualFile myVirtualFile;
  private PsiFile myPsiFile;
  private String myText;
  private TextRange myRange;

  public MavenModuleReference(PsiElement element, VirtualFile virtualFile, PsiFile psiFile, String text, TextRange range) {
    myElement = element;
    myPsiFile = psiFile;
    myVirtualFile = virtualFile;
    myRange = range;
    myText = text;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public String getCanonicalText() {
    return myText;
  }

  public TextRange getRangeInElement() {
    return myRange;
  }

  public PsiElement resolve() {
    VirtualFile baseDir = myPsiFile.getVirtualFile().getParent();
    VirtualFile file = baseDir.findFileByRelativePath(myText + "/" + Constants.POM_XML);
    
    if (file == null) return null;

    return getPsiFile(file);
  }

  public boolean isReferenceTo(PsiElement element) {
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public Object[] getVariants() {
    List<DomFileElement<MavenModel>> files = DomService.getInstance().getFileElements(MavenModel.class,
                                                                                      myElement.getProject(),
                                                                                      GlobalSearchScope.allScope(getProject()));

    List<Object> result = new ArrayList<Object>();

    for (DomFileElement<MavenModel> domFile : files) {
      VirtualFile virtualFile = domFile.getOriginalFile().getVirtualFile();
      if (virtualFile == myVirtualFile) continue;

      PsiFile psiFile = domFile.getFile();
      String modulePath = calcRelativeModulePath(virtualFile);

      LookupElement<PsiFile> lookup = LookupElementFactory.getInstance().createLookupElement(psiFile, modulePath);
      lookup.setPresentableText(modulePath);
      result.add(lookup);
    }

    return result.toArray();
  }

  private String calcRelativeModulePath(VirtualFile modulePom) {
    String result = FileUtil.getRelativePath(new File(myVirtualFile.getParent().getPath()),
                                             new File(modulePom.getPath()));

    result = FileUtil.toSystemIndependentName(result);
    result = result.substring(0, result.length() - ("/" + Constants.POM_XML).length());

    return result;
  }

  private PsiFile getPsiFile(VirtualFile file) {
    Document doc = FileDocumentManager.getInstance().getDocument(file);
    return PsiDocumentManager.getInstance(getProject()).getPsiFile(doc);
  }

  private Project getProject() {
    return myPsiFile.getProject();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return null;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isSoft() {
    return true;
  }
}
