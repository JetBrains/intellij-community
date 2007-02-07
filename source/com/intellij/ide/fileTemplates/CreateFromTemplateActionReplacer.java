package com.intellij.ide.fileTemplates;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

public interface CreateFromTemplateActionReplacer {
  ExtensionPointName<CreateFromTemplateActionReplacer> CREATE_FROM_TEMPLATE_REPLACER =
    ExtensionPointName.create("com.intellij.createFromTemplateActionReplacer");

  @Nullable
  AnAction replaceCreateFromFileTemplateAction(FileTemplate fileTemplate);
}