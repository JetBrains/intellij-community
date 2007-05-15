package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:30:38
 * To change this template use Options | File Templates.
 */
public class JavaClassReferenceProvider extends GenericReferenceProvider implements CustomizableReferenceProvider {
  public static final ReferenceType CLASS_REFERENCE_TYPE = new ReferenceType(ReferenceType.JAVA_CLASS);

  public static final CustomizationKey<Boolean> RESOLVE_QUALIFIED_CLASS_NAME =
    new CustomizationKey<Boolean>(PsiBundle.message("qualified.resolve.class.reference.provider.option"));
  public static final CustomizationKey<String[]> EXTEND_CLASS_NAMES = new CustomizationKey<String[]>("EXTEND_CLASS_NAMES");
  public static final CustomizationKey<Boolean> INSTANTIATABLE = new CustomizationKey<Boolean>("INSTANTIATABLE");
  public static final CustomizationKey<Boolean> RESOLVE_ONLY_CLASSES = new CustomizationKey<Boolean>("RESOLVE_ONLY_CLASSES");
  public static final CustomizationKey<Boolean> JVM_FORMAT = new CustomizationKey<Boolean>("JVM_FORMAT");
  public static final CustomizationKey<String> DEFAULT_PACKAGE = new CustomizationKey<String>("DEFAULT_PACKAGE");

  @Nullable private Map<CustomizationKey, Object> myOptions;
  private boolean mySoft;
  @Nullable private final GlobalSearchScope myScope;

  public JavaClassReferenceProvider(@NonNls String extendClassName, boolean instantiatable, GlobalSearchScope scope) {
    this(new String[]{extendClassName}, instantiatable, false, scope);
  }

  public JavaClassReferenceProvider(@NonNls String[] extendClassNames, boolean instantiatable) {
    this(extendClassNames, instantiatable, false, null);
  }

  public JavaClassReferenceProvider(@NonNls String[] extendClassNames, boolean instantiatable, boolean jvmFormat, GlobalSearchScope scope) {
    myOptions = new THashMap<CustomizationKey, Object>();
    EXTEND_CLASS_NAMES.putValue(myOptions, extendClassNames);
    INSTANTIATABLE.putValue(myOptions, instantiatable);
    RESOLVE_ONLY_CLASSES.putValue(myOptions, Boolean.TRUE);
    JVM_FORMAT.putValue(myOptions, jvmFormat);
    myScope = scope;
  }

  public JavaClassReferenceProvider(boolean jvmFormat) {
    this(ArrayUtil.EMPTY_STRING_ARRAY, false, jvmFormat, null);
  }

  public JavaClassReferenceProvider(@NotNull String extendClassName) {
    this(extendClassName, true, null);
  }

  public JavaClassReferenceProvider() {
    myScope = null;
  }

  public JavaClassReferenceProvider(GlobalSearchScope scope) {
    myScope = scope;
  }

  @Nullable
  public GlobalSearchScope getScope() {
    return myScope;
  }

  public boolean isSoft() {
    return mySoft;
  }

  public void setSoft(final boolean soft) {
    mySoft = soft;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    return getReferencesByElement(element, CLASS_REFERENCE_TYPE);
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    String text = element.getText();

    if (element instanceof XmlAttributeValue) {
      final String valueString = ((XmlAttributeValue)element).getValue();
      int startOffset = StringUtil.startsWithChar(text, '"') || StringUtil.startsWithChar(text, '\'') ? 1 : 0;
      return getReferencesByString(valueString, element, type, startOffset);
    }
    else if (element instanceof XmlTag) {
      final XmlTagValue value = ((XmlTag)element).getValue();

      text = value.getText();
      final String trimmedText = text.trim();

      return getReferencesByString(trimmedText, element, type,
                                   value.getTextRange().getStartOffset() + text.indexOf(trimmedText) - element.getTextOffset());
    }

    return getReferencesByString(text, element, type, 0);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return new JavaClassReferenceSet(str, position, offsetInPosition, type, false, this).getAllReferences();
  }

  private static final SoftReference<List<PsiElement>> NULL_REFERENCE = new SoftReference<List<PsiElement>>(null);
  private SoftReference<List<PsiElement>> myDefaultPackageContent = NULL_REFERENCE;
  private Runnable myPackagesEraser = null;

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
    final ElementClassHint hint = processor.getHint(ElementClassHint.class);
    if (position == null) return;
    if (hint == null || hint.shouldProcess(PsiPackage.class) || hint.shouldProcess(PsiClass.class)) {
      final List<PsiElement> cachedPackages = getDefaultPackages(position);
      for (final PsiElement psiPackage : cachedPackages) {
        if (!processor.execute(psiPackage, PsiSubstitutor.EMPTY)) return;
      }
    }
  }

  protected List<PsiElement> getDefaultPackages(PsiElement position) {
    List<PsiElement> cachedPackages = myDefaultPackageContent.get();
    if (cachedPackages == null) {
      final List<PsiElement> psiPackages = new ArrayList<PsiElement>();
      final PsiManager manager = position.getManager();
      final BaseScopeProcessor processor = new BaseScopeProcessor() {
        public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
          psiPackages.add(element);
          return true;
        }
      };
      final String defPackageName = DEFAULT_PACKAGE.getValue(myOptions);
      if (StringUtil.isNotEmpty(defPackageName)) {
        final PsiPackage defaultPackage = manager.findPackage(defPackageName);
        if (defaultPackage != null) {
          defaultPackage.processDeclarations(processor, PsiSubstitutor.EMPTY, position, position);
        }
      }
      final PsiPackage rootPackage = manager.findPackage("");
      if (rootPackage != null) {
        rootPackage.processDeclarations(processor, PsiSubstitutor.EMPTY, position, position);
      }
      if (myPackagesEraser == null) {
        myPackagesEraser = new Runnable() {
          public void run() {
            myDefaultPackageContent = NULL_REFERENCE;
          }
        };
      }
      cachedPackages = psiPackages;
      ((PsiManagerEx)manager).registerWeakRunnableToRunOnChange(myPackagesEraser);
      myDefaultPackageContent = new SoftReference<List<PsiElement>>(cachedPackages);
    }
    return cachedPackages;
  }

  public void setOptions(@Nullable Map<CustomizationKey, Object> options) {
    myOptions = options;
  }

  @Nullable
  public Map<CustomizationKey, Object> getOptions() {
    return myOptions;
  }

}
