/*
 * User: anna
 * Date: 14-May-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.deadCode.UnusedCodeExtension;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class TestNGUnusedCodeExtension extends UnusedCodeExtension {
   public boolean ADD_TESTNG_TO_ENTRIES = true;

  public boolean isSelected() {
    return ADD_TESTNG_TO_ENTRIES;
  }

  public void setSelected(boolean selected) {
    ADD_TESTNG_TO_ENTRIES = selected;
  }

  @NotNull
  public String getDisplayName() {
    return "Automatically add all TestNG classes/methods/etc. to entry points";
  }

  public boolean isEntryPoint(RefElement refElement) {
    return isEntryPoint(refElement.getElement());
  }

  @Override
  public boolean isEntryPoint(PsiElement psiElement) {
    if (ADD_TESTNG_TO_ENTRIES) {
      if (psiElement instanceof PsiModifierListOwner) {
        return TestNGUtil.hasTest((PsiModifierListOwner)psiElement, false) || (psiElement instanceof PsiMethod && TestNGUtil.hasConfig((PsiModifierListOwner)psiElement));
      }
    }
    return false;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  @Nullable
  public String[] getIgnoreAnnotations() {
    return TestNGUtil.CONFIG_ANNOTATIONS_FQN;
  }
}