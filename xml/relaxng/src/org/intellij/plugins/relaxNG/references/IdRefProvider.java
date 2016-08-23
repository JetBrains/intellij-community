/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.references;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.AttributeValueSelfReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.patterns.XmlPatterns.xmlAttribute;
import static com.intellij.patterns.XmlPatterns.xmlAttributeValue;

public class IdRefProvider extends PsiReferenceProvider {
  public static final HasIdRefTypeCondition HAS_ID_REF_TYPE = new HasIdRefTypeCondition();
  public static final HasIdTypeCondition HAS_ID_TYPE = new HasIdTypeCondition();

  @Override
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    final XmlAttributeValue value = (XmlAttributeValue)element;

    if (hasIdRefType(value)) {
      return new PsiReference[]{
              new IdReference(value)
      };
    } else if (hasIdType(value)) {
      return new PsiReference[]{
              new AttributeValueSelfReference(element)
      };
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static class IdReference extends PsiReferenceBase<XmlAttributeValue> {

    private static final Key<XmlAttributeValue> TARGET = Key.create("target");
    private static final Key<Set<XmlAttributeValue>> VARIANTS = Key.create("variants");
    private static final XmlAttributeValuePattern PATTERN = xmlAttributeValue().withParent(xmlAttribute()).with(HAS_ID_TYPE);

    private final AttributeValueCondition myCondition;

    public IdReference(XmlAttributeValue element) {
      super(element, TextRange.from(1, element.getTextLength() - 2), true);
      myCondition = new AttributeValueCondition(element.getValue());
    }

    @Override
    public PsiElement resolve() {
      final ProcessingContext context = new ProcessingContext();
      final ResolvingVisitor visitor = new ResolvingVisitor(PATTERN.with(myCondition).save(TARGET), context) {
        @Override
        public void visitXmlTag(XmlTag tag) {
          super.visitXmlTag(tag);
          if (shouldContinue()) {
            visitSubTags(tag);
          }
        }
        @Override
        protected boolean shouldContinue() {
          return context.get(TARGET) == null;
        }
      };

      process(visitor);

      return context.get(TARGET);
    }

    private void process(ResolvingVisitor visitor) {
      final XmlDocument document = PsiTreeUtil.getParentOfType(getElement(), XmlDocument.class);
      if (document != null) {
        visitor.execute(document);
      }
    }

    @Override
    @NotNull
    public Object[] getVariants() {
      final ProcessingContext context = new ProcessingContext();
      context.put(VARIANTS, new HashSet<>());

      final ResolvingVisitor visitor = new ResolvingVisitor(PATTERN.with(AddValueCondition.create(VARIANTS)), context) {
        @Override
        public void visitXmlTag(XmlTag tag) {
          super.visitXmlTag(tag);
          visitSubTags(tag);
        }
      };

      process(visitor);

      return AttributeValueFunction.toStrings(context.get(VARIANTS));
    }
  }

  private static boolean hasIdType(XmlAttributeValue xmlAttributeValue) {
    final XmlAttributeDescriptor descriptor = ((XmlAttribute)xmlAttributeValue.getParent()).getDescriptor();
    return descriptor != null && descriptor.hasIdType();
  }

  private static boolean hasIdRefType(XmlAttributeValue xmlAttributeValue) {
    final XmlAttributeDescriptor descriptor = ((XmlAttribute)xmlAttributeValue.getParent()).getDescriptor();
    return descriptor != null && descriptor.hasIdRefType();
  }

  static class HasIdTypeCondition extends PatternCondition<XmlAttributeValue> {
    public HasIdTypeCondition() {
      super("IdType");
    }

    @Override
    public boolean accepts(@NotNull XmlAttributeValue xmlAttributeValue, ProcessingContext context) {
      return hasIdType(xmlAttributeValue);
    }
  }

  static class HasIdRefTypeCondition extends PatternCondition<XmlAttributeValue> {
    public HasIdRefTypeCondition() {
      super("IdRef");
    }

    @Override
    public boolean accepts(@NotNull XmlAttributeValue xmlAttributeValue,  ProcessingContext context) {
      return hasIdRefType(xmlAttributeValue);
    }
  }
}