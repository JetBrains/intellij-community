// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.model.SymbolPointer
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.ModPlanEdit
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.ElementsForRename
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.SubjectUsage
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.Substitution
import com.intellij.refactoring.modplan.rename.ModPlanRenameUtil
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.ResolvableCollisionUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.scopes.memberScope
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.directlyOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.propertyNameByAccessor
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.psi.removeModifierKeyword
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.resolve.DataClassResolver
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * The rename of Kotlin declarations on the ModPlan engine. It finds what is renamed and the usages through the processors of Kotlin,
 * such as [RenameKotlinFunctionProcessor], and decides before the write what their `renameElement` decides while it writes: the names that the
 * usages of the accessors of a property get, the mangled names, the imports of several declarations, the `override` modifier. A Kotlin
 * element that no Kotlin processor renames, such as a label, gets the new name with its references.
 *
 * **Known differences from the legacy rename:**
 * - the facade class of a file is renamed with the file only, as its own legacy rename writes the files with its own helper; a usage
 *   that a [ForeignUsagesRenameProcessor] writes is not supported;
 * - a reference to an enum entry that a companion member of the new name takes is not bound to the entry.
 *
 * Each subclass is the provider of one processor of Kotlin. An element belongs to the provider of the processor that
 * `RenamePsiElementProcessor.forElement` gives. An element that the legacy rename gives to a processor of another plugin, such as the one of
 * the Compose resources, has no owner here.
 *
 * @param processor an instance of the legacy processor. The hooks of the bridge call the registered instance; a subclass calls this one
 * where the legacy method asks the user, with the answer of the ModPlan question instead.
 */
@ApiStatus.Internal
abstract class ModPlanKotlinRenameProvider<P : RenamePsiElementProcessor>(protected val processor: P) :
    ModPlanBridgeRenameProvider(processor.javaClass) {
    /** @return true if `canProcessElement` of the legacy processor of this provider accepts [element] */
    open fun processorAccepts(element: PsiElement): Boolean = processor.canProcessElement(element)

    /** The legacy rename gives [subject] to the processor. */
    override fun isOwner(subject: SymbolPointer): Boolean {
        val element = subject.element() ?: return false
        return processorAccepts(element) && processorClass().isInstance(RenamePsiElementProcessor.forElement(element))
    }

    /** The usages in another language, which a [ForeignUsagesRenameProcessor] renames, need the legacy rename. */
    override fun isLegacyOnly(subject: SymbolPointer): Boolean = ForeignUsagesRenameProcessor.hasProcessors()

    /**
     * As the legacy rename: the question about a declaration that overrides another one and `substituteElementToRename`.
     *
     * The base class gives the target the new name of the subject. Here a light accessor that its property renames gets the name of an
     * accessor, and the property takes the name of the property, as `RenameKotlinPropertyProcessor.prepareRenaming` computes it with
     * `propertyNameByAccessor`:
     * ```
     * // Kotlin
     * class AP { var first: Int = 1 }
     * // Java
     * int x = new AP().getFirst();   // rename getFirst to getSecond
     * // after, in Kotlin
     * class AP { var second: Int = 1 }
     * ```
     */
    override fun substituteRequest(chainBuilder: ModPlanChainBuilder, subject: SymbolPointer, newName: String): ModPlanChain<Substitution?> {
        val element = subject.element() ?: return chainBuilder.passValue(null)
        val substitutes = chainBuilder.request.optionValues().get(ModPlanRenameOptions.SUBSTITUTE_TARGET)
        return chainBuilder.askAll(if (substitutes) substitutionQuestions(subject, element) else emptyList()) { afterBase ->
            val answeredElement = subject.element() ?: return@askAll afterBase.passValue(null)
            val renamed = if (substitutes) {
                substitute(answeredElement, afterBase.request.optionValues())
            } else {
                answeredElement
            } ?: return@askAll afterBase.passValue(null)
            val target = if (renamed == answeredElement) { subject } else { SymbolPointer.of(renamed) }
            val targetName = if (renamed == answeredElement) { newName } else { replacementNameOf(answeredElement, newName) }
            afterBase.passValue(Substitution(target, targetName))
        }
    }

    /** @return the new name of what replaces [element]: the name of the property for a light accessor */
    private fun replacementNameOf(element: PsiElement, newName: String): String =
        (element as? KtLightMethod)?.let { propertyNameByAccessor(newName, it) } ?: newName

    /**
     * @return the question of the legacy rename about a declaration that overrides another one: rename the base declarations instead. The
     * legacy processors ask it in `substituteElementToRename` with a dialog, which the ModPlan path must not show.
     */
    override fun substitutionQuestions(subject: SymbolPointer, element: PsiElement): List<ModPlanQuestion> {
        val declaration = element.unwrapped as? KtCallableDeclaration
        if (declaration?.hasModifier(KtTokens.OVERRIDE_KEYWORD) != true) return emptyList()
        val baseDeclarations = KotlinSearchUsagesSupport.SearchUtils.findDeepestSuperMethodsNoWrapping(element)
        return listOf(renameBaseDeclarationQuestion(declaration, baseDeclarations))
    }

    /**
     * The edits of the declaration. The legacy rename writes them with `setName` and [RenameLightElementsHelper], on the original code. The
     * ModPlan engine writes on copies, so the decision is made on the original code, and the edit writes it on the copy.
     *
     * A light method writes its `@JvmName`, as `renameLightMethod` does. Here the Java reference `foo` is renamed to `bar`:
     * ```
     * class A {
     *     @JvmName("foo")
     *     fun first() = 1
     * }
     * // after
     * class A {
     *     @JvmName("bar")
     *     fun first() = 1
     * }
     * ```
     * A facade class writes the `@file:JvmName` of its files, or renames a file, as `renameFacadeLightClass` does. Here the Java
     * reference `Foo` is renamed to `Bar`:
     * ```
     * @file:JvmName("Foo")
     * package test
     * // after
     * @file:JvmName("Bar")
     * package test
     * ```
     */
    override fun declarationEdits(element: PsiElement, newName: String): List<ModPlanEdit> {
        val declaration = (element as? KtLightElement<*, *>)?.kotlinOrigin ?: element
        return when (element) {
            // The wrapper of an accessor renames its usages only, as its setName does nothing.
            is RenameKotlinPropertyProcessor.PropertyMethodWrapper -> emptyList()
            // The facade class of a renamed file gets its name with the file: only its usages are renamed.
            is RenameKotlinFileProcessor.FileRenamingPsiClassWrapper -> emptyList()
            is KtLightClassForFacade -> facadeEdits(element, newName)
            is KtLightMethod -> listOfNotNull(lightMethodEdit(element, newName))
            else -> listOf(ModPlanRenameUtil.declarationEdit(declaration, newName))
        }
    }

    override fun analyze(
        element: PsiElement,
        newName: String,
        usages: MutableList<UsageInfo>,
        renamed: MutableMap<PsiElement, String>,
    ): ModPlanAnalysisResult {
        val result = super.analyze(element, newName, usages, renamed)
        if (result !is ModPlanAnalysisResult.Found) return result
        val overrideEdit = overrideEdit((element as? KtLightElement<*, *>)?.kotlinOrigin ?: element, newName, renamed) ?: return result
        return ModPlanAnalysisResult.Found(result.edits + overrideEdit, result.conflicts)
    }

    /**
     * The edits of the references to [element], as the processor writes them. The legacy `renameElement` writes each reference in the
     * original files and sorts the usages while it writes. The ModPlan engine writes on copies, so this code makes the same decisions
     * before the write. It takes them from the processor: `importState` of [RenameKotlinPsiProcessor],
     * [RenameKotlinPropertyProcessor.usageKindOf] and [RenameKotlinPsiProcessor.MangledJavaRefUsageInfo].
     *
     * As the legacy rename, the imports go first. The references of one name are written together, see [ModPlanRenameUtil.referenceEdits].
     * Null refuses the plan: a collision has no edit of its own, or a reference cannot get the new name.
     *
     * A usage of an accessor gets the name of the new accessor, see [usageNameOf]:
     * ```
     * // Kotlin
     * class AP { var first: Int = 1 }   // rename first to second
     * // Java, after
     * int x = new AP().getSecond();
     * new AP().setSecond(2);
     * ```
     * An import of several declarations keeps the old name for the others, see [addAmbiguousImportEdits].
     */
    override fun referenceEdits(
        element: PsiElement,
        newName: String,
        references: MutableList<UsageInfo>,
        renamed: MutableMap<PsiElement, String>,
    ): List<ModPlanEdit>? {
        val declaration = (element as? KtLightElement<*, *>)?.kotlinOrigin ?: element
        // As the legacy rename of a light accessor, which renames its usages as they are.
        val accessors = accessorNamesOf(element)
        val importUsages = ArrayList<UsageInfo>()
        val usagesByName = LinkedHashMap<String, MutableList<UsageInfo>>()
        val importDirectiveEdits = ArrayList<ModPlanEdit>()
        for (usage in references) {
            // A collision that is not a ModPlanWritableUsageInfo has no edit: legacy calls its resolveCollision after the write.
            if (usage is ResolvableCollisionUsageInfo) return null
            // `p.component1()` of `data class XYZ(val x: Int)` keeps its name when x is renamed, see isComponentUsage.
            if (declaration is KtParameter && isComponentUsage(usage)) continue
            val reference = usage.reference
            // A bindable reference, which the legacy rename binds after the write, such as a reference of a framework to a class.
            if (reference == null || !ModPlanRenameUtil.isRenamedByName(reference)) return null
            val usageName = usageNameOf(element, usage, newName, accessors)
            when {
                // `new AP().getFirst()` in Java gets `getSecond()` when the property first is renamed to second, see usageNameOf.
                usageName != null -> usagesByName.getOrPut(usageName) { ArrayList() }.add(usage)
                // `import lib.Foo` of the class Foo and of the function Foo, when only the function is renamed: see addAmbiguousImportEdits.
                importStateOf(usage) == RenameKotlinPsiProcessor.ImportState.AMBIGUOUS -> {
                    addAmbiguousImportEdits(usage, newName, renamed, usagesByName.getOrPut(newName) { ArrayList() }, importDirectiveEdits)
                }
                // `import p.foo` of the renamed function: the import is written first, as the legacy rename does.
                importStateOf(usage) == RenameKotlinPsiProcessor.ImportState.SIMPLE -> importUsages.add(usage)
                // `foo()`: the reference gets the new name.
                else -> usagesByName.getOrPut(newName) { ArrayList() }.add(usage)
            }
        }
        val result = ArrayList<ModPlanEdit>()
        result.addAll(ModPlanRenameUtil.referenceEdits(importUsages, newName) ?: return null)
        for ([name, usagesOfName] in usagesByName) {
            result.addAll(ModPlanRenameUtil.referenceEdits(usagesOfName, name) ?: return null)
        }
        result.addAll(importDirectiveEdits)
        return result
    }

    /** @return the import state of [usage], as `renameElement` of a Kotlin processor sorts the usages; a usage of another processor is no import */
    private fun importStateOf(usage: UsageInfo): RenameKotlinPsiProcessor.ImportState {
        val kotlinProcessor = processor as? RenameKotlinPsiProcessor ?: return RenameKotlinPsiProcessor.ImportState.NOT_IMPORT
        return with(kotlinProcessor) { usage.importState() }
    }

    /** The old names of the getter and the setter of a property, whose usages in Java get the names of the new ones. */
    private class AccessorNames(val oldGetter: String, val oldSetter: String)

    private fun accessorNamesOf(declaration: PsiElement): AccessorNames? {
        if (declaration !is KtProperty && declaration !is KtParameter || declaration is KtProperty && declaration.isLocal) return null
        val name = (declaration as KtNamedDeclaration).name ?: return null
        return AccessorNames(JvmAbi.getterName(name), JvmAbi.setterName(name))
    }

    /** The edit of the declaration of a light method, as [RenameLightElementsHelper.renameLightMethod] writes it. */
    private fun lightMethodEdit(method: KtLightMethod, newName: String): ModPlanEdit? {
        return when (val rename = RenameLightElementsHelper.lightMethodRename(method, newName)) {
            is RenameLightElementsHelper.LightMethodRename.DeleteJvmName -> ModPlanEdit.delete(rename.annotation.unwrapped ?: rename.annotation)
            is RenameLightElementsHelper.LightMethodRename.ReplaceJvmName -> ModPlanEdit.update(rename.nameExpression) { copy ->
                copy.replace(KtPsiFactory(copy.project).createStringTemplate(rename.name))
            }
            is RenameLightElementsHelper.LightMethodRename.RenameOrigin -> ModPlanRenameUtil.declarationEdit(rename.origin, rename.name)
            null -> null
        }
    }

    /** The edits of the files of a facade class, as [RenameLightElementsHelper.renameFacadeLightClass] writes them. */
    private fun facadeEdits(facade: KtLightClassForFacade, newName: String): List<ModPlanEdit> {
        return facade.files.mapNotNull { file -> RenameLightElementsHelper.facadeFileRename(file, newName) }.map { rename ->
            when (rename) {
                is RenameLightElementsHelper.FacadeFileRename.DeleteJvmName -> ModPlanEdit.delete(rename.annotation)
                is RenameLightElementsHelper.FacadeFileRename.RenameFile -> ModPlanRenameUtil.declarationEdit(rename.file, rename.newFileName)
                is RenameLightElementsHelper.FacadeFileRename.AddJvmName -> {
                    val name = rename.name
                    ModPlanEdit.update(rename.file) { copy -> RenameLightElementsHelper.addJvmName(copy, name) }
                }
                is RenameLightElementsHelper.FacadeFileRename.ReplaceJvmName -> {
                    val name = rename.name
                    ModPlanEdit.update(rename.nameExpression) { copy -> ElementManipulators.handleContentChange(copy, name) }
                }
            }
        }
    }

    /**
     * @return the name that [usage] gets, if it is not [newName]: the one of an accessor, as `RenameKotlinPropertyProcessor.renameElement`
     * renames them, the mangled one of a usage in Java of an internal declaration, and the demangled one of a usage in Kotlin of a mangled
     * light method
     *
     * The mangled name keeps the suffix of the module, as `renameMangledUsageIfPossible` writes it:
     * ```
     * // Kotlin
     * sealed class X { internal abstract fun overridableMethod(x: Int): Int }   // rename overridableMethod to newOverridableMethod
     * // Java, after
     * public int newOverridableMethod$light_idea_test_case(int x) { ... }
     * ```
     */
    private fun usageNameOf(element: PsiElement, usage: UsageInfo, newName: String, accessors: AccessorNames?): String? {
        if (element is KtLightMethod && element.isMangled && usage.reference is KtReference) {
            return KotlinRenameRefactoringSupport.getInstance().demangleInternalName(newName)
        }
        val unquoted = newName.unquoteKotlinIdentifier()
        val propertyProcessor = processor as? RenameKotlinPropertyProcessor
        val usageKind = accessors?.let { propertyProcessor?.usageKindOf(usage, it.oldGetter, it.oldSetter) }
        val accessorName = when (usageKind) {
            RenameKotlinPropertyProcessor.UsageKind.GETTER_USAGE -> JvmAbi.getterName(unquoted).quoteIfNeeded()
            RenameKotlinPropertyProcessor.UsageKind.SETTER_USAGE -> JvmAbi.setterName(unquoted).quoteIfNeeded()
            else -> null
        }
        if (usage is RenameKotlinPsiProcessor.MangledJavaRefUsageInfo) {
            return KotlinRenameRefactoringSupport.getInstance().mangleInternalName(accessorName ?: newName, usage.manglingSuffix)
        }
        return accessorName
    }

    /**
     * As the legacy rename of a parameter: a call of a `componentN` function of a data class keeps its name.
     * ```
     * data class XYZ(val x: Int, val y: Int)   // rename x to a
     * fun test(p: XYZ) = p.component1()      // after: the call keeps its name
     * ```
     */
    private fun isComponentUsage(usage: UsageInfo): Boolean {
        val target = usage.reference?.resolve()
        return target is KtLightMethod && DataClassResolver.isComponentLike(Name.guessByFirstCharacter(target.name))
    }

    /**
     * Adds the edit of an import of several declarations, which the legacy rename writes after the write: the import gets the new name if
     * all of them are renamed, then [usage] goes to [renamedUsages]; else the old one stays for the others, and an import of the new name
     * is added to [importDirectiveEdits].
     *
     * Here the import `lib.Foo` refers to the class `Foo` and to the function `Foo`, and only the function is renamed:
     * ```
     * package lib
     * class Foo
     * fun Foo(p: Int): Foo = Foo()   // rename the function Foo to Foo2
     *
     * package usage
     * import lib.Foo
     * fun test() { val v: Foo = Foo(1) }
     * // after
     * import lib.Foo
     * import lib.Foo2
     * fun test() { val v: Foo = Foo2(1) }
     * ```
     */
    private fun addAmbiguousImportEdits(
        usage: UsageInfo,
        newName: String,
        renamed: Map<PsiElement, String>,
        renamedUsages: MutableList<UsageInfo>,
        importDirectiveEdits: MutableList<ModPlanEdit>,
    ) {
        val reference = usage.reference as? PsiPolyVariantReference ?: return
        val renamedDeclarations = renamed.keys.mapNotNullTo(HashSet()) { it.unwrapped }
        // The import refers to the class Foo and to the function Foo. If the rename changes both, the import gets the new name.
        if (reference.multiResolve(false).all { it.element?.unwrapped in renamedDeclarations }) {
            renamedUsages.add(usage)
            return
        }
        // Else `import lib.Foo` stays for the class, and `import lib.Foo2` is added after it, unless the file has it.
        val directive = reference.element.getStrictParentOfType<KtImportDirective>() ?: return
        val newFqName = directive.importedFqName?.parent()?.child(Name.identifier(newName)) ?: return
        val importList = directive.parent as? KtImportList ?: return
        if (importList.imports.any { it.importedFqName == newFqName }) return
        importDirectiveEdits.add(ModPlanEdit.update(directive) { copy ->
            copy.parent.addAfter(KtPsiFactory(copy.project).createImportDirective(ImportPath(newFqName, false)), copy)
        })
    }

    /**
     * The edit that drops the `override` modifier of a declaration that overrides nothing under its new name, which the legacy rename finds
     * after the write, see `dropOverrideKeywordIfNecessary`. The copy does not see the other renamed declarations, so this code decides it
     * before the write. Under the new name it overrides a declaration that is renamed to it too, or a member of that name of a supertype
     * that keeps its name.
     *
     * Here the base declaration keeps its name, as the answer to the base-declaration question is no:
     * ```
     * open class A { open fun foo() {} }
     * class B : A() { override fun foo() {} }   // rename B.foo to bar
     * // after
     * class B : A() { fun bar() {} }
     * ```
     */
    private fun overrideEdit(declaration: PsiElement, newName: String, renamed: Map<PsiElement, String>): ModPlanEdit? {
        val callable = declaration as? KtCallableDeclaration ?: return null
        if (!callable.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return null
        // As the legacy rename of a function, which drops the modifier only if it was needed.
        if (callable is KtNamedFunction && KotlinRenameRefactoringSupport.getInstance().overridesNothing(callable)) return null
        val namesOfRenamed = renamed.entries.associate { [element, name] -> element.unwrapped to name }
        // True keeps the modifier. An unknown symbol keeps it too: the code stays as it is.
        val overrides = analyze(callable) {
            val symbol = callable.symbol as? KaCallableSymbol ?: return@analyze true
            // `class B(override val x: Int) : A()`: the parameter overrides as its property.
            val callableSymbol = (symbol as? KaValueParameterSymbol)?.primaryConstructorProperty ?: symbol
            // `A.foo` and `B.foo` are both renamed to bar: B.bar still overrides A.bar.
            if (callableSymbol.directlyOverriddenSymbols.any { namesOfRenamed[it.psi?.unwrapped] == newName }) return@analyze true
            val owner = callable.containingClassOrObject?.symbol as? KaClassSymbol ?: return@analyze true
            // `A` already has `open fun bar()` that keeps its name: B.bar overrides it. Only a member of a supertype counts.
            owner.memberScope.callables(Name.identifier(newName.unquoteKotlinIdentifier())).any { member ->
                val psi = member.psi?.unwrapped
                psi != null && psi.getStrictParentOfType<KtNamedDeclaration>() != callable.containingClassOrObject && psi !in namesOfRenamed
            }
        }
        if (overrides) return null
        return ModPlanEdit.update(callable) { copy -> copy.removeModifierKeyword(KtTokens.OVERRIDE_KEYWORD) }
    }
}

/** @return the Kotlin provider of [element]: the first registered one whose processor renames it */
private fun ownerOfKotlinElement(element: PsiElement): ModPlanKotlinRenameProvider<*>? {
    return ModPlanRenameProvider.EP_NAME.findFirstSafe { it is ModPlanKotlinRenameProvider<*> && it.processorAccepts(element) }
        as ModPlanKotlinRenameProvider<*>?
}

/**
 * The rename of a light element that no Kotlin processor renames, which the legacy rename renames with the processor of the Java
 * variables. That processor would write the light element, which the ModPlan engine cannot copy. So this provider substitutes the Kotlin
 * declaration of the element and asks nothing. The provider of the declaration does the rest. It is registered first.
 *
 * Here the Java code renames the field of an enum entry:
 * ```
 * // Kotlin
 * enum class Color { RED }
 * // Java
 * Color c = Color.RED;   // rename RED to CRIMSON
 * // after, in Kotlin
 * enum class Color { CRIMSON }
 * ```
 */
@ApiStatus.Internal
class ModPlanKotlinLightOriginRenameProvider : ModPlanRenameProvider {
    override fun isOwner(subject: SymbolPointer): Boolean {
        val element = subject.element() ?: return false
        return element is KtLightElement<*, *> && element.kotlinOrigin != null && ownerOfKotlinElement(element) == null
    }

    override fun substituteRequest(chainBuilder: ModPlanChainBuilder, subject: SymbolPointer, newName: String): ModPlanChain<Substitution?> {
        val declaration = (subject.element() as? KtLightElement<*, *>)?.kotlinOrigin
        return chainBuilder.passValue(declaration?.let { Substitution(SymbolPointer.of(it), newName) })
    }

    override fun findUsages(subject: SymbolPointer, elementsForRename: ElementsForRename, request: ModPlanRequest): List<SubjectUsage> = emptyList()

    override fun analyze(subject: SymbolPointer, elementsForRename: ElementsForRename, keptUsages: List<SubjectUsage>): ModPlanAnalysisResult {
        return ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED)
    }
}

/**
 * The rename of a Kotlin element that no Kotlin processor renames, which the default processor renames. The default processor accepts
 * the elements of every language, so this provider accepts the named Kotlin elements alone. It is registered after the other Kotlin
 * providers.
 * ```
 * fun f() { loop@ for (i in 1..2) { break@loop } }   // rename the label loop to outer
 * // after
 * fun f() { outer@ for (i in 1..2) { break@outer } }
 * ```
 */
@ApiStatus.Internal
class ModPlanKotlinPlainRenameProvider : ModPlanKotlinRenameProvider<RenamePsiElementProcessor>(RenamePsiElementProcessor.DEFAULT) {
    override fun processorAccepts(element: PsiElement): Boolean {
        return element.language == KotlinLanguage.INSTANCE && element.isPhysical && element is PsiNamedElement && element !is PsiFileSystemItem
    }
}

/**
 * The question "the declaration overrides some, rename the base declarations instead?". Its answers name the classes of [baseDeclarations]
 * and of [declaration], as the dialog of the legacy rename does. Its answer is [ModPlanKotlinRenameOptions.RENAME_BASE_DECLARATION].
 */
internal fun renameBaseDeclarationQuestion(declaration: KtCallableDeclaration, baseDeclarations: List<PsiElement>): ModPlanQuestion.Form {
    return ModPlanQuestion.Form.yesNo(
        ModPlanKotlinRenameOptions.RENAME_BASE_DECLARATION,
        KotlinBundle.message("rename.the.base.declarations.in.0", baseDeclarations.joinToString { containingClassNameOf(it) }),
        KotlinBundle.message("rename.only.the.declaration.in.0", containingClassNameOf(declaration)),
    )
}

/** @return the qualified name of the class of the member [element], else its short name */
@ApiStatus.Internal
fun containingClassNameOf(element: PsiElement): String {
    val name = when (val unwrapped = element.unwrapped) {
        is KtDeclaration -> unwrapped.containingClassOrObject?.let { it.fqName?.asString() ?: it.name }
        is PsiMember -> unwrapped.containingClass?.let { it.qualifiedName ?: it.name }
        else -> null
    }
    return name.orEmpty()
}