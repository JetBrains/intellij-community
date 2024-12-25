// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.HashMap;
import java.util.Map;


public final class UastAnonymousClassUtil {

  private static final @NotNull Key<ParameterizedCachedValue<Map<PsiClass, String>, UClass>>
    MY_ANONYMOUS_CLASSES_NAMES_PROVIDER_KEY =
    Key.create("org.jetbrains.uast.java.UastAnonymousClassUtil.MY_ANONYMOUS_CLASSES_NAMES_PROVIDER_KEY");

  private static final @NotNull MyAnonymousClassNameProvider OUR_ANONYMOUS_CLASS_NAME_PROVIDER = new MyAnonymousClassNameProvider();

  private UastAnonymousClassUtil() { }

  public static @Nullable String getName(@NotNull PsiAnonymousClass cls) {
    final var parentClass = UastContextKt.getUastParentOfType(cls, UClass.class);
    if (parentClass == null) {
      return null;
    }
    var value = parentClass.getUserData(MY_ANONYMOUS_CLASSES_NAMES_PROVIDER_KEY);
    if (value == null) {
      value = CachedValuesManager.getManager(cls.getProject())
        .createParameterizedCachedValue(OUR_ANONYMOUS_CLASS_NAME_PROVIDER, false);
      parentClass.putUserData(MY_ANONYMOUS_CLASSES_NAMES_PROVIDER_KEY, value);
    }
    return value.getValue(parentClass).get(cls);
  }


  private static final class MyAnonymousClassNameProvider
    implements ParameterizedCachedValueProvider<Map<PsiClass, String>, UClass> {

    @Override
    public CachedValueProvider.Result<Map<PsiClass, String>> compute(UClass parentClass) {
      final var namesCollector = new MyAnonymousClassesNamesCollectingVisitor();
      parentClass.accept(namesCollector);
      return CachedValueProvider.Result.create(namesCollector.getCollectedNames(), parentClass);
    }
  }


  private static final class MyAnonymousClassesNamesCollectingVisitor extends AbstractUastNonRecursiveVisitor {

    @SuppressWarnings("unchecked")
    private static final Class<? extends UElement> @NotNull [] OUR_EXPECTED_UAST_TYPES = new Class[]{
      UClass.class
    };

    private final @NotNull Map<PsiClass, String> myCollectedNames = new HashMap<>();

    private int myCounter = 0;

    public @NotNull PsiElementVisitor asPsiVisitor() {
      return new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          final var uElement = UastContextKt.toUElementOfExpectedTypes(element, OUR_EXPECTED_UAST_TYPES);
          if (uElement != null) {
            uElement.accept(MyAnonymousClassesNamesCollectingVisitor.this);
          }
          super.visitElement(element);
        }
      };
    }

    private @NotNull Map<PsiClass, String> getCollectedNames() {
      return myCollectedNames;
    }

    @Override
    public boolean visitClass(@NotNull UClass node) {
      if (!(node instanceof UAnonymousClass)) return false;
      myCounter++;
      myCollectedNames.put(node.getJavaPsi(), "$" + myCounter);
      return false;
    }
  }
}
