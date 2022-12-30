package de.plushnikov.intellij.plugin.navigation;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public class ConstructorNavigationTest extends AbstractLombokLightCodeInsightTestCase {
  //@Override
  //protected String getBasePath() {
  //  return super.getBasePath() + "/navigation/constructor";
  //}

  public void testConstructorParameter() {
    PsiClass psiClass = myFixture.addClass("""
                                             @lombok.AllArgsConstructor
                                             class MyBean {
                                               String property;
                                             }""");
    PsiMethod[] constructors = psiClass.getConstructors();
    PsiMethod constructor = assertOneElement(constructors);
    PsiParameter parameter = assertOneElement(constructor.getParameterList().getParameters());
    PsiElement navigationElement = parameter.getNavigationElement();
    PsiField field = assertInstanceOf(navigationElement, PsiField.class);
    assertEquals("MyBean.java", field.getContainingFile().getVirtualFile().getName());
    assertEquals("property", field.getName());
  }

  public void testStaticFactoryParameter() {
    PsiClass psiClass = myFixture.addClass("""
                                             @lombok.AllArgsConstructor(staticName="of", access = AccessLevel.PUBLIC)
                                             class MyBean {
                                               String property;
                                             }""");
    PsiMethod factory = ContainerUtil.find(psiClass.getMethods(), psiMethod -> psiMethod.getName().equals("of"));
    assertNotNull(factory);
    PsiParameter parameter = assertOneElement(factory.getParameterList().getParameters());
    PsiElement navigationElement = parameter.getNavigationElement();
    PsiField field = assertInstanceOf(navigationElement, PsiField.class);
    assertEquals("MyBean.java", field.getContainingFile().getVirtualFile().getName());
    assertEquals("property", field.getName());
  }
}
