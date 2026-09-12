// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.standaloneAnalysis.java.smoke

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.core.JavaCoreApplicationEnvironment
import com.intellij.core.JavaCoreProjectEnvironment
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartTypePointerManager
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes
import com.intellij.psi.impl.java.stubs.PsiClassStub
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl
import com.intellij.psi.impl.java.stubs.serializers.JavaClassStubSerializer
import com.intellij.psi.impl.source.JavaFileElementType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PackageScope
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.PsiMethodUtil
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.io.AbstractStringEnumerator
import com.intellij.util.io.DataEnumerator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@ApiStatus.Internal
object JavaStandaloneSmoke {
  private const val NOT_NULL = "org.jetbrains.annotations.NotNull"
  private const val ANNOTATIONS_PACKAGE = "org.jetbrains.annotations"
  private const val MOCK_JDK_PROPERTY = "intellij.standalone.smoke.jdk"

  // A line comment and a doc comment: the syntax library needs the common element type converters for them.
  private val SOURCE = """
    import org.jetbrains.annotations.NotNull;

    // the base of the hierarchy
    class Base {
      Object value() {
        return null;
      }
    }

    class Box<T> {
      private final T item;

      Box(T item) {
        this.item = item;
      }

      static <T> Box<T> of(T item) {
        return new Box<T>(item);
      }

      T get() {
        return item;
      }
    }

    class Derived extends Base {
      static final int SIZE = 2 * 21;

      /** The size of the instance. */
      @Override
      @NotNull
      Object value() {
        return size();
      }

      int size() {
        return SIZE;
      }

      Derived self() {
        return Box.of(this).get();
      }
    }

    class Launcher {
      public static void main(String[] args) {
      }
    }
  """.trimIndent()

  @JvmStatic
  fun main(args: Array<String>) {
    val applicationDisposable = Disposer.newDisposable("JavaStandaloneSmoke application")
    val projectDisposable = Disposer.newDisposable("JavaStandaloneSmoke project")
    val mockJdk = mockJdkClassesJar()
    try {
      val environment = JavaCoreApplicationEnvironment(applicationDisposable, false)
      check(ApplicationManager.getApplication() === environment.application) { "The environment application is not the application" }

      val projectEnvironment = JavaCoreProjectEnvironment(projectDisposable, environment)
      val annotationsJar = PathManager.getJarPathForClass(NotNull::class.java) ?: error("The annotations jar is not found")
      projectEnvironment.addJarToClasspath(Paths.get(annotationsJar))
      projectEnvironment.addJarToClasspath(mockJdk)

      val checks = Checks(projectEnvironment.project)
      checks.classFromJarResolves()
      checks.javaSourceParsesResolvesAndEvaluates()
      checks.expressionTypeIsComputed()
      checks.callsResolveAndTypeArgumentsAreInferred()
      checks.classFileStubRoundTripsThroughSerializers()
      checks.annotationsComeFromTheSourceAndTheManagers()
      checks.inheritanceAndAssignabilityUseClassSupers()
      checks.mainMethodIsFound()
      checks.compiledClassNavigatesToItself()
      checks.elementFactoryReadsCodeStyle()
      checks.packageIsFoundWithItsScope()
      checks.elementFactoryValidatesNames()
      checks.moduleLookupWorks()
      checks.libraryFileIsNotSourceContent()
      checks.smartTypePointerRestoresTheType()
      checks.psiChangeAddsAMethod()
    }
    finally {
      Disposer.dispose(projectDisposable)
      Disposer.dispose(applicationDisposable)
    }
    check(ApplicationManager.getApplication() == null) { "The application is not released after the disposal" }
  }

  private fun mockJdkClassesJar(): Path {
    val path = System.getProperty(MOCK_JDK_PROPERTY) ?: error("The system property $MOCK_JDK_PROPERTY is not set")
    val jar = Paths.get(path).toAbsolutePath()
    check(Files.isRegularFile(jar)) { "The mock JDK classes are not found: $jar" }
    return jar
  }

  private class Checks(private val project: Project) {
    private val allScope: GlobalSearchScope
      get() = GlobalSearchScope.allScope(project)

    private val javaPsiFacade: JavaPsiFacade
      get() = JavaPsiFacade.getInstance(project)

    private val notNullClass: PsiClass
      get() = javaPsiFacade.findClass(NOT_NULL, allScope) ?: error("$NOT_NULL is not found")

    fun classFromJarResolves() {
      val psiClass = notNullClass
      check(psiClass is ClsClassImpl) { "$NOT_NULL is not a compiled class: $psiClass" }
      check(psiClass.isAnnotationType) { "$NOT_NULL is not an annotation" }
      check(psiClass.qualifiedName == NOT_NULL) { "Unexpected qualified name: ${psiClass.qualifiedName}" }
      val value = psiClass.methods.single { it.name == "value" }
      check(value.returnType?.presentableText == "String") { "Unexpected return type: ${value.returnType}" }
    }

    fun javaSourceParsesResolvesAndEvaluates() {
      val psiFile = parseSource()
      val derived = psiFile.classes.single { it.name == "Derived" }
      val derivedValue = derived.methods.single { it.name == "value" }

      val annotation = derivedValue.modifierList.annotations.single { it.qualifiedName == NOT_NULL }
      check(annotation.nameReferenceElement?.resolve() === notNullClass) { "The annotation reference does not resolve to $NOT_NULL" }

      val constant = javaPsiFacade.constantEvaluationHelper.computeConstantExpression(derived.fields.single().initializer)
      check(constant == 42) { "Unexpected constant: $constant" }

      val derivedValueSupers = derivedValue.findSuperMethods().map { it.containingClass?.name }
      check(derivedValueSupers == listOf("Base")) { "Unexpected super methods: $derivedValueSupers" }
    }

    fun expressionTypeIsComputed() {
      val derived = parseSource().classes.single { it.name == "Derived" }

      val sizeCall = PsiTreeUtil
        .findChildrenOfType(derived, PsiMethodCallExpression::class.java)
        .single { it.methodExpression.referenceName == "size" }

      check(sizeCall.type == PsiTypes.intType()) { "Unexpected expression type: ${sizeCall.type}" }
    }

    fun callsResolveAndTypeArgumentsAreInferred() {
      val file = parseSource()

      val box = file.classes.single { it.name == "Box" }
      val derived = file.classes.single { it.name == "Derived" }

      val derivedSelf = derived.methods.single { it.name == "self" }
      val calls = PsiTreeUtil.findChildrenOfType(derivedSelf, PsiMethodCallExpression::class.java)
      val ofCall = calls.single { it.methodExpression.referenceName == "of" }
      val getCall = calls.single { it.methodExpression.referenceName == "get" }

      val boxOf = box.methods.single { it.name == "of" }
      val boxGet = box.methods.single { it.name == "get" }

      val ofResult = ofCall.resolveMethodGenerics()
      val ofResultMethod = ofResult.element
      check(ofResultMethod === boxOf) { "Box.of does not resolve: $ofResultMethod" }
      check(ofResult.isValidResult) { "The resolve result of Box.of is invalid" }
      val ofResultInferred = ofResult.substitutor.substitute(ofResultMethod.typeParameters.single())
      check(ofResultInferred.resolvesTo(derived)) { "T of Box.of is not inferred as Derived: $ofResultInferred" }
      check(ofCall.type?.presentableText == "Box<Derived>") { "Unexpected type of Box.of(this): ${ofCall.type}" }

      check(getCall.resolveMethod() === boxGet) { "Box.get does not resolve: ${getCall.resolveMethod()}" }
      check(getCall.type.resolvesTo(derived)) { "Unexpected type of Box.of(this).get(): ${getCall.type}" }
    }

    private fun PsiType?.resolvesTo(psiClass: PsiClass): Boolean = this is PsiClassType && resolve() === psiClass

    fun classFileStubRoundTripsThroughSerializers() {
      @Suppress("UNCHECKED_CAST")
      val classStub = (notNullClass as ClsClassImpl).stub as PsiClassStub<PsiClass>
      val serializer = JavaClassStubSerializer(JavaStubElementTypes.CLASS)
      val enumerator = InMemoryStringEnumerator()

      val bytes = ByteArrayOutputStream()
      StubOutputStream(bytes, enumerator).use { serializer.serialize(classStub, it) }
      val fileStub = PsiJavaFileStubImpl(true)
      val deserialized = StubInputStream(ByteArrayInputStream(bytes.toByteArray()), enumerator).use {
        serializer.deserialize(it, fileStub)
      }

      check(deserialized.name == "NotNull") { "Unexpected stub name: ${deserialized.name}" }
      check(deserialized.qualifiedName == NOT_NULL) { "Unexpected stub qualified name: ${deserialized.qualifiedName}" }
      check(deserialized.isAnnotationType) { "The deserialized stub is not an annotation" }
      check(deserialized.sourceFileName == classStub.sourceFileName) { "Unexpected source file name: ${deserialized.sourceFileName}" }
    }

    fun annotationsComeFromTheSourceAndTheManagers() {
      val file = parseSource()
      val derivedValue = file.classes.single { it.name == "Derived" }.methods.single { it.name == "value" }
      val baseValue = file.classes.single { it.name == "Base" }.methods.single { it.name == "value" }

      val flags = AnnotationUtil.CHECK_EXTERNAL or AnnotationUtil.CHECK_INFERRED
      check(AnnotationUtil.isAnnotated(derivedValue, NOT_NULL, flags)) { "The source annotation is not found" }
      check(!AnnotationUtil.isAnnotated(baseValue, NOT_NULL, flags)) { "An annotation is found where none exists" }

      // `AnnotationUtil` asks both the external and the inferred annotations managers after the source annotations.
      val annotationNames = AnnotationUtil.getAllAnnotations(derivedValue, false, null).map { it.qualifiedName }
      check(annotationNames == listOf("java.lang.Override", NOT_NULL)) { "Unexpected annotations: $annotationNames" }
    }

    fun inheritanceAndAssignabilityUseClassSupers() {
      val file = parseSource()
      val base = file.classes.single { it.name == "Base" }
      val derived = file.classes.single { it.name == "Derived" }

      // The inheritance check and the assignability check compute the super class substitutor through `JavaClassSupers`.
      check(derived.isInheritor(base, true)) { "Derived is not an inheritor of Base" }
      check(!base.isInheritor(derived, true)) { "Base is an inheritor of Derived" }

      val baseType = PsiTypesUtil.getClassType(base)
      val derivedType = PsiTypesUtil.getClassType(derived)
      check(TypeConversionUtil.isAssignable(baseType, derivedType)) { "Derived is not assignable to Base" }
      check(!TypeConversionUtil.isAssignable(derivedType, baseType)) { "Base is assignable to Derived" }
    }

    fun mainMethodIsFound() {
      val file = parseSource()
      val launcher = file.classes.single { it.name == "Launcher" }
      val derived = file.classes.single { it.name == "Derived" }
      check(PsiMethodUtil.findMainInClass(launcher)?.name == "main") { "The main method of Launcher is not found" }
      check(!PsiMethodUtil.hasMainMethod(derived)) { "Derived has a main method" }
    }

    fun compiledClassNavigatesToItself() {
      val psiClass = notNullClass
      check(psiClass.navigationElement === psiClass) { "Unexpected navigation element: ${psiClass.navigationElement}" }
    }

    fun elementFactoryReadsCodeStyle() {
      val derived = parseSource().classes.single { it.name == "Derived" }
      val factory = JavaPsiFacade.getElementFactory(project)
      val statement = factory.createVariableDeclarationStatement(
        /* name = */ "total",
        /* type = */ PsiTypes.intType(),
        /* initializer = */ factory.createExpressionFromText("42", null),
        /* context = */ derived
      )
      check(statement.text == "int total = 42;") { "Unexpected declaration: ${statement.text}" }
    }

    fun packageIsFoundWithItsScope() {
      val psiPackage = javaPsiFacade.findPackage(ANNOTATIONS_PACKAGE) ?: error("$ANNOTATIONS_PACKAGE is not found")
      check(psiPackage.classes.any { it.qualifiedName == NOT_NULL }) { "$NOT_NULL is not in its package" }
      val scope = PackageScope.packageScope(psiPackage, false)
      val classFile = notNullClass.containingFile.virtualFile

      // A package scope keeps the package directories and files in compact virtual file sets.
      check(scope.contains(classFile)) { "The package scope does not contain the class file" }
      check(!scope.contains(parseSource().virtualFile)) { "The package scope contains a source file of another package" }
    }

    fun elementFactoryValidatesNames() {
      val factory = JavaPsiFacade.getElementFactory(project)
      check(factory.createKeyword("class").text == "class") { "The keyword is not created" }
      val import = factory.createImportStatementOnDemand(ANNOTATIONS_PACKAGE)
      check(import.text == "import $ANNOTATIONS_PACKAGE.*;") { "Unexpected import statement: ${import.text}" }

      // The element factory validates a keyword and a qualified name through `PsiNameHelper`.
      val invalid = runCatching { factory.createImportStatementOnDemand("not a name") }
      check(invalid.isFailure) { "An invalid package name is accepted" }
    }

    fun moduleLookupWorks() {
      val facade = javaPsiFacade
      check(facade.findModule("java.base", allScope) == null) { "A module is found without module descriptors" }
      check(facade.findModules("java.base", allScope).isEmpty()) { "Modules are found without module descriptors" }
    }

    fun libraryFileIsNotSourceContent() {
      check(!JavaFileElementType.isInSourceContent(notNullClass.containingFile.virtualFile)) { "A library class file became a source" }
    }

    fun smartTypePointerRestoresTheType() {
      val type = notNullClass.methods.single { it.name == "value" }.returnType ?: error("The value method has no return type")
      val pointer = SmartTypePointerManager.getInstance(project).createSmartTypePointer(type)
      check(pointer.type == type) { "The smart type pointer does not restore the type: ${pointer.type}" }
    }

    /**
     * A PSI change runs as a POM transaction: it fires the tree change events, increments the modification tracker, and keeps a smart
     * pointer to an unchanged element valid. The member lists ask the `PsiAugmentProvider` point after the change.
     */
    fun psiChangeAddsAMethod() {
      val derived = parseSource().classes.single { it.name == "Derived" }
      val derivedSize = derived.methods.single { it.name == "size" }
      val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(derivedSize)
      val tracker = PsiModificationTracker.getInstance(project)
      val countBefore = tracker.modificationCount

      val newMethod = JavaPsiFacade.getElementFactory(project).createMethodFromText("int extra() { return SIZE; }", derived)
      CommandProcessor.getInstance().runUndoTransparentAction {
        ApplicationManager.getApplication().runWriteAction { derived.add(newMethod) }
      }

      check(derived.findMethodsByName("extra", false).size == 1) { "The added method is not found: ${derived.methods.map { it.name }}" }
      check(tracker.modificationCount > countBefore) { "The modification tracker did not notice the change" }
      check(pointer.element === derivedSize) { "The smart pointer does not restore the method after the change: ${pointer.element}" }
      check(derived.fields.single().hasModifierProperty(PsiModifier.FINAL)) { "SIZE is not final" }
      check(!derived.hasModifierProperty(PsiModifier.STATIC)) { "Derived is static" }
    }

    private fun parseSource(): PsiJavaFile {
      val fileFactory = PsiFileFactory.getInstance(project)
      return fileFactory.createFileFromText(
        /* name = */ "Test.java",
        /* language = */ JavaLanguage.INSTANCE,
        /* text = */ SOURCE,
        /* supportsSendingPsiEvents = */ true,
        /* markAsCopy = */ false
      ) as PsiJavaFile
    }
  }
}

private class InMemoryStringEnumerator : AbstractStringEnumerator {
  private val values = ArrayList<String>()

  override fun enumerate(value: String?): Int {
    if (value == null) {
      return DataEnumerator.NULL_ID
    }
    val index = values.indexOf(value)
    if (index >= 0) {
      return index + 1
    }
    values.add(value)
    return values.size
  }

  override fun valueOf(idx: Int): String? {
    return if (idx == DataEnumerator.NULL_ID) null else values[idx - 1]
  }

  override fun markCorrupted() {}
  override fun isDirty(): Boolean = false
  override fun force() {}
  override fun close() {}
}
