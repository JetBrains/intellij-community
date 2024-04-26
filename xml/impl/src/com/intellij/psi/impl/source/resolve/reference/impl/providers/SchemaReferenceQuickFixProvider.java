// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import org.jetbrains.annotations.NotNull;

final class SchemaReferenceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<TypeOrElementOrAttributeReference> {
  @Override
  public void registerFixes(@NotNull TypeOrElementOrAttributeReference ref, @NotNull QuickFixActionRegistrar registrar) {
    if (ref.getType() == TypeOrElementOrAttributeReference.ReferenceType.TypeReference) {
      registrar.register(
        new CreateXmlElementIntentionAction("xml.quickfix.schema.create.complex.type", SchemaReferencesProvider.COMPLEX_TYPE_TAG_NAME, ref)
      );
      registrar.register(
        new CreateXmlElementIntentionAction("xml.quickfix.schema.create.simple.type", SchemaReferencesProvider.SIMPLE_TYPE_TAG_NAME, ref)
      );
    }
    else if (ref.getType() != null) {
      if (ref.getType() == TypeOrElementOrAttributeReference.ReferenceType.ElementReference) {
        registrar.register(new CreateXmlElementIntentionAction("xml.quickfix.schema.create.element",
                                                               SchemaReferencesProvider.ELEMENT_TAG_NAME, ref));
      } else if (ref.getType() == TypeOrElementOrAttributeReference.ReferenceType.AttributeReference) {
        registrar.register(new CreateXmlElementIntentionAction("xml.quickfix.schema.create.attribute",
                                                               SchemaReferencesProvider.ATTRIBUTE_TAG_NAME, ref));
      } else if (ref.getType() == TypeOrElementOrAttributeReference.ReferenceType.AttributeGroupReference) {
        registrar.register(new CreateXmlElementIntentionAction("xml.quickfix.schema.create.attribute.group",
                                                               SchemaReferencesProvider.ATTRIBUTE_GROUP_TAG_NAME, ref));
      } else if (ref.getType() == TypeOrElementOrAttributeReference.ReferenceType.GroupReference) {
        registrar.register(new CreateXmlElementIntentionAction("xml.quickfix.schema.create.group",
                                                               SchemaReferencesProvider.GROUP_TAG_NAME, ref));
      }
    }
  }

  @Override
  public @NotNull Class<TypeOrElementOrAttributeReference> getReferenceClass() {
    return TypeOrElementOrAttributeReference.class;
  }
}
