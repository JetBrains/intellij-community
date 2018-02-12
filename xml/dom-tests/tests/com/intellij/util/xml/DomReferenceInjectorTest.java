/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DomReferenceInjectorTest extends DomHardCoreTestCase {
  public void testBasic() {
    MyElement element = createElement("<a><value>abc${prop}def</value></a>", MyElement.class);
    assertEquals("abc${prop}def", element.getValue().getStringValue());
    assertEquals("abc${prop}def", element.getValue().getValue());
  }

  public void testWithInjector() {
    MyElement element = createElement("<a><value>abc${prop}def</value></a>", MyElement.class);

    registerInjectorFor(element, null);

    assertEquals("abcFOOdef", element.getValue().getStringValue());
    assertEquals("abcFOOdef", element.getValue().getValue());
  }

  public void testCorrectlyCalculateOffsetWithInjector() {
    MyElement element = createElement("<a><value>   abc${prop}def   </value></a>", MyElement.class);

    registerInjectorFor(element, null);

    assertEquals("abcFOOdef", element.getValue().getStringValue());
    assertEquals("abcFOOdef", element.getValue().getValue());
  }

  public void testWithInjectorAndConverter() {
    MyElement element = createElement("<a><converted-value>abc${prop}def</converted-value></a>", MyElement.class);

    registerInjectorFor(element, null);

    assertEquals("abcFOOdef", element.getConvertedValue().getStringValue());
    assertEquals("abcBARdef", element.getConvertedValue().getValue());
  }

  public void testReference() {
    String text = "<a><value>abc${prop}def</value></a>";
    MyElement element = createElement(text, MyElement.class);

    MyPsiElement targetElement = new MyPsiElement();
    registerInjectorFor(element, targetElement);

    assertEquals("abcFOOdef", element.getValue().getStringValue());
    assertEquals("abcFOOdef", element.getValue().getValue());
    
    assertReference(element.getValue(), targetElement, text.indexOf("${prop}") + 1);
  }

  public void testAttribute() {
    String text = "<a attr=\"abc${prop}def\"/>";
    MyElement element = createElement(text, MyElement.class);

    MyPsiElement targetElement = new MyPsiElement();
    registerInjectorFor(element, targetElement);

    assertEquals("abcFOOdef", element.getAttr().getStringValue());
    assertEquals("abcFOOdef", element.getAttr().getValue());

    assertReference(element.getAttr(), targetElement, text.indexOf("${prop}") + 1);
  }

  private void registerInjectorFor(DomElement element, PsiElement targetElement) {
    DomUtil.getFileElement(element).getFileDescription().registerReferenceInjector(new MyInjector(targetElement));
  }

  public interface MyElement extends DomElement {
    GenericDomValue<String> getValue();

    @Convert(MyConverter.class)
    GenericDomValue<String> getConvertedValue();

    GenericAttributeValue<String> getAttr();
  }

  public static class MyConverter extends Converter<String> {
    @Override
    public String fromString(@Nullable @NonNls String s, ConvertContext context) {
      return s == null ? null : s.replaceAll("FOO", "BAR");
    }

    @Override
    public String toString(@Nullable String s, ConvertContext context) {
      return s;
    }
  }

  public static class MyPsiElement implements PsiElement {
    @Override
    @NotNull
    public Project getProject() throws PsiInvalidElementAccessException {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public Language getLanguage() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiManager getManager() {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public PsiElement[] getChildren() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement getParent() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement getFirstChild() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement getLastChild() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement getNextSibling() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement getPrevSibling() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
      throw new UnsupportedOperationException();
    }

    @Override
    public TextRange getTextRange() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getStartOffsetInParent() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getTextLength() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement findElementAt(int offset) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiReference findReferenceAt(int offset) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getTextOffset() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getText() {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public char[] textToCharArray() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement getNavigationElement() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement getOriginalElement() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean textMatches(@NotNull @NonNls CharSequence text) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean textMatches(@NotNull PsiElement element) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean textContains(char c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void acceptChildren(@NotNull PsiElementVisitor visitor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement copy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
      throws IncorrectOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete() throws IncorrectOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void checkDelete() throws IncorrectOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValid() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWritable() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiReference getReference() {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public PsiReference[] getReferences() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getCopyableUserData(Key<T> key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> void putCopyableUserData(Key<T> key, T value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                       @NotNull ResolveState state,
                                       @Nullable PsiElement lastParent,
                                       @NotNull PsiElement place) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement getContext() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPhysical() {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public GlobalSearchScope getResolveScope() {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public SearchScope getUseScope() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ASTNode getNode() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEquivalentTo(PsiElement another) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Icon getIcon(int flags) {
      throw new UnsupportedOperationException();
    }
  }

  private static class MyInjector implements DomReferenceInjector {
    private final PsiElement myMyTargetElement;

    public MyInjector(PsiElement myTargetElement) {
      myMyTargetElement = myTargetElement;
    }

    @Override
    public String resolveString(@Nullable String unresolvedText, @NotNull ConvertContext context) {
      return unresolvedText == null ? null : unresolvedText.replaceAll("\\$\\{prop\\}", "FOO");
    }

    @Override
    @NotNull
    public PsiReference[] inject(@Nullable String unresolvedText, @NotNull final PsiElement element, @NotNull ConvertContext context) {
      final String prop = "${prop}";
      int index = unresolvedText == null ? -1 : unresolvedText.indexOf(prop);
      if (index == -1) return PsiReference.EMPTY_ARRAY;

      TextRange textRange = ElementManipulators.getValueTextRange(element);
      final TextRange refRange = TextRange.from(textRange.getStartOffset() + index, prop.length());

      return new PsiReference[] {
        new PsiReference() {
          @Override
          public PsiElement getElement() {
            return element;
          }

          @Override
          public TextRange getRangeInElement() {
            return refRange;
          }

          @Override
          public PsiElement resolve() {
            return myMyTargetElement;
          }

          @Override
          @NotNull
          public String getCanonicalText() {
            return prop;
          }

          @Override
          public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
            return null;
          }

          @Override
          public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
            return null;
          }

          @Override
          public boolean isReferenceTo(PsiElement element) {
            return false;
          }

          @Override
          @NotNull
          public Object[] getVariants() {
            return EMPTY_ARRAY;
          }

          @Override
          public boolean isSoft() {
            return true;
          }
        }
      };
    }
  }
}
