/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.fir.fe10.binding

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.fir.fe10.Fe10WrapperContext
import org.jetbrains.kotlin.idea.fir.fe10.toDeclarationDescriptor
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.*
import org.jetbrains.kotlin.utils.Printer
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class Fe10BindingScopeProvider(bindingContext: KtSymbolBasedBindingContext) {
    private val context = bindingContext.context

    init {
        bindingContext.registerGetterByKey(BindingContext.LEXICAL_SCOPE, this::getLexicalScope)
    }

    private fun getLexicalScope(element: KtElement): LexicalScope? {
        return context.getLexicalScope(element)
    }
}

private fun Fe10WrapperContext.getLexicalScope(element: PsiElement): LexicalScope? {
    val ktDeclaration = KtStubbedPsiUtil.getPsiOrStubParent(element, KtDeclaration::class.java, false) ?: return null

    return when (ktDeclaration) {
        is KtPropertyAccessor -> {
            val propertyDeclaration = KtStubbedPsiUtil.getContainingDeclaration(ktDeclaration, KtDeclaration::class.java) ?: return null

            val parent = Fe10BindingLexicalScopeForCallableDeclaration(propertyDeclaration, withReceivers = true, this)
            val propertyDescriptor = parent.ownerDescriptor.safeAs<PropertyDescriptor>() ?: return null

            val accessorDescriptor = (if (ktDeclaration.isGetter) propertyDescriptor.getter else propertyDescriptor.setter) ?: return parent

            Fe10BindingLexicalScopeForAccessors(parent, accessorDescriptor, this)
        }
        // i.e. we inside initializer --> no implicit receiver
        is KtProperty -> Fe10BindingLexicalScopeForCallableDeclaration(ktDeclaration, withReceivers = false, this)
        is KtFunction -> Fe10BindingLexicalScopeForCallableDeclaration(ktDeclaration, withReceivers = true, this)
        else -> null
    }
}

/**
 * based on [org.jetbrains.kotlin.resolve.lazy.DeclarationScopeProviderImpl.getResolutionScopeForDeclaration]
 */
private fun Fe10WrapperContext.getResolutionScopeForDeclaration(declaration: KtDeclaration): LexicalScope {
    val ktDeclaration = KtStubbedPsiUtil.getPsiOrStubParent(declaration, KtDeclaration::class.java, false) ?: return fileScope()

    var parentDeclaration = KtStubbedPsiUtil.getContainingDeclaration(ktDeclaration)

    if (ktDeclaration is KtPropertyAccessor) {
        parentDeclaration = KtStubbedPsiUtil.getContainingDeclaration(parentDeclaration!!, KtDeclaration::class.java)
    }

    if (parentDeclaration is KtClassOrObject) {
        val classScopeKind =
            if (ktDeclaration is KtAnonymousInitializer || ktDeclaration is KtProperty) {
                // scopeForInitializerResolution, actually should contain primary constructor also
                Fe10BindingLexicalScopeForClassLikeElement.Kind.ME_AND_COMPANIONS
            } else if (ktDeclaration is KtObjectDeclaration && ktDeclaration.isCompanion()) {
                // scopeForCompanionObjectHeaderResolution
                Fe10BindingLexicalScopeForClassLikeElement.Kind.SCOPE_FOR_COMPANION
            } else if (ktDeclaration is KtObjectDeclaration || (ktDeclaration is KtClass && !ktDeclaration.isInner())) {
                Fe10BindingLexicalScopeForClassLikeElement.Kind.MY_STATIC_SCOPE
            } else Fe10BindingLexicalScopeForClassLikeElement.Kind.ME_AND_COMPANIONS
        return Fe10BindingLexicalScopeForClassLikeElement(parentDeclaration, classScopeKind, this)
    }

    // i.e. ~declaration is local
    if (parentDeclaration != null) {
        return getLexicalScope(parentDeclaration) ?: fileScope()
    }

    return fileScope()
}


private fun Fe10WrapperContext.fileScope() = LexicalScope.Base(ImportingScope.Empty, moduleDescriptor)

private sealed class Fe10BindingLexicalScope(val context: Fe10WrapperContext) : LexicalScope {
    private fun noImplementation(): Nothing = context.noImplementation("LexicalScope wrappers has very limited functionally")

    override val isOwnerDescriptorAccessibleByLabel: Boolean
        get() = noImplementation()
    override val kind: LexicalScopeKind
        get() = noImplementation()

    override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor? = noImplementation()

    override fun getContributedDescriptors(
        kindFilter: DescriptorKindFilter,
        nameFilter: (Name) -> Boolean
    ): Collection<DeclarationDescriptor> = noImplementation()

    override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<FunctionDescriptor> = noImplementation()

    override fun getContributedVariables(name: Name, location: LookupLocation): Collection<VariableDescriptor> = noImplementation()
    override fun printStructure(p: Printer) = noImplementation()
}

private class Fe10BindingLexicalScopeForCompanionObjectReceivers(
    override val implicitReceiver: ReceiverParameterDescriptor?,
    override val ownerDescriptor: DeclarationDescriptor,
    override val parent: LexicalScope,
    context: Fe10WrapperContext
): Fe10BindingLexicalScope(context) {
    /**
     * see [org.jetbrains.kotlin.resolve.lazy.descriptors.ClassResolutionScopesSupport.createInheritanceScope]
     */
    override val contextReceiversGroup: List<ReceiverParameterDescriptor>
        get() = emptyList()
}

private class Fe10BindingLexicalScopeForAccessors(
    override val parent: Fe10BindingLexicalScopeForCallableDeclaration,
    override val ownerDescriptor: DeclarationDescriptor,
    context: Fe10WrapperContext,
): Fe10BindingLexicalScope(context) {
    override val contextReceiversGroup: List<ReceiverParameterDescriptor>
        get() = emptyList()

    override val implicitReceiver: ReceiverParameterDescriptor?
        get() = null
}

private class Fe10BindingLexicalScopeForCallableDeclaration(
    private val ktDeclaration: KtDeclaration,
    private val withReceivers: Boolean,
    context: Fe10WrapperContext
): Fe10BindingLexicalScope(context) {
    override val ownerDescriptor: CallableDescriptor = run {
        context.withAnalysisSession { ktDeclaration.getSymbol() }.toDeclarationDescriptor(context) as CallableDescriptor
    }

    override val parent: HierarchicalScope
        get() = context.getResolutionScopeForDeclaration(ktDeclaration)

    override val implicitReceiver: ReceiverParameterDescriptor?
        get() = ownerDescriptor.extensionReceiverParameter.takeIf { withReceivers }

    override val contextReceiversGroup: List<ReceiverParameterDescriptor>
        get() = ownerDescriptor.contextReceiverParameters
}

private class Fe10BindingLexicalScopeForClassLikeElement(
    val ktClassOrObject: KtClassOrObject,
    val myKind: Kind,
    context: Fe10WrapperContext
) : Fe10BindingLexicalScope(context) {
    enum class Kind {
        OUTER_CLASS, // without receiver, only owner class, parent class is parent scope
        ME_AND_COMPANIONS, // this receiver, parent -- all companion objects and the last one -- same class with OUTER_CLASS
        MY_STATIC_SCOPE, // me -- only if object, parent -- all companion objects and the last one -- same class with OUTER_CLASS
        SCOPE_FOR_COMPANION // all companion objects except mine and the last one -- same class with OUTER_CLASS
    }

    private val ktClassSymbol = context.withAnalysisSession { ktClassOrObject.getSymbol() } as KtClassOrObjectSymbol

    override val ownerDescriptor: ClassDescriptor = ktClassSymbol.toDeclarationDescriptor(context)

    override val implicitReceiver: ReceiverParameterDescriptor?
        get() = when (myKind) {
            Kind.OUTER_CLASS, Kind.SCOPE_FOR_COMPANION -> null
            Kind.ME_AND_COMPANIONS -> ownerDescriptor.thisAsReceiverParameter
            Kind.MY_STATIC_SCOPE -> ownerDescriptor.thisAsReceiverParameter.takeIf { ownerDescriptor.kind.isSingleton }
        }

    override val contextReceiversGroup: List<ReceiverParameterDescriptor>
        get() = ownerDescriptor.contextReceivers.takeIf { myKind == Kind.ME_AND_COMPANIONS } ?: emptyList()

    override val parent: HierarchicalScope
        get() = when (myKind) {
            Kind.OUTER_CLASS -> context.getResolutionScopeForDeclaration(ktClassOrObject)
            Kind.ME_AND_COMPANIONS, Kind.MY_STATIC_SCOPE, Kind.SCOPE_FOR_COMPANION -> {
                val topLevelMe = Fe10BindingLexicalScopeForClassLikeElement(ktClassOrObject, Kind.OUTER_CLASS, context)
                collectAllCompanionObjects(skipMine = myKind == Kind.SCOPE_FOR_COMPANION).reversed().fold<_, Fe10BindingLexicalScope>(topLevelMe) {
                        parent, companion ->
                    val implicitReceiver = companion.toDeclarationDescriptor(context).thisAsReceiverParameter
                    Fe10BindingLexicalScopeForCompanionObjectReceivers(implicitReceiver, ownerDescriptor, parent, context)
                }
            }
        }
    
    private fun collectAllCompanionObjects(skipMine: Boolean): List<KtNamedClassOrObjectSymbol> = buildList {
        var current = ktClassSymbol
        mainLoop@ while (true) {
            if (current is KtNamedClassOrObjectSymbol && (current !== ktClassSymbol || !skipMine)) {
                addIfNotNull(current.companionObject)
            }
            
            superTypeLoop@ for (superType in current.superTypes) {
                if (superType !is KtNonErrorClassType) continue@superTypeLoop
                val classOrObjectSymbol: KtClassOrObjectSymbol = when (val typeSymbol = superType.classSymbol) {
                    is KtClassOrObjectSymbol -> typeSymbol
                    is KtTypeAliasSymbol -> typeSymbol.expandedType.safeAs<KtNonErrorClassType>()?.classSymbol.safeAs<KtClassOrObjectSymbol>()
                        ?: continue@superTypeLoop
                }
                if (classOrObjectSymbol.classKind == KtClassKind.CLASS) {
                    current = classOrObjectSymbol
                    continue@mainLoop
                }
            }
            break@mainLoop
        }
    }
}
