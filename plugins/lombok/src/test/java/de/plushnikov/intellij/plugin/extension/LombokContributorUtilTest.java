// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.extension;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class LombokContributorUtilTest extends AbstractLombokLightCodeInsightTestCase {

  public void testDirectFieldGetterAnnotations() {
    PsiClass psiClass = configureClass("DirectGetters", """
      class DirectGetters {
        @lombok.Getter private int value;
        @lombok.Getter(lombok.AccessLevel.NONE) private int hidden;
        @lombok.Getter private static int staticValue;
        @lombok.Getter private int $dollar;
        private String valueSource = "value";
        @lombok.Getter(lazy = true) private final String lazyValue = valueSource;
        @lombok.Getter(lazy = true) private String invalidLazyValue = "value";
      }
      """);

    assertGetterGenerated(psiClass, "value");
    assertGetterNotGenerated(psiClass, "hidden");
    assertGetterGenerated(psiClass, "staticValue");
    assertGetterGenerated(psiClass, "$dollar");
    assertGetterGenerated(psiClass, "lazyValue");
    assertGetterNotGenerated(psiClass, "invalidLazyValue");
  }

  public void testClassGetterAnnotations() {
    PsiJavaFile psiFile = configureFile("ClassGetters.java", """
      @lombok.Getter
      class ClassGetters {
        private int value;
        @lombok.Getter(lombok.AccessLevel.NONE) private int hidden;
        private static int staticValue;
        private int $dollar;
        private int existing;

        int getExisting() {
          return 0;
        }
      }

      @lombok.Getter(lombok.AccessLevel.NONE)
      class SuppressedClassGetter {
        private int value;
      }
      """);

    PsiClass classGetters = findClass(psiFile, "ClassGetters");
    assertGetterGenerated(classGetters, "value");
    assertGetterNotGenerated(classGetters, "hidden");
    assertGetterNotGenerated(classGetters, "staticValue");
    assertGetterNotGenerated(classGetters, "$dollar");
    assertGetterNotGenerated(classGetters, "existing");

    assertGetterNotGenerated(findClass(psiFile, "SuppressedClassGetter"), "value");
  }

  public void testDataAndValueGetterAnnotations() {
    PsiJavaFile psiFile = configureFile("DataAndValueGetters.java", """
      @lombok.Data
      class DataGetters {
        private int value;
        @lombok.Getter(lombok.AccessLevel.NONE) private int hidden;
        private static int staticValue;
      }

      @lombok.Data
      @lombok.Getter(lombok.AccessLevel.NONE)
      class SuppressedDataGetters {
        private int value;
      }

      @lombok.Value
      class ValueGetters {
        int value;
      }
      """);

    PsiClass dataGetters = findClass(psiFile, "DataGetters");
    assertGetterGenerated(dataGetters, "value");
    assertGetterNotGenerated(dataGetters, "hidden");
    assertGetterNotGenerated(dataGetters, "staticValue");

    assertGetterNotGenerated(findClass(psiFile, "SuppressedDataGetters"), "value");
    assertGetterGenerated(findClass(psiFile, "ValueGetters"), "value");
  }

  public void testAccessorPrefixRejection() {
    PsiClass psiClass = configureClass("PrefixedGetters", """
      @lombok.Getter
      @lombok.experimental.Accessors(prefix = "m")
      class PrefixedGetters {
        private int value;
        private int mValue;
      }
      """);

    assertGetterNotGenerated(psiClass, "value");
    assertGetterGenerated(psiClass, "mValue");
  }

  public void testDirectFieldSetterAnnotations() {
    PsiClass psiClass = configureClass("DirectSetters", """
      class DirectSetters {
        @lombok.Setter private int value;
        @lombok.Setter(lombok.AccessLevel.NONE) private int hidden;
        @lombok.Setter private final int finalValue = value;
        @lombok.Setter private static int staticValue;
        @lombok.Setter private int $dollar;
        @lombok.Setter private int existing;

        void setExisting(int existing) {
        }
      }
      """);

    assertSetterGenerated(psiClass, "value");
    assertSetterNotGenerated(psiClass, "hidden");
    assertSetterNotGenerated(psiClass, "finalValue");
    assertSetterGenerated(psiClass, "staticValue");
    assertSetterGenerated(psiClass, "$dollar");
    assertSetterNotGenerated(psiClass, "existing");
  }

  public void testClassSetterAnnotations() {
    PsiJavaFile psiFile = configureFile("ClassSetters.java", """
      @lombok.Setter
      class ClassSetters {
        private int value;
        @lombok.Setter(lombok.AccessLevel.NONE) private int hidden;
        private final int finalValue = value;
        private static int staticValue;
        private int $dollar;
        private int existing;

        void setExisting(int existing) {
        }
      }

      @lombok.Setter(lombok.AccessLevel.NONE)
      class SuppressedClassSetter {
        private int value;
      }
      """);

    PsiClass classSetters = findClass(psiFile, "ClassSetters");
    assertSetterGenerated(classSetters, "value");
    assertSetterNotGenerated(classSetters, "hidden");
    assertSetterNotGenerated(classSetters, "finalValue");
    assertSetterNotGenerated(classSetters, "staticValue");
    assertSetterNotGenerated(classSetters, "$dollar");
    assertSetterNotGenerated(classSetters, "existing");

    assertSetterNotGenerated(findClass(psiFile, "SuppressedClassSetter"), "value");
  }

  public void testDataAndValueSetterAnnotations() {
    PsiJavaFile psiFile = configureFile("DataAndValueSetters.java", """
      @lombok.Data
      class DataSetters {
        private int value;
        @lombok.Setter(lombok.AccessLevel.NONE) private int hidden;
        private final int finalValue = value;
        private static int staticValue;
      }

      @lombok.Data
      @lombok.Setter(lombok.AccessLevel.NONE)
      class SuppressedDataSetters {
        private int value;
      }

      @lombok.Value
      class ValueSetters {
        int value;
      }
      """);

    PsiClass dataSetters = findClass(psiFile, "DataSetters");
    assertSetterGenerated(dataSetters, "value");
    assertSetterNotGenerated(dataSetters, "hidden");
    assertSetterNotGenerated(dataSetters, "finalValue");
    assertSetterNotGenerated(dataSetters, "staticValue");

    assertSetterNotGenerated(findClass(psiFile, "SuppressedDataSetters"), "value");
    assertSetterNotGenerated(findClass(psiFile, "ValueSetters"), "value");
  }

  public void testSetterAccessorPrefixRejection() {
    PsiClass psiClass = configureClass("PrefixedSetters", """
      @lombok.Setter
      @lombok.experimental.Accessors(prefix = "m")
      class PrefixedSetters {
        private int value;
        private int mValue;
      }
      """);

    assertSetterNotGenerated(psiClass, "value");
    assertSetterGenerated(psiClass, "mValue");
  }

  private PsiClass configureClass(@NotNull String className, @NotNull @Language("JAVA") String text) {
    return findClass(configureFile(className + ".java", text), className);
  }

  private PsiJavaFile configureFile(@NotNull String fileName, @NotNull @Language("JAVA") String text) {
    return (PsiJavaFile)myFixture.configureByText(fileName, text);
  }

  private static PsiClass findClass(@NotNull PsiJavaFile psiFile, @NotNull String className) {
    for (PsiClass psiClass : psiFile.getClasses()) {
      if (className.equals(psiClass.getName())) {
        return psiClass;
      }
    }
    fail("Class not found: " + className);
    return null;
  }

  private static void assertGetterGenerated(@NotNull PsiClass psiClass, @NotNull String fieldName) {
    assertGetterGenerated(psiClass, fieldName, true);
  }

  private static void assertGetterNotGenerated(@NotNull PsiClass psiClass, @NotNull String fieldName) {
    assertGetterGenerated(psiClass, fieldName, false);
  }

  private static void assertGetterGenerated(@NotNull PsiClass psiClass, @NotNull String fieldName, boolean expected) {
    PsiField field = psiClass.findFieldByName(fieldName, false);
    assertNotNull(fieldName, field);
    assertEquals(fieldName, expected, LombokContributorUtil.isGetterContributedFor(field));
  }

  private static void assertSetterGenerated(@NotNull PsiClass psiClass, @NotNull String fieldName) {
    assertSetterGenerated(psiClass, fieldName, true);
  }

  private static void assertSetterNotGenerated(@NotNull PsiClass psiClass, @NotNull String fieldName) {
    assertSetterGenerated(psiClass, fieldName, false);
  }

  private static void assertSetterGenerated(@NotNull PsiClass psiClass, @NotNull String fieldName, boolean expected) {
    PsiField field = psiClass.findFieldByName(fieldName, false);
    assertNotNull(fieldName, field);
    assertEquals(fieldName, expected, LombokContributorUtil.isSetterContributedFor(field));
  }
}
