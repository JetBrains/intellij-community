// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.meta.impl;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.meta.model.Field;
import org.jetbrains.yaml.meta.model.TypeFieldPair;
import org.jetbrains.yaml.meta.model.YamlMetaType;
import org.jetbrains.yaml.meta.model.YamlMetaType.ForcedCompletionPath;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLPsiElement;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.Objects;

@ApiStatus.Internal
public abstract class YamlDocumentationProviderBase implements DocumentationProvider {

  @Override
  public @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (!(element instanceof DocumentationElement)) {
      return null;
    }

    return ((DocumentationElement)element).getDocumentation();
  }

  @Override
  public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                            @NotNull PsiFile file,
                                                            @Nullable PsiElement contextElement,
                                                            int targetOffset) {
    if (contextElement == null || !isRelevant(contextElement)) {
      return null;
    }

    return createFromPsiElement(contextElement);
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement contextElement) {
    if(!isRelevant(contextElement))
      return null;

    if (object instanceof ForcedCompletionPath) {  // deep completion
      return createFromCompletionPath((ForcedCompletionPath)object, contextElement);
    }
    else if (object instanceof TypeFieldPair) {  // basic completion with Field object
      return createFromField((TypeFieldPair)object, contextElement);
    }
    else if (object instanceof String) {  // basic completion with plain string
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
  protected abstract @Nullable @Nls String getDocumentation(@NotNull Project project, @NotNull YamlMetaType type, @Nullable Field field);

  protected abstract @Nullable YamlMetaTypeProvider getMetaTypeProvider(@NotNull PsiElement element);

  private static @Nullable <T extends PsiElement> T getTypedAncestorOrSelf(@NotNull PsiElement psi, @NotNull Class<? extends T> clazz) {
    return clazz.isInstance(psi) ? clazz.cast(psi) : PsiTreeUtil.getParentOfType(psi, clazz);
  }

  private @Nullable DocumentationElement createFromPsiElement(@Nullable PsiElement contextElement) {
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

  private @Nullable DocumentationElement createFromCompletionPath(@NotNull ForcedCompletionPath path, @NotNull PsiElement contextElement) {
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

  private @Nullable DocumentationElement createFromString(@NotNull String fieldName, @NotNull PsiElement contextElement) {
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

  private @NotNull DocumentationElement createFromField(@NotNull TypeFieldPair field, @NotNull PsiElement contextElement) {
    return new DocumentationElement(contextElement.getManager(), field.getMetaType(), field.getField());
  }



  private class DocumentationElement extends LightElement {
    private final @NotNull Project myProject;
    private final @NotNull YamlMetaType myType;
    private final @Nullable Field myField;

    DocumentationElement(@NotNull PsiManager manager,
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

    public @Nullable @Nls String getDocumentation() {
      return YamlDocumentationProviderBase.this.getDocumentation(myProject, myType, myField);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DocumentationElement element = (DocumentationElement)o;
      return Objects.equals(myProject, element.myProject) &&
             Objects.equals(myType, element.myType) &&
             Objects.equals(myField, element.myField);
    }

    @Override
    public int hashCode() {

      return Objects.hash(myProject, myType, myField);
    }
  }
}
