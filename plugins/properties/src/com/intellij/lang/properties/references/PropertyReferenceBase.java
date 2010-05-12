/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.PropertiesFilesManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public abstract class PropertyReferenceBase implements PsiPolyVariantReference, EmptyResolveMessageProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.references.PropertyReferenceBase");
  protected final String myKey;
  protected final PsiElement myElement;
  protected boolean mySoft;
  private final TextRange myTextRange;

  public PropertyReferenceBase(@NotNull String key, final boolean soft, @NotNull PsiElement element) {
    this(key, soft, element, ElementManipulators.getValueTextRange(element));
  }

  public PropertyReferenceBase(@NotNull String key, final boolean soft, @NotNull PsiElement element, TextRange range) {
    myKey = key;
    mySoft = soft;
    myElement = element;
    myTextRange = range;
  }

  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @NotNull
  protected String getKeyText() {
    return myKey;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PropertyReferenceBase other = (PropertyReferenceBase)o;

    return getElement() == other.getElement() && getKeyText().equals(other.getKeyText());
  }

  public int hashCode() {
    return getKeyText().hashCode();
  }

  @NotNull
  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return myTextRange;
  }

  @NotNull
  public String getCanonicalText() {
    return myKey;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    /*PsiElementFactory factory = JavaPsiFacade.getInstance(myElement.getProject()).getElementFactory();

    if (myElement instanceof PsiLiteralExpression) {
      PsiExpression newExpression = factory.createExpressionFromText("\"" + newElementName + "\"", myElement);
      return myElement.replace(newExpression);
    }
    else {*/
      ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(myElement);
      if (manipulator == null) {
        LOG.error("Cannot find manipulator for " + myElement + " of class " + myElement.getClass());
      }
      return manipulator.handleContentChange(myElement, getRangeInElement(), newElementName);
    /*}*/
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("not implemented");
  }

  public boolean isReferenceTo(PsiElement element) {
    if(!(element instanceof Property)) return false;
    for (ResolveResult result : multiResolve(false)) {
      final PsiElement el = result.getElement();
      if (el != null && el.isEquivalentTo(element)) return true;
    }
    return false;
  }

  protected void addKey(Object property, Set<Object> variants) {
    variants.add(property);
  }

  protected void addVariantsFromFile(final PropertiesFile propertiesFile, final Set<Object> variants) {
    if (propertiesFile == null) return;
    List<Property> properties = propertiesFile.getProperties();
    for (Property property : properties) {
      addKey(property, variants);
    }
  }

  protected void setSoft(final boolean soft) {
    mySoft = soft;
  }

  public boolean isSoft() {
    return mySoft;
  }

  public String getUnresolvedMessagePattern() {
    return PropertiesBundle.message("unresolved.property.key");
  }

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final String key = getKeyText();

    List<Property> properties;
    final List<PropertiesFile> propertiesFiles = getPropertiesFiles();
    if (propertiesFiles == null) {
      properties = PropertiesUtil.findPropertiesByKey(getElement().getProject(), key);
    }
    else {
      properties = new ArrayList<Property>();
      for (PropertiesFile propertiesFile : propertiesFiles) {
        properties.addAll(propertiesFile.findPropertiesByKey(key));
      }
    }
    // put default properties file first
    ContainerUtil.quickSort(properties, new Comparator<Property>() {
      public int compare(final Property o1, final Property o2) {
        String name1 = o1.getContainingFile().getName();
        String name2 = o2.getContainingFile().getName();
        return Comparing.compare(name1, name2);
      }
    });
    return PsiElementResolveResult.createResults(properties);
  }

  @Nullable
  protected abstract List<PropertiesFile> getPropertiesFiles();

  @NotNull
  public Object[] getVariants() {
    final Set<Object> variants = new THashSet<Object>(new TObjectHashingStrategy<Object>() {
      public int computeHashCode(final Object object) {
        if (object instanceof Property) {
          final String key = ((Property)object).getKey();
          return key == null ? 0 : key.hashCode();
        }
        else {
          return 0;
        }
      }

      public boolean equals(final Object o1, final Object o2) {
        return o1 instanceof Property && o2 instanceof Property &&
               Comparing.equal(((Property)o1).getKey(), ((Property)o2).getKey(), true);
      }
    });
    List<PropertiesFile> propertiesFileList = getPropertiesFiles();
    if (propertiesFileList == null) {
      final PsiManager psiManager = myElement.getManager();
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(psiManager.getProject()).getFileIndex();
      PropertiesFilesManager.getInstance(myElement.getProject()).processAllPropertiesFiles(new Processor<VirtualFile>() {
        public boolean process(VirtualFile file) {
          if (!file.isValid()) return true;
          if (!fileIndex.isInContent(file)) return true; //multiple opened projects
          PsiFile psiFile = psiManager.findFile(file);
          if (!(psiFile instanceof PropertiesFile)) return true;
          PropertiesFile propertiesFile = (PropertiesFile)psiFile;
          addVariantsFromFile(propertiesFile, variants);
          return true;
        }
      });
    }
    else {
      for (PropertiesFile propFile : propertiesFileList) {
        addVariantsFromFile(propFile, variants);
      }
    }
    return ArrayUtil.toObjectArray(variants);
  }
}
