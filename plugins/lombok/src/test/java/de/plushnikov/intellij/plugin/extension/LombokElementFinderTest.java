package de.plushnikov.intellij.plugin.extension;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import lombok.Builder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
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
  private JavaFileManager javaFileManager;

  @Mock
  private GlobalSearchScope scope;

  @Before
  public void setUp() throws Exception {
    doReturn(javaFileManager).when(elementFinder).getServiceManager(any(GlobalSearchScope.class));
  }

  @Test
  public void findClass() throws Exception {
    final PsiClass psiBaseClass = mock(PsiClass.class);
    when(javaFileManager.findClass(BASE_CLASS, scope)).thenReturn(psiBaseClass);

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
    when(psiBuilderClass.getName()).thenReturn(SOME_CLASS_BUILDER.substring(1));

    final PsiClass psiClass = elementFinder.findClass(BASE_CLASS + SOME_CLASS_BUILDER, scope);
    assertNotNull(psiClass);
    verify(javaFileManager).findClass(BASE_CLASS, scope);
  }

  @Test
  public void findClassRecursion() throws Exception {
    // setup recursive calls of elementFinder
    when(javaFileManager.findClass(BASE_CLASS, scope)).thenAnswer(new Answer<PsiClass>() {
      @Override
      public PsiClass answer(InvocationOnMock invocation) throws Throwable {
        final String fqn = (String) invocation.getArguments()[0];
        final GlobalSearchScope searchScope = (GlobalSearchScope) invocation.getArguments()[1];
        return elementFinder.findClass(fqn + SOME_CLASS_BUILDER, searchScope);
      }
    });

    final PsiClass psiClass = elementFinder.findClass(BASE_CLASS + SOME_CLASS_BUILDER, scope);
    assertNull(psiClass);
    verify(javaFileManager).findClass(BASE_CLASS, scope);
  }

}
