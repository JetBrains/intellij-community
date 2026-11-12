package de.plushnikov.intellij.plugin.navigation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ConstructorNavigationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);


  //@Override
  //protected String getBasePath() {
  //  return super.getBasePath() + "/navigation/constructor";
  //}

  @Test
  public void testConstructorParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass psiClass = myFixture.addClass("""
                                             @lombok.AllArgsConstructor
                                             class MyBean {
                                               String property;
                                             }""");
    ReadAction.runBlocking(() -> {
      PsiMethod[] constructors = psiClass.getConstructors();
      PsiMethod constructor = assertThat(constructors).singleElement().actual();
      PsiParameter parameter = assertThat(constructor.getParameterList().getParameters()).singleElement().actual();
      PsiElement navigationElement = parameter.getNavigationElement();
      PsiField field = assertInstanceOf(PsiField.class, navigationElement);
      assertEquals("MyBean.java", field.getContainingFile().getVirtualFile().getName());
      assertEquals("property", field.getName());
    });
  }

  @Test
  public void testStaticFactoryParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass psiClass = myFixture.addClass("""
                                             @lombok.AllArgsConstructor(staticName="of", access = AccessLevel.PUBLIC)
                                             class MyBean {
                                               String property;
                                             }""");
    ReadAction.runBlocking(() -> {
      PsiMethod factory = ContainerUtil.find(psiClass.getMethods(), psiMethod -> psiMethod.getName().equals("of"));
      assertNotNull(factory);
      PsiParameter parameter = assertThat(factory.getParameterList().getParameters()).singleElement().actual();
      PsiElement navigationElement = parameter.getNavigationElement();
      PsiField field = assertInstanceOf(PsiField.class, navigationElement);
      assertEquals("MyBean.java", field.getContainingFile().getVirtualFile().getName());
      assertEquals("property", field.getName());
    });
  }
}
