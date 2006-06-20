package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.compiler.Utils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class FormReferenceProvider implements PsiReferenceProvider, ProjectComponent {
  private static class CachedFormData {
    PsiReference[] myReferences;
    Map<String, Pair<PsiType, TextRange>> myFieldNameToTypeMap;

    public CachedFormData(final PsiReference[] refs, final Map<String, Pair<PsiType, TextRange>> map) {
      myReferences = refs;
      myFieldNameToTypeMap = map;
    }
  }

  private static final Key<CachedValue<CachedFormData>> CACHED_DATA = Key.create("Cached form reference");

  public FormReferenceProvider(ReferenceProvidersRegistry registry) {
    registry.registerReferenceProvider(PsiPlainTextFile.class, this);
  }

  @NotNull
  public PsiReference[] getReferencesByElement(final PsiElement element) {
    if (element instanceof PsiPlainTextFile) {
      final PsiPlainTextFile plainTextFile = (PsiPlainTextFile) element;
      return getCachedData(plainTextFile).myReferences;
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
    final PsiSearchHelper searchHelper = field.getManager().getSearchHelper();
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && containingClass.getQualifiedName() != null) {
      final PsiFile[] forms = searchHelper.findFormsBoundToClass(containingClass.getQualifiedName());
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

  public static void setGUIComponentType(PsiPlainTextFile file, String fieldName, PsiType componentTypeToSet) {
    final Map<String, Pair<PsiType, TextRange>> fieldNameToTypeMap = getCachedData(file).myFieldNameToTypeMap;
    final Pair<PsiType, TextRange> typeRangePair = fieldNameToTypeMap.get(fieldName);
    if (typeRangePair != null) {
      final TextRange range = typeRangePair.getSecond();
      if (range != null) {
        PsiDocumentManager.getInstance(file.getProject()).getDocument(file).replaceString(range.getStartOffset(), range.getEndOffset(), componentTypeToSet.getCanonicalText());
      }
    }
  }

  private static void processReferences(final PsiPlainTextFile file, final PsiReferenceProcessor processor) {
    final PsiManager manager = file.getManager();

    final PsiElementFactory elementFactory = manager.getElementFactory();

    final PsiFile _f = elementFactory.createFileFromText("a.xml", file.getText());

    final XmlFile xmlFile = (XmlFile)_f;
    final XmlDocument document = xmlFile.getDocument();

    final XmlTag rootTag = document.getRootTag();

    if (rootTag == null || !Utils.FORM_NAMESPACE.equals(rootTag.getNamespace())) {
      return;
    }

    @NonNls final String name = rootTag.getName();
    if (!"form".equals(name)){
      return;
    }

    PsiReference classReference = null;

    final XmlAttribute classToBind = rootTag.getAttribute("bind-to-class", Utils.FORM_NAMESPACE);
    if (classToBind != null) {
      // reference to class
      final String className = classToBind.getValue().replace('$','.');
      final XmlAttributeValue valueElement = classToBind.getValueElement();
      final PsiReference[] referencesByString = new JavaClassReferenceProvider().getReferencesByString(className, file, new ReferenceType(ReferenceType.JAVA_CLASS), valueElement.getTextRange().getStartOffset() + 1);
      if(referencesByString.length < 1){
        // There are no references there
        return;
      }
      for (PsiReference aReferencesByString : referencesByString) {
        processor.execute(aReferencesByString);
      }
      classReference = referencesByString[referencesByString.length - 1];
    }

    processReferences(rootTag, classReference, file, processor);
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
    final XmlAttribute clsAttribute = tag.getAttribute("class", Utils.FORM_NAMESPACE);
    final String classNameStr = clsAttribute != null? clsAttribute.getValue().replace('$','.') : null;
    // field
    {
      final XmlAttribute bindingAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_BINDING, Utils.FORM_NAMESPACE);
      if (bindingAttribute != null && classReference != null) {
        final XmlAttribute customCreateAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_CUSTOM_CREATE, Utils.FORM_NAMESPACE);
        boolean customCreate = (customCreateAttribute != null && Boolean.parseBoolean(customCreateAttribute.getValue()));
        final TextRange nameRange = clsAttribute != null ? getValueRange(clsAttribute) : null;
        processor.execute(new FieldFormReference(file, classReference, getValueRange(bindingAttribute), classNameStr, nameRange, customCreate));
      }
      final XmlAttribute titleBundleAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_TITLE_RESOURCE_BUNDLE, Utils.FORM_NAMESPACE);
      final XmlAttribute titleKeyAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_TITLE_KEY, Utils.FORM_NAMESPACE);
      if (titleBundleAttribute != null && titleKeyAttribute != null) {
        processor.execute(new ResourceBundleFileReference(file, getValueRange(titleBundleAttribute)));
        processor.execute(new ResourceBundleKeyReference(file, titleBundleAttribute.getValue(), getValueRange(titleKeyAttribute)));
      }

      final XmlAttribute bundleAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_RESOURCE_BUNDLE, Utils.FORM_NAMESPACE);
      final XmlAttribute keyAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_KEY, Utils.FORM_NAMESPACE);
      if (bundleAttribute != null && keyAttribute != null) {
        processor.execute(new ResourceBundleFileReference(file, getValueRange(bundleAttribute)));
        processor.execute(new ResourceBundleKeyReference(file, bundleAttribute.getValue(), getValueRange(keyAttribute)));
      }

      processNestedFormReference(tag, processor, file);
      processButtonGroupReference(tag, processor, file, classReference);
    }

    // component class
    {
      if (clsAttribute != null) {
        final JavaClassReferenceProvider provider = new JavaClassReferenceProvider();
        final PsiReference[] referencesByString = provider.getReferencesByString(classNameStr, file, new ReferenceType(ReferenceType.JAVA_CLASS), clsAttribute.getValueElement().getTextRange().getStartOffset() + 1);
        if(referencesByString.length < 1){
          // There are no references there
          return;
        }
        for (PsiReference aReferencesByString : referencesByString) {
          processor.execute(aReferencesByString);
        }
      }
    }

    final XmlTag[] subtags = tag.getSubTags();
    for (XmlTag subtag : subtags) {
      processReferences(subtag, classReference, file, processor);
    }
  }

  private static void processNestedFormReference(final XmlTag tag, final PsiReferenceProcessor processor, final PsiPlainTextFile file) {
    final XmlAttribute formFileAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_FORM_FILE, Utils.FORM_NAMESPACE);
    if (formFileAttribute != null) {
      processor.execute(new NestedFormFileReference(file, getValueRange(formFileAttribute)));
    }
  }

  private static void processButtonGroupReference(final XmlTag tag, final PsiReferenceProcessor processor, final PsiPlainTextFile file,
                                                  final PsiReference classReference) {
    final XmlAttribute boundAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_BOUND, Utils.FORM_NAMESPACE);
    final XmlAttribute nameAttribute = tag.getAttribute(UIFormXmlConstants.ATTRIBUTE_NAME, Utils.FORM_NAMESPACE);
    if (boundAttribute != null && Boolean.parseBoolean(boundAttribute.getValue()) && nameAttribute != null) {
      processor.execute(new FieldFormReference(file, classReference, getValueRange(nameAttribute), null, null, false));
    }
  }

  @Nullable
  public static String getBundleName(final PropertiesFile propertiesFile) {
    final PsiDirectory directory = propertiesFile.getContainingDirectory();
    if (directory == null) {
      return null;
    }
    final String packageName;
    final PsiPackage aPackage = directory.getPackage();
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
      data = element.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<CachedFormData>() {
        final Map<String, Pair<PsiType, TextRange>> map = new HashMap<String, Pair<PsiType, TextRange>>();
        public Result<CachedFormData> compute() {
          final PsiReferenceProcessor.CollectElements processor = new PsiReferenceProcessor.CollectElements() {
            public boolean execute(PsiReference ref) {
              if (ref instanceof FieldFormReference) {
                final FieldFormReference fieldRef = ((FieldFormReference)ref);
                final String componentClassName = fieldRef.getComponentClassName();
                if (componentClassName != null) {
                  final PsiClassType type = element.getManager().getElementFactory().createTypeByFQClassName(componentClassName, element.getResolveScope());
                  map.put(fieldRef.getRangeText(), new Pair<PsiType, TextRange>(type, fieldRef.getComponentClassNameTextRange()));
                }
              }
              return super.execute(ref);
            }
          };
          processReferences(element, processor);
          final PsiReference[] refs = processor.toArray(PsiReference.EMPTY_ARRAY);
          return new Result<CachedFormData>(new CachedFormData(refs, map), element);
        }
      }, false);
      element.putUserData(CACHED_DATA, data);
    }
    return data.getValue();
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return PsiReference.EMPTY_ARRAY;
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return PsiReference.EMPTY_ARRAY;
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
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
