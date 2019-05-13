/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author yole
 */
public class SchemaReferenceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<TypeOrElementOrAttributeReference> {
  @Override
  public void registerFixes(@NotNull TypeOrElementOrAttributeReference ref, @NotNull QuickFixActionRegistrar registrar) {
    if (ref.getType() == TypeOrElementOrAttributeReference.ReferenceType.TypeReference) {
      registrar.register(
        new CreateXmlElementIntentionAction("xml.schema.create.complex.type.intention.name", SchemaReferencesProvider.COMPLEX_TYPE_TAG_NAME, ref)
      );
      registrar.register(
        new CreateXmlElementIntentionAction("xml.schema.create.simple.type.intention.name", SchemaReferencesProvider.SIMPLE_TYPE_TAG_NAME, ref)
      );
    }
    else if (ref.getType() != null) {
      @PropertyKey(resourceBundle = XmlBundle.PATH_TO_BUNDLE) String key = null;
      @NonNls String declarationTagName = null;

      if (ref.getType() == TypeOrElementOrAttributeReference.ReferenceType.ElementReference) {
        declarationTagName = SchemaReferencesProvider.ELEMENT_TAG_NAME;
        key = "xml.schema.create.element.intention.name";
      } else if (ref.getType() == TypeOrElementOrAttributeReference.ReferenceType.AttributeReference) {
        declarationTagName = SchemaReferencesProvider.ATTRIBUTE_TAG_NAME;
        key = "xml.schema.create.attribute.intention.name";
      } else if (ref.getType() == TypeOrElementOrAttributeReference.ReferenceType.AttributeGroupReference) {
        declarationTagName = SchemaReferencesProvider.ATTRIBUTE_GROUP_TAG_NAME;
        key = "xml.schema.create.attribute.group.intention.name";
      } else if (ref.getType() == TypeOrElementOrAttributeReference.ReferenceType.GroupReference) {
        declarationTagName = SchemaReferencesProvider.GROUP_TAG_NAME;
        key = "xml.schema.create.group.intention.name";
      }

      assert key != null;
      registrar.register(new CreateXmlElementIntentionAction(key, declarationTagName, ref));
    }
  }

  @NotNull
  @Override
  public Class<TypeOrElementOrAttributeReference> getReferenceClass() {
    return TypeOrElementOrAttributeReference.class;
  }
}
