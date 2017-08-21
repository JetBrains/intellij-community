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
package com.intellij.uiDesigner.binding;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class FormReferenceProvider extends PsiReferenceProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.binding.FormReferenceProvider");
  private static class CachedFormData {
    PsiReference[] myReferences;
    Map<String, Pair<PsiType, TextRange>> myFieldNameToTypeMap;

    public CachedFormData(final PsiReference[] refs, final Map<String, Pair<PsiType, TextRange>> map) {
      myReferences = refs;
      myFieldNameToTypeMap = map;
    }
  }

  private static final Key<CachedValue<CachedFormData>> CACHED_DATA = Key.create("Cached form reference");

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
    if (element instanceof PsiPlainTextFile) {
      PsiPlainTextFile plainTextFile = (PsiPlainTextFile) element;
      if (plainTextFile.getFileType().equals(GuiFormFileType.INSTANCE)) {
        return getCachedData(plainTextFile).myReferences;
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @Nullable
  public static PsiFile getFormFile(PsiField field) {
    PsiReference ref = getFormReference(field);
    if (ref != null) {
      return ref.getElement().getContainingFile();
    }
    return null;
  }

  @Nullable
  public static PsiReference getFormReference(PsiField field) {
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && containingClass.getQualifiedName() != null) {
      final List<PsiFile> forms = FormClassIndex.findFormsBoundToClass(containingClass.getProject(), containingClass);
      for (PsiFile formFile : forms) {
        final PsiReference[] refs = formFile.getReferences();
        for (final PsiReference ref : refs) {
          if (ref.isReferenceTo(field)) {
            return ref;
          }
        }
      }
    }
    return null;
  }

  public static @Nullable
  PsiType getGUIComponentType(final PsiPlainTextFile file, String fieldName) {
    final Map<String, Pair<PsiType, TextRange>> fieldNameToTypeMap = getCachedData(file).myFieldNameToTypeMap;
    final Pair<PsiType, TextRange> typeRangePair = fieldNameToTypeMap.get(fieldName);
    return typeRangePair != null? typeRangePair.getFirst() : null;
  }

  public static void setGUIComponentType(PsiPlainTextFile file, String fieldName, String typeText) {
    final Map<String, Pair<PsiType, TextRange>> fieldNameToTypeMap = getCachedData(file).myFieldNameToTypeMap;
    final Pair<PsiType, TextRange> typeRangePair = fieldNameToTypeMap.get(fieldName);
    if (typeRangePair != null) {
      final TextRange range = typeRangePair.getSecond();
      if (range != null) {
        PsiDocumentManager.getInstance(file.getProject()).getDocument(file).replaceString(range.getStartOffset(), range.getEndOffset(), typeText);
      }
    }
  }

  private static void processReferences(final PsiPlainTextFile file, final PsiReferenceProcessor processor) {
    final Project project = file.getProject();
    final XmlTag rootTag = ReadAction.compute(() -> {
      final XmlFile xmlFile = (XmlFile)PsiFileFactory.getInstance(project)
        .createFileFromText("a.xml", XmlFileType.INSTANCE, file.getViewProvider().getContents());
      return xmlFile.getRootTag();
    });

    if (rootTag == null || !Utils.FORM_NAMESPACE.equals(rootTag.getNamespace())) {
      return;
    }

    @NonNls final String name = rootTag.getName();
    if (!"form".equals(name)){
      return;
    }

    PsiReference classReference = null;

    final XmlAttribute classToBind = rootTag.getAttribute("bind-to-class", null);
    if (classToBind != null) {
      // reference to class
      final XmlAttributeValue valueElement = classToBind.getValueElement();
      if (valueElement == null) {
        return;
      }
      final String className = valueElement.getValue().replace('$','.');
      final PsiReference[] referencesByString = new JavaClassReferenceProvider().getReferencesByString(className, file, valueElement.getTextRange().getStartOffset() + 1);
      if(referencesByString.length < 1){
        // There are no references there
        return;
      }
      for (PsiReference aReferencesByString : referencesByString) {
        processor.execute(aReferencesByString);
      }
      classReference = referencesByString[referencesByString.length - 1];
    }

    final PsiReference finalClassReference = classReference;
    ApplicationManager.getApplication().runReadAction(() -> processReferences(rootTag, finalClassReference, file, processor));
  }

  private static TextRange getValueRange(final XmlAttribute classToBind) {
    final XmlAttributeValue valueElement = classToBind.getValueElement();
    final TextRange textRange = valueElement.getTextRange();
    return new TextRange(textRange.getStartOffset() + 1, textRange.getEndOffset() - 1); // skip " "
  }

  private static void processReferences(final XmlTag tag,
                                        final PsiReference classReference,
                                        final PsiPlainTextFile file,
                                        final PsiReferenceProcessor processor) {
    final XmlAttribute clsAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_CLASS, null);
    final String classNameStr = clsAttribute != null? clsAttribute.getValue().replace('$','.') : null;
    // field
    {
      final XmlAttribute bindingAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_BINDING, null);
      if (bindingAttribute != null && classReference != null) {
        final XmlAttribute customCreateAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_CUSTOM_CREATE, null);
        boolean customCreate = (customCreateAttribute != null && Boolean.parseBoolean(customCreateAttribute.getValue()));
        final TextRange nameRange = clsAttribute != null ? getValueRange(clsAttribute) : null;
        processor.execute(new FieldFormReference(file, classReference, getValueRange(bindingAttribute), classNameStr, nameRange, customCreate));
      }
      final XmlAttribute titleBundleAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_TITLE_RESOURCE_BUNDLE, null);
      final XmlAttribute titleKeyAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_TITLE_KEY, null);
      if (titleBundleAttribute != null && titleKeyAttribute != null) {
        processResourceBundleFileReferences(file, processor, titleBundleAttribute);
        processor.execute(new ResourceBundleKeyReference(file, titleBundleAttribute.getValue(), getValueRange(titleKeyAttribute)));
      }

      final XmlAttribute bundleAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_RESOURCE_BUNDLE, null);
      final XmlAttribute keyAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_KEY, null);
      if (bundleAttribute != null && keyAttribute != null) {
        processResourceBundleFileReferences(file, processor, bundleAttribute);
        processor.execute(new ResourceBundleKeyReference(file, bundleAttribute.getValue(), getValueRange(keyAttribute)));
      }

      processNestedFormReference(tag, processor, file);
      processButtonGroupReference(tag, processor, file, classReference);
    }

    // component class
    {
      if (clsAttribute != null) {
        final JavaClassReferenceProvider provider = new JavaClassReferenceProvider();
        final PsiReference[] referencesByString = provider.getReferencesByString(classNameStr, file, clsAttribute.getValueElement().getTextRange().getStartOffset() + 1);
        if(referencesByString.length < 1){
          // There are no references there
          return;
        }
        for (PsiReference aReferencesByString : referencesByString) {
          processor.execute(aReferencesByString);
        }
      }
    }

    // property references
    XmlTag parentTag = tag.getParentTag();
    if (parentTag != null && parentTag.getName().equals(UIFormXmlConstants.ELEMENT_PROPERTIES)) {
      XmlTag componentTag = parentTag.getParentTag();
      if (componentTag != null) {
        String className = componentTag.getAttributeValue(UIFormXmlConstants.ATTRIBUTE_CLASS, Utils.FORM_NAMESPACE);
        if (className != null) {
          processPropertyReference(tag, processor, file, className.replace('$', '.'));
        }
      }
    }

    final XmlTag[] subtags = tag.getSubTags();
    for (XmlTag subtag : subtags) {
      processReferences(subtag, classReference, file, processor);
    }
  }

  private static void processResourceBundleFileReferences(final PsiPlainTextFile file,
                                                          final PsiReferenceProcessor processor,
                                                          final XmlAttribute titleBundleAttribute) {
    processPackageReferences(file, processor, titleBundleAttribute);
    processor.execute(new ResourceBundleFileReference(file, getValueRange(titleBundleAttribute)));
  }

  private static void processPackageReferences(final PsiPlainTextFile file,
                                               final PsiReferenceProcessor processor,
                                               final XmlAttribute attribute) {
    final TextRange valueRange = getValueRange(attribute);
    final String value = attribute.getValue();
    int pos=-1;
    while(true) {
      pos = value.indexOf('/', pos+1);
      if (pos < 0) {
        break;
      }
      processor.execute(new FormPackageReference(file, new TextRange(valueRange.getStartOffset(), valueRange.getStartOffset() + pos)));
    }
  }

  private static void processNestedFormReference(final XmlTag tag, final PsiReferenceProcessor processor, final PsiPlainTextFile file) {
    final XmlAttribute formFileAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_FORM_FILE, null);
    if (formFileAttribute != null) {
      processPackageReferences(file, processor, formFileAttribute);
      processor.execute(new ResourceFileReference(file, getValueRange(formFileAttribute)));
    }
  }

  private static void processButtonGroupReference(final XmlTag tag, final PsiReferenceProcessor processor, final PsiPlainTextFile file,
                                                  final PsiReference classReference) {
    final XmlAttribute boundAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_BOUND, null);
    final XmlAttribute nameAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_NAME, null);
    if (boundAttribute != null && Boolean.parseBoolean(boundAttribute.getValue()) && nameAttribute != null) {
      processor.execute(new FieldFormReference(file, classReference, getValueRange(nameAttribute), null, null, false));
    }
  }

  private static void processPropertyReference(final XmlTag tag, final PsiReferenceProcessor processor, final PsiPlainTextFile file,
                                               final String className) {
    final XmlAttribute valueAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, null);
    if (valueAttribute != null) {
      PsiReference reference = ReadAction.compute(() -> {
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(file.getProject());
        final Module module = ModuleUtil.findModuleForPsiElement(file);
        if (module == null) return null;
        final GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(false);
        PsiClass psiClass = psiFacade.findClass(className, scope);
        if (psiClass != null) {
          PsiMethod getter = PropertyUtil.findPropertyGetter(psiClass, tag.getName(), false, true);
          if (getter != null) {
            final PsiType returnType = getter.getReturnType();
            if (returnType instanceof PsiClassType) {
              PsiClassType propClassType = (PsiClassType)returnType;
              PsiClass propClass = propClassType.resolve();
              if (propClass != null) {
                if (propClass.isEnum()) {
                  return new FormEnumConstantReference(file, getValueRange(valueAttribute), propClassType);
                }
                PsiClass iconClass = psiFacade.findClass("javax.swing.Icon", scope);
                if (iconClass != null && InheritanceUtil.isInheritorOrSelf(propClass, iconClass, true)) {
                  return new ResourceFileReference(file, getValueRange(valueAttribute));
                }
              }
            }
          }
        }
        return null;
      });
      if (reference != null) {
        if (reference instanceof ResourceFileReference) {
          processPackageReferences(file, processor, valueAttribute);
        }
        processor.execute(reference);
      }
    }
  }

  @Nullable
  public static String getBundleName(final PropertiesFile propertiesFile) {
    final PsiDirectory directory = propertiesFile.getParent();
    if (directory == null) {
      return null;
    }
    final String packageName;
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage == null) {
      packageName = "";
    }
    else {
      packageName = aPackage.getQualifiedName();
    }

    //noinspection NonConstantStringShouldBeStringBuffer
    String bundleName = propertiesFile.getResourceBundle().getBaseName();

    if (packageName.length() > 0) {
      bundleName = packageName + '.' + bundleName;
    }
    bundleName = bundleName.replace('.', '/');
    return bundleName;
  }

  private static CachedFormData getCachedData(final PsiPlainTextFile element) {
    CachedValue<CachedFormData> data = element.getUserData(CACHED_DATA);

    if(data == null) {
      data = CachedValuesManager.getManager(element.getProject()).createCachedValue(new CachedValueProvider<CachedFormData>() {
        final Map<String, Pair<PsiType, TextRange>> map = new HashMap<>();
        public Result<CachedFormData> compute() {
          final PsiReferenceProcessor.CollectElements processor = new PsiReferenceProcessor.CollectElements() {
            public boolean execute(PsiReference ref) {
              if (ref instanceof FieldFormReference) {
                final FieldFormReference fieldRef = ((FieldFormReference)ref);
                final String componentClassName = fieldRef.getComponentClassName();
                if (componentClassName != null) {
                  final PsiClassType type = JavaPsiFacade.getInstance(element.getProject()).getElementFactory()
                    .createTypeByFQClassName(componentClassName, element.getResolveScope());
                  map.put(fieldRef.getRangeText(), new Pair<>(type, fieldRef.getComponentClassNameTextRange()));
                }
              }
              return super.execute(ref);
            }
          };
          processReferences(element, processor);
          final PsiReference[] refs = processor.toArray(PsiReference.EMPTY_ARRAY);
          return new Result<>(new CachedFormData(refs, map), element);
        }
      }, false);
      element.putUserData(CACHED_DATA, data);
    }
    return data.getValue();
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "FormReferenceProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
