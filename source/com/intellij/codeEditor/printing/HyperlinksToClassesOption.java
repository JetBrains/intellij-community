/*
 * User: anna
 * Date: 25-Jan-2008
 */
package com.intellij.codeEditor.printing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.TreeMap;

public class HyperlinksToClassesOption implements PrintOption {
  private JCheckBox myCbGenerateHyperlinksToClasses;
  private boolean isGenerateHyperlinksToClasses = false;

  public boolean isGenerateHyperlinksToClasses() {
    return isGenerateHyperlinksToClasses;
  }

  public void setGenerateHyperlinksToClasses(boolean generateHyperlinksToClasses) {
    isGenerateHyperlinksToClasses = generateHyperlinksToClasses;
  }

  public JComponent createComponent() {
    myCbGenerateHyperlinksToClasses = new JCheckBox(CodeEditorBundle.message("export.to.html.generate.hyperlinks.checkbox"), isGenerateHyperlinksToClasses);
    return myCbGenerateHyperlinksToClasses;
  }

  public boolean isModified() {
    return myCbGenerateHyperlinksToClasses.isSelected() != isGenerateHyperlinksToClasses;
  }

  public void apply() throws ConfigurationException {
    isGenerateHyperlinksToClasses = myCbGenerateHyperlinksToClasses.isSelected();
  }

  public void reset() {
    myCbGenerateHyperlinksToClasses.setSelected(isGenerateHyperlinksToClasses);
  }

  public void disposeUIResources() {
    myCbGenerateHyperlinksToClasses = null;
  }

  @Nullable
  public TreeMap<Integer, PsiReference> collectReferences(PsiFile psiFile, Map<PsiFile, PsiFile> filesMap) {
    if (isGenerateHyperlinksToClasses()) {
      FileType fileType = psiFile.getFileType();
      if(StdFileTypes.JAVA == fileType || StdFileTypes.JSP == fileType) {
        final TreeMap<Integer, PsiReference> refMap = new TreeMap<Integer, PsiReference>();
        findClassReferences(psiFile, refMap, filesMap, psiFile);
        return refMap;
      }
    }
    return null;
  }


  private static void findClassReferences(PsiElement psiElement, TreeMap<Integer, PsiReference> refMap, Map<PsiFile, PsiFile> filesMap, PsiFile psiFile) {
    PsiReference ref = psiElement.getReference();
    if(ref instanceof PsiJavaCodeReferenceElement) {
      PsiElement refElement = ref.resolve();
      if(refElement instanceof PsiClass) {
        PsiFile containingFile = refElement.getContainingFile();
        if(!containingFile.equals(psiFile) && filesMap.get(containingFile) != null) {
          refMap.put(psiElement.getTextRange().getStartOffset(), ref);
        }
        return;
      }
    }
    PsiElement[] children = psiElement.getChildren();
    for (PsiElement aChildren : children) {
      findClassReferences(aChildren, refMap, filesMap, psiFile);
    }
  }

}