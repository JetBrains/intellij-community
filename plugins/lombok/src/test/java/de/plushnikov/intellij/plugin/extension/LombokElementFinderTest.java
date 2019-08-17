package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import lombok.Builder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LombokElementFinderTest {

  private static final String BASE_CLASS = "de.test.SomeClass";
  private static final String SOME_CLASS_BUILDER = ".SomeClassBuilder";

  @Spy
  private LombokElementFinder elementFinder;

  @Mock
  private JavaFullClassNameIndex javaFullClassNameIndex;

  @Mock
  private GlobalSearchScope scope;

  @Mock
  private Project project;

  @Before
  public void setUp() {
    doReturn(project).when(scope).getProject();
    doReturn(javaFullClassNameIndex).when(elementFinder).getJavaFullClassNameIndexInstance();
  }

  @Test
  public void findClass() {
    final PsiClass psiBaseClass = mock(PsiClass.class);
    when(javaFullClassNameIndex.get(eq(BASE_CLASS.hashCode()), any(Project.class), eq(scope))).thenReturn(Collections.singleton(psiBaseClass));

    final PsiModifierList psiModifierList = mock(PsiModifierList.class);
    when(psiBaseClass.getModifierList()).thenReturn(psiModifierList);

    final PsiAnnotation psiAnnotation = mock(PsiAnnotation.class);
    when(psiModifierList.getAnnotations()).thenReturn(new PsiAnnotation[]{psiAnnotation});

    when(psiAnnotation.getQualifiedName()).thenReturn(Builder.class.getName());

    final PsiJavaCodeReferenceElement referenceElement = mock(PsiJavaCodeReferenceElement.class);
    when(psiAnnotation.getNameReferenceElement()).thenReturn(referenceElement);
    when(referenceElement.getReferenceName()).thenReturn(Builder.class.getSimpleName());

    final PsiClass psiBuilderClass = mock(PsiClass.class);
    when(psiBaseClass.findInnerClassByName(eq(SOME_CLASS_BUILDER.substring(1)), anyBoolean())).thenReturn(psiBuilderClass);

    final PsiClass psiClass = elementFinder.findClass(BASE_CLASS + SOME_CLASS_BUILDER, scope);
    assertNotNull(psiClass);
    verify(javaFullClassNameIndex).get(eq(BASE_CLASS.hashCode()), any(Project.class), eq(scope));
  }
}
