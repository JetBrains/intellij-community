/*
 * User: anna
 * Date: 14-May-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.deadCode.DeadCodeExtension;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jdom.Element;

public class TestNGDeadCodeExtension implements DeadCodeExtension{
   public boolean ADD_TESTNG_TO_ENTRIES = true;

  public boolean isSelected() {
    return ADD_TESTNG_TO_ENTRIES;
  }

  public void setSelected(boolean selected) {
    ADD_TESTNG_TO_ENTRIES = selected;
  }

  public String getDisplayName() {
    return "Automatically add all TestNG classes/methods/etc. to entry points";
  }

  public boolean isEntryPoint(RefElement refElement) {
    if (ADD_TESTNG_TO_ENTRIES) {
      final PsiElement element = refElement.getElement();
      if (element instanceof PsiModifierListOwner) {
        return TestNGUtil.hasTest((PsiModifierListOwner)element, false);
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

}