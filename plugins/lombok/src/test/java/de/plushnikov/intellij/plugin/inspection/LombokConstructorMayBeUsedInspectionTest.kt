package de.plushnikov.intellij.plugin.inspection

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase
import de.plushnikov.intellij.plugin.LombokBundle
import de.plushnikov.intellij.plugin.LombokClassNames
import de.plushnikov.intellij.plugin.util.PsiClassUtil

class LombokConstructorMayBeUsedInspectionTest : AbstractLombokLightCodeInsightTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(LombokConstructorMayBeUsedInspection())
  }

  fun testRequiredArgsPreferred() {
    checkConversion(
      """
      class Example {
        private final String name;
        private final int count;
        public <caret>Example(String name, int count) {
          this.name = name;
          this.count = count;
        }
      }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
    myFixture.checkResult(
      """
      import lombok.RequiredArgsConstructor;

      @RequiredArgsConstructor
      class Example {
        private final String name;
        private final int count;
      }
    """.trimIndent()
    )
  }

  fun testAllArgs() {
    checkConversion(
      """
      class Example {
        private final String name;
        private int count;
        public <caret>Example(String name, int count) {
          this.name = name;
          this.count = count;
        }
      }
    """, LombokClassNames.ALL_ARGS_CONSTRUCTOR
    )
  }

  fun testRequiredArgsWithOptionalFields() {
    checkConversion(
      """
      class Example {
        private static int total;
        private final int constant = 1;
        private final String name;
        private int count;
        public <caret>Example(String value) { name = (value); }
      }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  fun testInitializedMutableField() {
    checkConversion(
      """
      class Example {
        private int count = 1;
        public <caret>Example(int count) { this.count = count; }
      }
    """, LombokClassNames.ALL_ARGS_CONSTRUCTOR
    )
  }

  fun testExistingAllArgsConstructor() {
    checkConversion(
      """
        @lombok.AllArgsConstructor
        class Example {
          private final int a;
          private int b;

          public <caret>Example(int a) { this.a = a; }
        }
      """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  fun testExistingRequiredArgsConstructor() {
    checkConversion(
      """
        @lombok.RequiredArgsConstructor
        class Example {
          private final int a;
          private int b;

          public <caret>Example(int a, int b) { this.a = a; this.b = b; }
        }
      """, LombokClassNames.ALL_ARGS_CONSTRUCTOR
    )
  }

  fun testNonNullParameter() {
    checkConversion(
      """
      class Example {
        @lombok.NonNull private String name;
        public <caret>Example(@lombok.NonNull String name) { this.name = name; }
      }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  fun testNonNullParameterWithPrefix() {
    checkConversion(
      """
      @lombok.experimental.Accessors(prefix = "my")
      class Example {
        @lombok.NonNull private String myName;
        public <caret>Example(@lombok.NonNull String name) { this.myName = name; }
      }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  fun testNullableParameter() = checkConversion(
    """
    class Example {
      @org.jetbrains.annotations.Nullable private final String name;
      public <caret>Example(@org.jetbrains.annotations.Nullable String name) { this.name = name; }
    }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
  )

  fun testConfiguredCopyableParameterAnnotation() {
    addCopyableAnnotation()
    checkConversion(
      """
      class Example {
        @test.Marker("name") private final String name;
        public <caret>Example(@test.Marker(value = "name") String name) { this.name = name; }
      }
      """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  fun testNonNullWithConfiguredCopyableAnnotation() {
    addCopyableAnnotation()
    checkConversion(
      """
      class Example {
        @lombok.NonNull @test.Marker("name") private String name;
        public <caret>Example(@lombok.NonNull @test.Marker("name") String name) { this.name = name; }
      }
      """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  fun testDifferentCopyableAnnotationValue() {
    addCopyableAnnotation()
    checkUnavailable(
      """
      class Example {
        @test.Marker("field") private final String name;
        public <caret>Example(@test.Marker("parameter") String name) { this.name = name; }
      }
      """
    )
  }

  fun testCopyableAnnotationMissingFromField() = checkUnavailable(
    """
    class Example {
      private final String name;
      public <caret>Example(@org.jetbrains.annotations.Nullable String name) { this.name = name; }
    }
    """
  )

  fun testCopyableAnnotationMissingFromParameter() = checkUnavailable(
    """
    class Example {
      @org.jetbrains.annotations.Nullable private final String name;
      public <caret>Example(String name) { this.name = name; }
    }
    """
  )

  private fun addCopyableAnnotation() {
    myFixture.addClass("package test; public @interface Marker { String value(); }")
    myFixture.addFileToProject("lombok.config", "config.stopBubbling = true\nlombok.copyableAnnotations += test.Marker")
  }

  fun testPrivateAccess() = checkAccess("private")

  fun testProtectedAccess() = checkAccess("protected")

  fun testPackageAccess() = checkAccess("")

  fun testTypeParametersAndArray() {
    checkConversion(
      """
      class Example<T> {
        private final T value;
        private final String[] names;
        public <caret>Example(T value, String[] names) {
          this.value = value;
          this.names = names;
        }
      }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  fun testExplicitEmptySuperCall() {
    checkConversion(
      """
      class Example {
        private final int count;
        public <caret>Example(int count) { super(); this.count = count; }
      }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  fun testSuperCallObservesAssignedField() = checkUnavailable(
    """
    class Parent {
      Parent() { observe(); }
      void observe() {}
    }
    class Example extends Parent {
      int count;
      <caret>Example(int count) { this.count = count; super(); }
      @Override void observe() { System.out.println(count); }
    }
    """
  )

  fun testOverloadedConstructor() {
    checkConversion(
      """
      class Example {
        private final int count;
        public Example() { this(0); }
        public <caret>Example(int count) { this.count = count; }
      }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
    assertTrue(myFixture.file.text.contains("public Example() { this(0); }"))
  }

  fun testEnum() {
    checkConversion(
      """
      enum Example {
        FIRST(1);
        private final int count;
        <caret>Example(int count) { this.count = count; }
      }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  fun testComments() {
    checkConversion(
      """
      class Example {
        private final int count;
        public <caret>Example(int count) {
          // Keep the count.
          this.count = count;
        }
      }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
    assertTrue(myFixture.file.text.contains("// Keep the count."))
  }

  fun testWrongParameterOrder() = checkUnavailable(
    """
    class Example {
      int first;
      int second;
      <caret>Example(int second, int first) { this.first = first; this.second = second; }
    }
  """
  )

  fun testWrongAssignmentOrder() = checkConversion(
    """
    class Example {
      int first;
      int second;
      <caret>Example(int first, int second) { this.second = second; this.first = first; }
    }
  """, LombokClassNames.ALL_ARGS_CONSTRUCTOR
  )

  fun testWrongParameterType() = checkUnavailable(
    """
    class Example {
      long count;
      <caret>Example(int count) { this.count = count; }
    }
  """
  )

  fun testPartialFields() = checkUnavailable(
    """
    class Example {
      int first;
      int second;
      <caret>Example(int first) { this.first = first; }
    }
  """
  )

  fun testCompoundAssignment() = checkUnavailable(
    """
    class Example {
      int count;
      <caret>Example(int count) { this.count += count; }
    }
  """
  )

  fun testAdditionalLogic() = checkUnavailable(
    """
    class Example {
      int count;
      <caret>Example(int count) { this.count = count; System.out.println(count); }
    }
  """
  )

  fun testTransformedValue() = checkUnavailable(
    """
    class Example {
      int count;
      <caret>Example(int count) { this.count = Math.abs(count); }
    }
  """
  )

  fun testOtherInstance() = checkUnavailable(
    """
    class Example {
      Example other;
      <caret>Example(Example other) { other.other = other; }
    }
  """
  )

  fun testStaticField() = checkUnavailable(
    """
    class Example {
      static int count;
      <caret>Example(int count) { Example.count = count; }
    }
  """
  )

  fun testInheritedField() = checkUnavailable(
    """
    class Parent { int count; }
    class Example extends Parent {
      <caret>Example(int count) { this.count = count; }
    }
  """
  )

  fun testSuperArguments() = checkUnavailable(
    """
    class Parent { Parent(int count) {} }
    class Example extends Parent {
      int count;
      <caret>Example(int count) { super(count); this.count = count; }
    }
  """
  )

  fun testDelegation() = checkUnavailable(
    """
    class Example {
      int count;
      Example() {}
      <caret>Example(int count) { this(); this.count = count; }
    }
  """
  )

  fun testVarargs() = checkUnavailable(
    """
    class Example {
      String[] names;
      <caret>Example(String... names) { this.names = names; }
    }
  """
  )

  fun testThrows() = checkUnavailable(
    """
    class Example {
      int count;
      <caret>Example(int count) throws Exception { this.count = count; }
    }
  """
  )

  fun testConstructorTypeParameter() = checkUnavailable(
    """
    class Example {
      Object value;
      <T> <caret>Example(T value) { this.value = value; }
    }
  """
  )

  fun testConstructorAnnotation() = checkUnavailable(
    """
    class Example {
      int count;
      @Deprecated <caret>Example(int count) { this.count = count; }
    }
  """
  )

  fun testParameterAnnotation() = checkUnavailable(
    """
    class Example {
      String name;
      <caret>Example(@lombok.NonNull String name) { this.name = name; }
    }
  """
  )

  fun testNonNullFieldWithoutCheck() = checkUnavailable(
    """
    class Example {
      @lombok.NonNull String name;
      <caret>Example(String name) { this.name = name; }
    }
  """
  )

  fun testExistingRequiredArgsConstructorAnnotation() = checkUnavailable(
    """
    @lombok.RequiredArgsConstructor
    class Example {
      final int count;
      <caret>Example(int count) { this.count = count; }
    }
  """
  )

  fun testExistingAllArgsConstructorAnnotation() = checkUnavailable(
    """
    @lombok.AllArgsConstructor
    class Example {
      final int count;
      <caret>Example(int count) { this.count = count; }
    }
  """
  )

  // TODO Is this enough of a reason to disable the inspection?
  fun testDifferentNullCheckMessage() = checkUnavailable(
    """
    class Example {
      @lombok.NonNull String name;
      <caret>Example(@lombok.NonNull String value) { this.name = value; }
    }
  """
  )

  fun testRecord() = checkUnavailable(
    """
    record Example(int count) {
      <caret>Example(int count) { this.count = count; }
    }
  """
  )

  fun testOrdinaryMethod() = checkUnavailable(
    """
    class Example {
      int count;
      void <caret>Example(int count) { this.count = count; }
    }
  """
  )

  fun testNoArguments() = checkUnavailable(
    """
    class Example { <caret>Example() {} }
  """
  )

  fun testJavadoc() = checkUnavailable(
    """
    class Example {
      int count;
      /** Creates an example. */
      <caret>Example(int count) { this.count = count; }
    }
  """
  )

  fun testParenthesizedAssignment() {
    checkConversion(
      """
      class Example {
        private final int count;
        public <caret>Example(final int value) { ((this).count) = ((value)); }
      }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  fun testAllArgsIgnoresStaticAndInitializedFinalFields() {
    checkConversion(
      """
      class Example {
        private static int total;
        private final int constant = 1;
        private int count;
        public <caret>Example(int count) { this.count = count; }
      }
    """, LombokClassNames.ALL_ARGS_CONSTRUCTOR
    )
  }

  fun testExistingAllArgsAnnotation() = checkUnavailable(
    """
    @lombok.AllArgsConstructor
    class Example {
      int count;
      <caret>Example(int count) { this.count = count; }
    }
  """
  )

  fun testBothConstructorAnnotations() = checkUnavailable(
    """
    @lombok.RequiredArgsConstructor
    @lombok.AllArgsConstructor
    class Example {
      final int count;
      <caret>Example(int count) { this.count = count; }
    }
  """
  )

  fun testReceiverParameter() = checkUnavailable(
    """
    class Outer {
      class Example {
        int count;
        <caret>Example(Outer Outer.this, int count) { this.count = count; }
      }
    }
  """
  )

  fun testSyntaxError() = checkUnavailable(
    """
    class Example {
      int count;
      <caret>Example(int count) { this.count = count }
    }
  """
  )

  fun testMissingBody() = checkUnavailable(
    """
    class Example {
      int count;
      <caret>Example(int count);
    }
  """
  )

  fun testMissingAssignment() = checkUnavailable(
    """
    class Example {
      int first;
      int second;
      <caret>Example(int first, int second) { this.first = first; }
    }
  """
  )

  fun testDuplicateAssignment() = checkUnavailable(
    """
    class Example {
      int first;
      int second;
      <caret>Example(int first, int second) { this.first = first; this.first = first; }
    }
  """
  )

  fun testAssignmentBlock() = checkUnavailable(
    """
    class Example {
      int count;
      <caret>Example(int count) { { this.count = count; } }
    }
  """
  )

  fun testMethodCallInsteadOfAssignment() = checkUnavailable(
    """
    class Example {
      int count;
      <caret>Example(int count) { System.out.println(count); }
    }
  """
  )

  fun testArrayElementAssignment() = checkUnavailable(
    """
    class Example {
      int[] counts = new int[1];
      <caret>Example(int[] counts) { this.counts[0] = counts[0]; }
    }
  """
  )

  fun testParameterSelfAssignment() = checkUnavailable(
    """
    class Example {
      int count;
      <caret>Example(int count) { count = count; }
    }
  """
  )

  fun testQualifiedThis() = checkUnavailable(
    """
    class Example {
      int count;
      <caret>Example(int count) { Example.this.count = count; }
    }
  """
  )

  fun testQualifiedRightHandSide() = checkUnavailable(
    """
    class Example {
      int count;
      <caret>Example(int count) { this.count = this.count; }
    }
  """
  )

  fun testSuperTypeArguments() = checkUnavailable(
    """
    class Parent { <T> Parent() {} }
    class Example extends Parent {
      int count;
      <caret>Example(int count) { <String>super(); this.count = count; }
    }
  """
  )

  fun testQualifiedSuperCall() = checkUnavailable(
    """
    class Outer { class Parent {} }
    class Example extends Outer.Parent {
      static Outer outer = new Outer();
      int count;
      <caret>Example(int count) { outer.super(); this.count = count; }
    }
  """
  )

  fun testTypeUseAnnotation() = checkUnavailable(
    """
    import java.lang.annotation.ElementType;
    import java.lang.annotation.Target;

    @Target(ElementType.TYPE_USE)
    @interface Marker {}

    class Example {
      String[] names;
      <caret>Example(String @Marker [] names) { this.names = names; }
    }
  """
  )

  fun testCustomParameterAnnotation() = checkUnavailable(
    """
    import java.lang.annotation.ElementType;
    import java.lang.annotation.Target;

    @Target(ElementType.PARAMETER)
    @interface Marker {}

    class Example {
      int count;
      <caret>Example(@Marker int count) { this.count = count; }
    }
  """
  )

  // TODO see comment on test below
  fun testNonNullParameterWithAccessor() = checkUnavailable(
    """
    @lombok.experimental.Accessors(prefix = "my")
    class Example {
      @lombok.NonNull String myName;
      <caret>Example(@lombok.NonNull String myName) { this.myName = myName; }
    }
  """
  )

  // TODO This would cause a null check to be inserted by Lombok in the constructor. Should this cause the inspection to be disabled?
  //   It does change runtime behavior, but it feels it's a pro, not a con
  fun testExternalNonNullAnnotationAddsCheck() = checkUnavailable(
    """
    class Example {
      @org.jetbrains.annotations.NotNull String name;
      <caret>Example(@org.jetbrains.annotations.NotNull String name) { this.name = name; }
    }
    """
  )

  fun testExternalNonNullAnnotationOnPrimitive() = checkConversion(
    """
    class Example {
      @org.jetbrains.annotations.NotNull final int count;
      <caret>Example(@org.jetbrains.annotations.NotNull int value) { this.count = value; }
    }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
  )

  // The behavior would change:
  // - without the annotation: new Example(1) -> count = 1, other = 0
  // - with @RAC/@AAC:         new Example(1) -> count = 1, other = 42
  fun testBuilderDefaultAddsInitialization() = checkUnavailable(
    """
    @lombok.Builder
    class Example {
      final int count;
      @lombok.Builder.Default int other = 42;
      Example(int count, int other) { this.count = count; this.other = other; }
      <caret>Example(int count) { this.count = count; }
    }
    """
  )

  // Lombok would create a constructor: Example(int count, int count) which causes a compilation error
  fun testDuplicateGeneratedParameterNames() = checkUnavailable(
    """
    @lombok.experimental.Accessors(prefix = {"my", ""})
    class Example {
      final int myCount;
      final int count;
      <caret>Example(int first, int second) { this.myCount = first; this.count = second; }
    }
    """
  )

  fun testFieldTypeUseAnnotationAddsAnnotation() = checkUnavailable(
    """
    import java.lang.annotation.ElementType;
    import java.lang.annotation.Target;

    @Target(ElementType.TYPE_USE)
    @interface Marker {}

    class Example {
      String @Marker [] names;
      <caret>Example(String[] names) { this.names = names; }
    }
    """
  )

  fun testData() = checkConversion(
    """
    @lombok.Data
    class Example {
      final int count;
      public <caret>Example(int count) { this.count = count; }
    }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
  )

  fun testValue() = checkConversion(
    """
    @lombok.Value
    class Example {
      int count;
      public <caret>Example(int count) { this.count = count; }
    }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
  )

  fun testDollarFieldIsIgnored() = checkConversion(
    $$"""
    class Example {
      int $internal;
      final int count;
      <caret>Example(int count) { this.count = count; }
    }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
  )

  fun testMultipleNonNullParameters() = checkConversion(
    """
    class Example {
      @lombok.NonNull String first;
      @lombok.NonNull String second;
      <caret>Example(@lombok.NonNull String first, @lombok.NonNull String second) {
        this.first = first;
        this.second = second;
      }
    }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
  )

  fun testNestedClass() = checkConversion(
    """
    class Outer {
      static class Example {
        final int count;
        <caret>Example(int count) { this.count = count; }
      }
    }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
  )

  fun testInnerClass() = checkConversion(
    """
    class Outer {
      class Example {
        final int count;
        <caret>Example(int count) { this.count = count; }
      }
    }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
  )

  fun testLocalClass() = checkConversion(
    """
    class Outer {
      void example() {
        class Example {
          final int count;
          <caret>Example(int count) { this.count = count; }
        }
      }
    }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
  )

  fun testInitializedNonNullFieldIsOptional() = checkConversion(
    """
    class Example {
      @lombok.NonNull String name = "name";
      final int count;
      <caret>Example(int count) { this.count = count; }
    }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
  )

  fun testNonNullParameterWithConfiguredPrefix() {
    myFixture.addFileToProject("lombok.config", "config.stopBubbling = true\nlombok.accessors.prefix += my")
    checkConversion(
      """
      class Example {
        @lombok.NonNull String myName;
        <caret>Example(@lombok.NonNull String name) { this.myName = name; }
      }
      """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  fun testCopyableAnnotationOrder() {
    addCopyableAnnotation()
    checkConversion(
      """
      class Example {
        @test.Marker("name") @lombok.NonNull String name;
        <caret>Example(@lombok.NonNull @test.Marker("name") String name) { this.name = name; }
      }
      """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
  }

  private fun checkAccess(access: String) {
    checkConversion(
      """
      class Example {
        private final int count;
        $access <caret>Example(int count) { this.count = count; }
      }
    """, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR
    )
    val level = if (access.isEmpty()) "PACKAGE" else access.uppercase()
    assertTrue(myFixture.file.text.contains("@RequiredArgsConstructor(access = AccessLevel.$level)"))
  }

  private fun checkConversion(source: String, annotationName: String) {
    myFixture.configureByText("Example.java", source.trimIndent())
    val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
    val constructor = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)!!
    val parameterTypes = constructor.parameterList.parameters.map { it.type.canonicalText }
    val parameterAnnotations = constructor.parameterList.parameters.map { parameter ->
      parameter.annotations.map { it.copy() as PsiAnnotation }
    }
    val access = PsiUtil.getAccessLevel(constructor.modifierList)
    val className = constructor.containingClass!!.name
    val constructorCount = PsiClassUtil.collectClassConstructorIntern(constructor.containingClass!!).size
    val text = LombokBundle.message("inspection.lombok.constructor.may.be.used.fix", annotationName.substringAfterLast('.'))
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(text))

    val psiClass = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiClass::class.java).single { it.name == className }
    assertTrue(psiClass.hasAnnotation(annotationName))
    assertEquals(constructorCount - 1, PsiClassUtil.collectClassConstructorIntern(psiClass).size)
    val generatedConstructor = psiClass.constructors.single { method ->
      method.parameterList.parameters.map { it.type.canonicalText } == parameterTypes
    }
    assertEquals(access, PsiUtil.getAccessLevel(generatedConstructor.modifierList))
    parameterAnnotations.zip(generatedConstructor.parameterList.parameters).forEach { (annotations, parameter) ->
      val generatedAnnotations = parameter.annotations.toMutableList()
      assertEquals(annotations.size, generatedAnnotations.size)
      for (annotation in annotations) {
        val index = generatedAnnotations.indexOfFirst { AnnotationUtil.equal(annotation, it) }
        assertTrue("The generated parameter must keep the annotation: ${annotation.text}", index >= 0)
        generatedAnnotations.removeAt(index)
      }
    }
    myFixture.checkHighlighting()
  }

  private fun checkUnavailable(source: String) {
    myFixture.configureByText("Example.java", source.trimIndent())
    val fixNames = listOf(LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR, LombokClassNames.ALL_ARGS_CONSTRUCTOR).map {
      LombokBundle.message("inspection.lombok.constructor.may.be.used.fix", StringUtil.getShortName(it))
    }
    assertEmpty(myFixture.availableIntentions.filter { it.text in fixNames })
  }
}
