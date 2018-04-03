/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.impl;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.meta.model.Field;
import org.jetbrains.yaml.meta.model.YamlMetaType;
import org.jetbrains.yaml.meta.model.YamlMetaType.ForcedCompletionPath;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLPsiElement;
import org.jetbrains.yaml.psi.YAMLValue;

@ApiStatus.Experimental
public abstract class YamlDocumentationProviderBase extends AbstractDocumentationProvider {
  @Override
  @Nullable
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (!(element instanceof DocumentationElement)) {
      return null;
    }

    return ((DocumentationElement)element).getDocumentation();
  }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                  @NotNull PsiFile file,
                                                  @Nullable PsiElement contextElement) {
    if (contextElement == null || !isRelevant(contextElement)) {
      return null;
    }

    return createFromPsiElement(contextElement);
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement contextElement) {
    if (object instanceof ForcedCompletionPath) {  // deep completion
      return createFromCompletionPath((ForcedCompletionPath)object, contextElement);
    }
    else if (object instanceof String) {  // basic completion
      return createFromString((String)object, contextElement);
    }
    else {
      return null;
    }
  }

  protected abstract boolean isRelevant(@NotNull PsiElement element);

  /**
   * Provides documentation for the specified type and field.
   * If the field isn't given, only the documentation for the type should be returned.
   */
  @Nullable
  protected abstract String getDocumentation(@NotNull Project project, @NotNull YamlMetaType type, @Nullable Field field);

  @Nullable
  protected abstract YamlMetaTypeProvider getMetaTypeProvider(@NotNull PsiElement element);

  @Nullable
  private static <T extends PsiElement> T getTypedAncestorOrSelf(@NotNull PsiElement psi, @NotNull Class<? extends T> clazz) {
    return clazz.isInstance(psi) ? clazz.cast(psi) : PsiTreeUtil.getParentOfType(psi, clazz);
  }

  @Nullable
  private DocumentationElement createFromPsiElement(@Nullable PsiElement contextElement) {
    if (contextElement == null) {
      return null;
    }

    final YamlMetaTypeProvider modelProvider = getMetaTypeProvider(contextElement);
    if (modelProvider == null) {
      return null;
    }

    YAMLPsiElement yamlElement = getTypedAncestorOrSelf(contextElement, YAMLPsiElement.class);

    if (yamlElement == null) {
      return null;
    }

    final YamlMetaTypeProvider.MetaTypeProxy objectMetatype;  // describes the object type
    YamlMetaTypeProvider.MetaTypeProxy fieldMetatype;  // describes the field

    if (yamlElement instanceof YAMLValue) {
      fieldMetatype = modelProvider.getMetaTypeProxy(yamlElement);

      final YAMLMapping mapping = getTypedAncestorOrSelf(yamlElement, YAMLMapping.class);
      objectMetatype = mapping != null ? modelProvider.getMetaTypeProxy(mapping) : null;

      // if the element is the value of the key "kind" and there is a "apiVersion" key in the same mapping,
      // then we show documentation for the resource type, not the field
      if (mapping != null &&
          fieldMetatype != null &&
          fieldMetatype.getField().getName().equals("kind") &&
          mapping.getKeyValues().stream().anyMatch(kv -> "apiVersion".equals(kv.getKeyText().trim()))) {
        fieldMetatype = null;
      }
    }
    else if (yamlElement instanceof YAMLKeyValue) {
      objectMetatype = modelProvider.getMetaTypeProxy(yamlElement);
      fieldMetatype = modelProvider.getKeyValueMetaType((YAMLKeyValue)yamlElement);
    }
    else {
      objectMetatype = modelProvider.getMetaTypeProxy(yamlElement);
      fieldMetatype = null;
    }

    if (objectMetatype == null) {
      return null;
    }

    return new DocumentationElement(contextElement.getManager(),
                                    objectMetatype.getMetaType(),
                                    fieldMetatype != null ? fieldMetatype.getField() : null);
  }

  @Nullable
  private DocumentationElement createFromCompletionPath(@NotNull ForcedCompletionPath path, @NotNull PsiElement contextElement) {
    final YamlMetaTypeProvider typeProvider = getMetaTypeProvider(contextElement);
    if (typeProvider == null) {
      return null;
    }

    final Field field = path.getFinalizingField();
    if (field == null) {
      return null;
    }

    YamlMetaType type = path.getFinalizingType();
    if (type == null) {
      final YamlMetaTypeProvider.MetaTypeProxy proxy = typeProvider.getMetaTypeProxy(contextElement);
      if (proxy == null) {
        return null;
      }

      type = proxy.getMetaType();
    }

    return new DocumentationElement(contextElement.getManager(), type, field);
  }

  @Nullable
  private DocumentationElement createFromString(@NotNull String fieldName, @NotNull PsiElement contextElement) {
    final YamlMetaTypeProvider typeProvider = getMetaTypeProvider(contextElement);
    if (typeProvider == null) {
      return null;
    }

    final YamlMetaTypeProvider.MetaTypeProxy proxy = typeProvider.getMetaTypeProxy(contextElement);
    if (proxy == null) {
      return null;
    }

    final Field field = proxy.getMetaType().findFeatureByName(fieldName);
    if (field == null) {
      return null;
    }

    return new DocumentationElement(contextElement.getManager(), proxy.getMetaType(), field);
  }


  private class DocumentationElement extends LightElement {
    @NotNull private final Project myProject;
    @NotNull private final YamlMetaType myType;
    @Nullable private final Field myField;

    public DocumentationElement(@NotNull PsiManager manager,
                                @NotNull YamlMetaType type,
                                @Nullable Field field) {
      super(manager, YAMLLanguage.INSTANCE);
      myProject = manager.getProject();
      myType = type;
      myField = field;
    }

    @Override
    public String toString() {
      return "DocumentationElement: " + myType + "#" + myField;
    }

    @Override
    public String getText() {
      return myField != null
             ? myField.getName() + " : " + myField.getDefaultType().getDisplayName()
             : myType.getDisplayName(); // todo replace with rich presentation
    }

    @Nullable
    public String getDocumentation() {
      return YamlDocumentationProviderBase.this.getDocumentation(myProject, myType, myField);
    }
  }
}
