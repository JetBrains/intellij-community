// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.actions.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions.ActionDescription;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.text.NameUtilCore;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public abstract class AbstractDomGenerateProvider<T extends DomElement> extends DefaultGenerateElementProvider<T> {
  public static final String NAMESPACE_PREFIX_VAR = "NS_PREFIX";

  @Nullable private final String myMappingId;

  /**
   * @deprecated Provide both action description and text, as they have different capitalization rules.
   */
  @Deprecated(forRemoval = true)
  public AbstractDomGenerateProvider(@Nls final String description, final Class<T> aClass) {
    this(description, aClass, null);
  }

  /**
   * @deprecated Provide both action description and text, as they have different capitalization rules.
   */
  @Deprecated(forRemoval = true)
  public AbstractDomGenerateProvider(@Nls final String description, final Class<T> aClass, @Nullable String mappingId) {
    this(description, description, aClass, mappingId);
  }

  public AbstractDomGenerateProvider(@ActionText String text, @ActionDescription String description, final Class<T> aClass) {
    this(text, description, aClass, null);
  }

  public AbstractDomGenerateProvider(@ActionText String text, @ActionDescription String description, final Class<T> aClass, @Nullable String mappingId) {
    super(text, description, aClass);
    myMappingId = mappingId;
  }

  @Override
  public T generate(final Project project, final Editor editor, final PsiFile file) {
    DomElement parentDomElement = getParentDomElement(project, editor, file);

    final T t = generate(parentDomElement, editor);

    runTemplate(editor, file, t, getPredefinedVars(parentDomElement, t, editor, file));

    return t;
  }

  protected Map<String, String> getPredefinedVars(@Nullable DomElement parentDomElement,
                                                  @Nullable T t,
                                                  @NotNull Editor editor,
                                                  @NotNull PsiFile file) {
    return createNamespacePrefixMap(parentDomElement);
  }

  @NotNull
  public static Map<String, String> createNamespacePrefixMap(@Nullable DomElement domElement) {
    Map<String, String> vars = new HashMap<>();

    addNamespacePrefix(domElement, vars);

    return vars;
  }

  public static void addNamespacePrefix(@Nullable DomElement domElement, @NotNull Map<String, String> vars) {
    if (domElement != null) {
      XmlTag tag = domElement.getXmlTag();
      if (tag != null) {
        String namespacePrefix = tag.getNamespacePrefix();
        if (!StringUtil.isEmptyOrSpaces(namespacePrefix)) {
          vars.put(NAMESPACE_PREFIX_VAR, namespacePrefix + ":");
        }
      }
    }
  }

  protected void runTemplate(final Editor editor, final PsiFile file, final T t, Map<String, String> predefinedVars) {
    DomTemplateRunner.getInstance(file.getProject()).runTemplate(t, myMappingId, editor, predefinedVars);
  }

  @Override
  protected abstract DomElement getParentDomElement(final Project project, final Editor editor, final PsiFile file);

  @Override
  @SuppressWarnings({"unchecked"})
  protected void doNavigate(final DomElementNavigationProvider navigateProvider, final DomElement copy) {
    final DomElement element = getElementToNavigate((T)copy);
    if (element != null) {
      super.doNavigate(navigateProvider, element);
    }
  }

  @Nullable
  protected DomElement getElementToNavigate(final T t) {
    return t;
  }

  protected static String getDescription(final Class<? extends DomElement> aClass) {
    return StringUtil.join(Arrays.asList(NameUtilCore.nameToWords(aClass.getSimpleName())), " ");
  }

  @Nullable
  public String getMappingId() {
    return myMappingId;
  }
}
