package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.java.stubs.impl.PsiModifierListStubImpl;
import com.intellij.psi.impl.source.PsiModifierListImpl;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Extension of PsiModifierList for providing of lombok visibility annotation support
 */
public class LombokPsiModifierListImpl extends PsiModifierListImpl {
  public LombokPsiModifierListImpl(PsiModifierListStub stub) {
    super(stub);
  }

  public LombokPsiModifierListImpl(ASTNode node) {
    super(node);
  }

  private static final Set<String> VISIBILITIES = new HashSet<String>(Arrays.asList(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE));
//
//  @Override
//  public PsiModifierListStub getStub() {
//    final PsiModifierListStub stub = super.getStub();
//    if (null != stub) {
//      return new PsiModifierListStubImpl(stub.getParentStub(), stub.getModifiersMask() | ModifierFlags.FINAL_MASK);
//    } else {
//      return null;
//    }
//  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    if (PsiModifier.FINAL.equals(name)) {
      return hasFinalProperty(name);
    } else if (VISIBILITIES.contains(name)) {
      return hasVisibilityProperty(name);
    } else {
      return defaultModifier(name);
    }
  }

  private boolean hasFinalProperty(String name) {
    return new LombokFinalPropertyCachedValueProvider(getParent(), defaultModifier(name)).compute().getValue();
//    return CachedValuesManager.getCachedValue(this, new LombokFinalPropertyCachedValueProvider(getParent(), defaultModifier(name)));
  }

  private boolean hasVisibilityProperty(String name) {
    return defaultModifier(name);
//    return PsiModifier.PRIVATE.equals(name) && new LombokPrivatePropertyCachedValueProvider(getParent(), defaultModifier(name)).compute().getValue();
  }

  private boolean defaultModifier(@NotNull String name) {
    return super.hasModifierProperty(name);
  }

  private static final Logger log = Logger.getInstance(LombokPsiModifierListImpl.class.getName());

  private static class LombokFinalPropertyCachedValueProvider implements CachedValueProvider<Boolean> {
    private final PsiElement parentElement;
    private final boolean defaultModifier;

    public LombokFinalPropertyCachedValueProvider(PsiElement parentElement, boolean defaultModifier) {
      this.parentElement = parentElement;
      this.defaultModifier = defaultModifier;
    }

    @Nullable
    @Override
    public Result<Boolean> compute() {
      log.info("Computed for " + ((PsiNamedElement) parentElement).getName());

      boolean result = defaultModifier;

      Collection<PsiElement> dependencies = new ArrayList<PsiElement>();
      dependencies.add(parentElement);

      final PsiClass parentClass = PsiTreeUtil.getParentOfType(parentElement, PsiClass.class, true);
      if (null != parentClass) {
        PsiAnnotation lombokAnnotation = PsiAnnotationSearchUtil.findAnnotation(parentClass, Value.class);
        boolean hasLombokFinalProperty = null != lombokAnnotation;
        if (!hasLombokFinalProperty) {
          lombokAnnotation = PsiAnnotationSearchUtil.findAnnotation(parentClass, FieldDefaults.class);
          hasLombokFinalProperty = null != lombokAnnotation && PsiAnnotationUtil.getBooleanAnnotationValue(lombokAnnotation, "makeFinal", false);
        }

        if (null != lombokAnnotation) {
          dependencies.add(lombokAnnotation);
        }

        if (hasLombokFinalProperty) {
          final PsiField parentField = PsiTreeUtil.getParentOfType(parentElement, PsiField.class, false);
          if (parentField != null) {
            final PsiAnnotation nonFinalAnnotation = PsiAnnotationSearchUtil.findAnnotation(parentField, NonFinal.class);
            if (null == nonFinalAnnotation) {
              result = true;
            } else {
              dependencies.add(nonFinalAnnotation);
            }
          }
        }
      }

      return Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    }
  }

  private static class LombokPrivatePropertyCachedValueProvider implements CachedValueProvider<Boolean> {
    private final PsiElement parentElement;
    private final boolean defaultModifier;

    public LombokPrivatePropertyCachedValueProvider(PsiElement parentElement, boolean defaultModifier) {
      this.parentElement = parentElement;
      this.defaultModifier = defaultModifier;
    }

    @Nullable
    @Override
    public Result<Boolean> compute() {
      log.info("Computed for " + ((PsiNamedElement) parentElement).getName());

      boolean result = defaultModifier;

      Collection<PsiElement> dependencies = new ArrayList<PsiElement>();
      dependencies.add(parentElement);

      final PsiClass parentClass = PsiTreeUtil.getParentOfType(parentElement, PsiClass.class, true);
      if (null != parentClass) {
        PsiAnnotation lombokAnnotation = PsiAnnotationSearchUtil.findAnnotation(parentClass, Value.class);
        boolean hasLombokFinalProperty = null != lombokAnnotation;
        if (!hasLombokFinalProperty) {
          lombokAnnotation = PsiAnnotationSearchUtil.findAnnotation(parentClass, FieldDefaults.class);
          hasLombokFinalProperty = null != lombokAnnotation && PsiAnnotationUtil.getBooleanAnnotationValue(lombokAnnotation, "makeFinal", false);
        }

        if (null != lombokAnnotation) {
          dependencies.add(lombokAnnotation);
        }

        if (hasLombokFinalProperty) {
          final PsiField parentField = PsiTreeUtil.getParentOfType(parentElement, PsiField.class, false);
          if (parentField != null) {
              result = true;
          }
        }
      }

      return Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    }
  }
}
