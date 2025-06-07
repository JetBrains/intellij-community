// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.light.LightField
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.classMembers.MemberInfoBase
import com.intellij.refactoring.memberPullUp.PullUpData
import com.intellij.refactoring.memberPullUp.PullUpHelper
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.util.containers.reverse
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher.isSemanticMatch
import org.jetbrains.kotlin.idea.k2.refactoring.pushDown.getSuperTypeEntryBySymbol
import org.jetbrains.kotlin.idea.refactoring.*
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.idea.refactoring.pullUp.*
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameRefactoringSupport
import org.jetbrains.kotlin.idea.refactoring.rename.dropDefaultValue
import org.jetbrains.kotlin.idea.util.isBackingFieldRequired
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name.identifier
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.Variance

private val MODIFIERS_TO_LIFT_IN_SUPERCLASS = listOf(KtTokens.PRIVATE_KEYWORD)
private val MODIFIERS_TO_LIFT_IN_INTERFACE = listOf(KtTokens.PRIVATE_KEYWORD, KtTokens.PROTECTED_KEYWORD, KtTokens.INTERNAL_KEYWORD)

private val CONSTRUCTOR_VAL_VAR_MODIFIERS = listOf(
    KtTokens.OPEN_KEYWORD,
    KtTokens.FINAL_KEYWORD,
    KtTokens.OVERRIDE_KEYWORD,
    KtTokens.PUBLIC_KEYWORD,
    KtTokens.INTERNAL_KEYWORD,
    KtTokens.PROTECTED_KEYWORD,
    KtTokens.PRIVATE_KEYWORD,
    KtTokens.LATEINIT_KEYWORD
)

internal class K2PullUpHelper(
    private val javaData: PullUpData,
    private val data: K2PullUpData,
) : PullUpHelper<MemberInfoBase<PsiMember>> {
    private fun KtExpression.isMovable(): Boolean = accept(
        object : KtVisitor<Boolean, Nothing?>() {
            override fun visitKtElement(element: KtElement, arg: Nothing?): Boolean =
                element.allChildren.filterIsInstance<KtElement>().all { it.accept(this, arg) }

            override fun visitKtFile(file: KtFile, data: Nothing?): Boolean = false

            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, arg: Nothing?): Boolean = analyze(expression) {
                val resolvedCall = expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() ?: return true
                val partiallyAppliedSymbol = resolvedCall.partiallyAppliedSymbol
                val receiverValue = partiallyAppliedSymbol.dispatchReceiver ?: partiallyAppliedSymbol.extensionReceiver
                val receiver = (receiverValue as? KaExplicitReceiverValue)?.expression
                if (receiver != null && receiver !is KtThisExpression && receiver !is KtSuperExpression) return true

                var symbol: KaDeclarationSymbol? = resolvedCall.symbol
                if (symbol is KaConstructorSymbol) {
                    symbol = symbol.containingDeclaration
                }
                // todo: local functions
                if (symbol is KaValueParameterSymbol) return true
                if (symbol is KaNamedClassSymbol && !symbol.isInner) return true
                if (symbol is KaCallableSymbol) {
                    if (symbol.sourcePsi() in propertiesToMoveInitializers) return true
                    symbol = symbol.containingDeclaration
                }
                symbol is KaPackageSymbol || isSubClassOf(
                    subClass = data.getTargetClassSymbol(analysisSession = this),
                    superClass = symbol,
                )
            }
        }, null
    )

    private fun KaSession.getCommonInitializer(
        currentInitializer: KtExpression?,
        scope: KtBlockExpression?,
        propertySymbol: KaPropertySymbol,
        elementsToRemove: MutableSet<KtElement>
    ): KtExpression? {
        if (scope == null) return currentInitializer

        var initializerCandidate: KtExpression? = null

        for (statement in scope.statements) {
            statement.asAssignment()?.let {
                val lhs = KtPsiUtil.safeDeparenthesize(it.left ?: return@let)
                val receiver = (lhs as? KtQualifiedExpression)?.receiverExpression
                if (receiver != null && receiver !is KtThisExpression) return@let

                val resolvedCall = lhs.resolveToCall()?.successfulVariableAccessCall() ?: return@let
                if (resolvedCall.symbol != propertySymbol) return@let

                if (initializerCandidate == null) {
                    if (currentInitializer == null) {
                        if (!statement.isMovable()) return null

                        initializerCandidate = statement
                        elementsToRemove.add(statement)
                    } else {
                        if (!statement.isSemanticMatch(currentInitializer)) return null

                        initializerCandidate = currentInitializer
                        elementsToRemove.add(statement)
                    }
                } else if (!statement.isSemanticMatch(initializerCandidate)) return null
            }
        }

        return initializerCandidate
    }

    private data class InitializerInfo(
        val initializer: KtExpression?,
        val usedProperties: Set<KtProperty>,
        val usedParameters: Set<KtParameter>,
        val elementsToRemove: Set<KtElement>,
    )

    private fun KaSession.getInitializerInfo(
        property: KtProperty,
        propertySymbol: KaPropertySymbol,
        targetConstructor: KtElement,
    ): InitializerInfo? {
        val sourceConstructors = targetToSourceConstructors[targetConstructor] ?: return null
        val elementsToRemove = LinkedHashSet<KtElement>()
        val commonInitializer = sourceConstructors.fold(null as KtExpression?) { commonInitializer, constructor ->
            val body = (constructor as? KtSecondaryConstructor)?.bodyExpression
            getCommonInitializer(commonInitializer, body, propertySymbol, elementsToRemove)
        }
        if (commonInitializer == null) {
            elementsToRemove.clear()
        }

        val usedProperties = LinkedHashSet<KtProperty>()
        val usedParameters = LinkedHashSet<KtParameter>()
        val visitor = object : KtTreeVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                val resolvedCall = expression.resolveToCall()?.singleVariableAccessCall() ?: return
                val partiallyAppliedSymbol = resolvedCall.partiallyAppliedSymbol
                val receiverValue = partiallyAppliedSymbol.dispatchReceiver ?: partiallyAppliedSymbol.extensionReceiver
                val receiver = (receiverValue as? KaExplicitReceiverValue)?.expression
                if (receiver != null && receiver !is KtThisExpression) return
                when (val target = resolvedCall.symbol.sourcePsi<KtCallableDeclaration>()) {
                    is KtParameter -> usedParameters.add(target)
                    is KtProperty -> usedProperties.add(target)
                }
            }
        }
        commonInitializer?.accept(visitor)
        if (targetConstructor == ((data.targetClass as? KtClass)?.primaryConstructor ?: data.targetClass)) {
            property.initializer?.accept(visitor)
        }

        return InitializerInfo(commonInitializer, usedProperties, usedParameters, elementsToRemove)
    }

    private val propertiesToMoveInitializers = with(data) {
        analyze(sourceClass) {
            membersToMove.filterIsInstance<KtProperty>().filter {
                isBackingFieldRequired(it)
            }
        }
    }

    private val targetToSourceConstructors: LinkedHashMap<KtElement, MutableList<KtElement>> = analyze(data.sourceClass) {
        LinkedHashMap<KtElement, MutableList<KtElement>>().let { result ->
            if (!data.isInterfaceTarget && data.targetClass is KtClass) {
                result[data.targetClass.primaryConstructor ?: data.targetClass] = ArrayList()
                data.sourceClass.accept(object : KtTreeVisitorVoid() {
                    private fun processConstructorReference(expression: KtReferenceExpression, callingConstructorElement: KtElement) {
                        val symbol = expression.resolveExpression()
                        val constructorElement = symbol?.psi ?: return
                        if (constructorElement == data.targetClass || (constructorElement as? KtConstructor<*>)?.getContainingClassOrObject() == data.targetClass) {
                            result.getOrPut(constructorElement as KtElement) { ArrayList() }.add(callingConstructorElement)
                        }
                    }

                    override fun visitSuperTypeCallEntry(specifier: KtSuperTypeCallEntry) {
                        val constructorRef = specifier.calleeExpression.constructorReferenceExpression ?: return
                        val containingClass = specifier.getStrictParentOfType<KtClassOrObject>() ?: return
                        val callingConstructorElement = containingClass.primaryConstructor ?: containingClass
                        processConstructorReference(constructorRef, callingConstructorElement)
                    }

                    override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
                        val constructorRef = constructor.getDelegationCall().calleeExpression ?: return
                        processConstructorReference(constructorRef, constructor)
                    }
                })
            }
            result
        }
    }

    private val targetConstructorToPropertyInitializerInfoMap = analyze(data.sourceClass) {
        LinkedHashMap<KtElement, Map<KtProperty, InitializerInfo>>().let { result ->
            for (targetConstructor in targetToSourceConstructors.keys) {
                val propertyToInitializerInfo = LinkedHashMap<KtProperty, InitializerInfo>()
                for (property in propertiesToMoveInitializers) {
                    val propertySymbol = property.symbol as? KaPropertySymbol ?: continue
                    propertyToInitializerInfo[property] = getInitializerInfo(property, propertySymbol, targetConstructor) ?: continue
                }
                val unmovableProperties = RefactoringUtil.transitiveClosure(object : RefactoringUtil.Graph<KtProperty> {
                    override fun getVertices(): MutableSet<KtProperty> = propertyToInitializerInfo.keys
                    override fun getTargets(source: KtProperty): Set<KtProperty>? = propertyToInitializerInfo[source]?.usedProperties
                }) { !propertyToInitializerInfo.containsKey(it) }

                propertyToInitializerInfo.keys.removeAll(unmovableProperties)
                result[targetConstructor] = propertyToInitializerInfo
            }
            result
        }
    }

    private var dummyField: PsiField? = null

    private fun addMovedMember(newMember: KtNamedDeclaration) {
        if (newMember is KtProperty) {
            // Add dummy light field since PullUpProcessor won't invoke moveFieldInitializations() if no PsiFields are present
            if (dummyField == null) {
                val factory = JavaPsiFacade.getElementFactory(newMember.project)
                val dummyField = object : LightField(
                    newMember.manager,
                    factory.createField("dummy", PsiTypes.booleanType()),
                    factory.createClass("Dummy"),
                ) {
                    // Prevent processing by JavaPullUpHelper
                    override fun getLanguage(): KotlinLanguage = KotlinLanguage.INSTANCE
                }
                javaData.movedMembers.add(dummyField)
            }
        }

        when (newMember) {
            is KtProperty, is KtNamedFunction -> {
                newMember.getRepresentativeLightMethod()?.let { javaData.movedMembers.add(it) }
            }

            is KtClassOrObject -> {
                newMember.toLightClass()?.let { javaData.movedMembers.add(it) }
            }
        }
    }

    private fun liftVisibility(declaration: KtNamedDeclaration, ignoreUsages: Boolean = false) {
        val newModifier = if (data.isInterfaceTarget) KtTokens.PUBLIC_KEYWORD else KtTokens.PROTECTED_KEYWORD
        val modifiersToLift = if (data.isInterfaceTarget) MODIFIERS_TO_LIFT_IN_INTERFACE else MODIFIERS_TO_LIFT_IN_SUPERCLASS
        val currentModifier = declaration.visibilityModifierTypeOrDefault()
        if (currentModifier !in modifiersToLift) return
        if (ignoreUsages || willBeUsedInSourceClass(declaration, data.sourceClass, data.membersToMove)) {
            if (newModifier != KtTokens.DEFAULT_VISIBILITY_KEYWORD) {
                declaration.addModifier(newModifier)
            } else {
                declaration.removeModifier(currentModifier)
            }
        }
    }

    override fun setCorrectVisibility(info: MemberInfoBase<PsiMember>) {
        val member = info.member.namedUnwrappedElement as? KtNamedDeclaration ?: return

        if (data.isInterfaceTarget) {
            member.removeModifier(KtTokens.PUBLIC_KEYWORD)
        }

        val modifiersToLift = if (data.isInterfaceTarget) MODIFIERS_TO_LIFT_IN_INTERFACE else MODIFIERS_TO_LIFT_IN_SUPERCLASS
        if (member.visibilityModifierTypeOrDefault() in modifiersToLift) {
            member.accept(object : KtVisitorVoid() {
                override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                    when (declaration) {
                        is KtClass -> {
                            liftVisibility(declaration)
                            declaration.declarations.forEach { it.accept(this) }
                        }

                        is KtNamedFunction, is KtProperty -> {
                            liftVisibility(declaration, declaration == member && info.isToAbstract)
                        }
                    }
                }
            })
        }
    }

    override fun encodeContextInfo(info: MemberInfoBase<PsiMember>): Unit = Unit

    private fun KaSession.fixOverrideAndGetClashingSuper(
        sourceMember: KtCallableDeclaration,
        targetMember: KtCallableDeclaration,
    ): KtCallableDeclaration? {
        val callableSymbol = sourceMember.symbol as KaCallableSymbol

        if (callableSymbol.allOverriddenSymbols.none()) {
            targetMember.removeOverrideModifier()
            return null
        }

        val clashingSuperSymbol = getClashingMemberInTargetClass(data, callableSymbol) ?: return null
        if (clashingSuperSymbol.allOverriddenSymbols.none()) {
            targetMember.removeOverrideModifier()
        }
        return clashingSuperSymbol.psi as? KtCallableDeclaration
    }

    @OptIn(KaExperimentalApi::class)
    private fun moveSuperInterface(member: KtNamedDeclaration, substitutor: PsiSubstitutor) {
        val realMemberPsi = (member as? KtPsiClassWrapper)?.psiClass ?: member

        val currentSpecifier = allowAnalysisFromWriteActionInEdt(member) {
            analyze(member) {
                val classSymbol = member.symbol as? KaClassSymbol ?: return
                getSuperTypeEntryBySymbol(
                    data.sourceClass,
                    classSymbol,
                ) ?: return
            }
        }
        when (data.targetClass) {
            is KtClass -> {
                allowAnalysisFromWriteActionInEdt(data.sourceClass) {
                    analyze(data.sourceClass) {
                        addSuperTypeEntry(
                            currentSpecifier,
                            data.targetClass,
                            data.getSourceToTargetClassSubstitutor(analysisSession = this),
                        )
                    }
                }
                data.sourceClass.removeSuperTypeListEntry(currentSpecifier)
            }

            is PsiClass -> {
                val elementFactory = JavaPsiFacade.getElementFactory(member.project)

                val sourcePsiClass = data.sourceClass.toLightClass() ?: return
                val superRef =
                    sourcePsiClass.implementsList?.referenceElements?.firstOrNull { it.resolve()?.unwrapped == realMemberPsi } ?: return
                val superTypeForTarget = substitutor.substitute(elementFactory.createType(superRef))

                data.sourceClass.removeSuperTypeListEntry(currentSpecifier)

                allowAnalysisFromWriteActionInEdt(data.sourceClass) {
                    analyze(data.sourceClass) {
                        val classSymbol = member.symbol as KaClassSymbol
                        val targetClassSymbol = data.getTargetClassSymbol(analysisSession = this)
                        if (targetClassSymbol.isSubClassOf(classSymbol)) return
                    }
                }

                val refList = if (data.isInterfaceTarget) data.targetClass.extendsList else data.targetClass.implementsList
                refList?.add(elementFactory.createReferenceFromText(superTypeForTarget.canonicalText, null))
            }
        }
        return
    }

    private fun removeOriginalMemberOrAddOverride(member: KtCallableDeclaration) {
        if (member.isAbstract()) {
            member.deleteWithCompanion()
        } else {
            member.addModifier(KtTokens.OVERRIDE_KEYWORD)
            KtTokens.VISIBILITY_MODIFIERS.types.forEach { member.removeModifier(it as KtModifierKeywordToken) }
            (member as? KtNamedFunction)?.valueParameters?.forEach { it.dropDefaultValue() }
        }
    }

    private fun moveToJavaClass(member: KtNamedDeclaration, substitutor: PsiSubstitutor) {
        if (!(data.targetClass is PsiClass && member.canMoveMemberToJavaClass(data.targetClass))) return

        val project = member.project
        val elementFactory = JavaPsiFacade.getElementFactory(project)
        val lightMethod = member.getRepresentativeLightMethod()!!

        val movedMember = when (member) {
            is KtProperty, is KtParameter -> {
                val newType = substitutor.substitute(lightMethod.returnType)
                val newField = createJavaField(member, data.targetClass)
                newField.typeElement?.let { typeElement ->
                    typeElement.replace(
                        elementFactory.createTypeElement(newType.annotate {
                            typeElement.annotations
                        })
                    )
                }
                if (member.isCompanionMemberOf(data.sourceClass)) {
                    newField.modifierList?.setModifierProperty(PsiModifier.STATIC, true)
                }
                if (member is KtParameter) {
                    (member.parent as? KtParameterList)?.removeParameter(member)
                } else {
                    member.deleteWithCompanion()
                }
                newField
            }

            is KtNamedFunction -> {
                val newReturnType = substitutor.substitute(lightMethod.returnType)
                val newParameterTypes = lightMethod.parameterList
                    .parameters
                    .map { substitutor.substitute(it.type) }
                val objectType = PsiType.getJavaLangObject(PsiManager.getInstance(project), GlobalSearchScope.allScope(project))
                val newTypeParameterBounds = lightMethod.typeParameters.map {
                    it.superTypes.map { type -> substitutor.substitute(type) as? PsiClassType ?: objectType }
                }
                val newMethod = createJavaMethod(member, data.targetClass)
                RefactoringUtil.makeMethodAbstract(data.targetClass, newMethod)
                newMethod.returnTypeElement?.let { returnTypeElement ->
                    returnTypeElement.replace(
                        elementFactory.createTypeElement(newReturnType.annotate {
                            returnTypeElement.annotations
                        })
                    )
                }

                newMethod.parameterList.parameters.zip(newParameterTypes) { parameter, newParameterType ->
                    parameter.typeElement?.replace(
                        elementFactory.createTypeElement(newParameterType.annotate {
                            parameter.type.annotations
                        })
                    )
                }
                newMethod.typeParameters.forEachIndexed { i, typeParameter ->
                    typeParameter.extendsList.referenceElements.forEachIndexed { j, referenceElement ->
                        referenceElement.replace(elementFactory.createReferenceElementByType(newTypeParameterBounds[i][j]))
                    }
                }
                removeOriginalMemberOrAddOverride(member)
                if (!data.isInterfaceTarget && !data.targetClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    data.targetClass.modifierList?.setModifierProperty(PsiModifier.ABSTRACT, true)
                }
                newMethod
            }

            else -> return
        }
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(movedMember)
    }

    @OptIn(KaExperimentalApi::class, KaAllowAnalysisOnEdt::class)
    override fun move(info: MemberInfoBase<PsiMember>, substitutor: PsiSubstitutor) {
        val member = info.member.toKtDeclarationWrapperAware() ?: return

        if ((member is KtClass || member is KtPsiClassWrapper) && info.overrides != null) {
            moveSuperInterface(member, substitutor)
            return
        }

        val targetClass = data.targetClass
        if (targetClass is PsiClass) {
            moveToJavaClass(member, substitutor)
            return
        } else if (targetClass !is KtClass) return

        val markedElements = allowAnalysisFromWriteActionInEdt(member) {
            analyze(member) {
                markElements(
                    member,
                    data.sourceClass,
                    targetClass,
                    data.getSourceToTargetClassSubstitutor(analysisSession = this),
                )
            }
        }
        val memberCopy = member.copy() as KtNamedDeclaration

        fun moveClassOrObject(member: KtClassOrObject, memberCopy: KtClassOrObject): KtClassOrObject {
            if (data.isInterfaceTarget) {
                memberCopy.removeModifier(KtTokens.INNER_KEYWORD)
            }

            val movedMember = addMemberToTarget(memberCopy, targetClass) as KtClassOrObject
            member.deleteWithCompanion()
            return movedMember
        }

        fun moveCallableMember(member: KtCallableDeclaration, memberCopy: KtCallableDeclaration): KtCallableDeclaration {
            val movedMember: KtCallableDeclaration
            val clashingSuper = allowAnalysisFromWriteActionInEdt(member) {
                analyze(member) {
                    fixOverrideAndGetClashingSuper(member, memberCopy)
                }
            }

            val psiFactory = KtPsiFactory(member.project)

            val originalIsAbstract = member.hasModifier(KtTokens.ABSTRACT_KEYWORD)
            val toAbstract = when {
                info.isToAbstract -> true
                !data.isInterfaceTarget -> false
                member is KtProperty -> member.mustBeAbstractInInterface()
                else -> false
            }

            val classToAddTo =
                if (member.isCompanionMemberOf(data.sourceClass)) targetClass.getOrCreateCompanionObject() else targetClass

            if (toAbstract) {
                if (!originalIsAbstract) {
                    val renderedType = allowAnalysisFromWriteActionInEdt(member) {
                        analyze(member) {
                            computeAndRenderReturnType(
                                member.symbol as KaCallableSymbol,
                                memberCopy,
                                data.getSourceToTargetClassSubstitutor(analysisSession = this),
                            )
                        }
                    }
                    if (renderedType != null) {
                        memberCopy.typeReference = KtPsiFactory(member.project).createType(renderedType)
                    }
                    makeAbstract(memberCopy, targetClass)
                }

                movedMember = doAddCallableMember(memberCopy, clashingSuper, classToAddTo)
                if (member.typeReference == null) {
                    movedMember.typeReference?.let(::shortenReferences)
                }
                if (movedMember.nextSibling.anyDescendantOfType<PsiComment>()) {
                    movedMember.parent.addAfter(psiFactory.createNewLine(), movedMember)
                }

                removeOriginalMemberOrAddOverride(member)
            } else {
                movedMember = doAddCallableMember(memberCopy, clashingSuper, classToAddTo)
                if (member is KtParameter && movedMember is KtParameter) {
                    member.valOrVarKeyword?.delete()
                    CONSTRUCTOR_VAL_VAR_MODIFIERS.forEach { member.removeModifier(it) }

                    allowAnalysisFromWriteActionInEdt(data.sourceClass) {
                        analyze(data.sourceClass) {
                            val superEntry = data.getSuperEntryForTargetClass(analysisSession = this)
                            val superResolvedCall = superEntry?.resolveToCall()?.singleFunctionCallOrNull()
                            if (superResolvedCall != null) {
                                val superCall = if (superEntry !is KtSuperTypeCallEntry || superEntry.valueArgumentList == null) {
                                    superEntry.replaced(psiFactory.createSuperTypeCallEntry("${superEntry.text}()"))
                                } else superEntry
                                val argumentList = superCall.valueArgumentList!!

                                val parameterIndex = movedMember.parameterIndex()
                                val prevParameterSymbol = superResolvedCall.symbol.valueParameters.getOrNull(parameterIndex - 1)


                                val prevArgument = prevParameterSymbol?.asSignature()?.let { prevParameterSignature ->
                                    superResolvedCall.argumentMapping.reverse()[prevParameterSignature]
                                }?.parent as? KtValueArgument
                                val newArgumentName =
                                    if (prevArgument != null && prevArgument.isNamed()) identifier(member.name!!) else null
                                val newArgument = psiFactory.createArgument(psiFactory.createExpression(member.name!!), newArgumentName)
                                if (prevArgument == null) {
                                    argumentList.addArgument(newArgument)
                                } else {
                                    argumentList.addArgumentAfter(newArgument, prevArgument)
                                }
                            }
                        }
                    }
                } else {
                    member.deleteWithCompanion()
                }
            }

            if (originalIsAbstract && data.isInterfaceTarget) {
                movedMember.removeModifier(KtTokens.ABSTRACT_KEYWORD)
            }

            if (movedMember.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
                targetClass.makeAbstract()
            }
            return movedMember
        }

        try {
            val movedMember = when (member) {
                is KtCallableDeclaration -> moveCallableMember(member, memberCopy as KtCallableDeclaration)
                is KtClassOrObject -> moveClassOrObject(member, memberCopy as KtClassOrObject)
                else -> return
            }

            movedMember.modifierList?.reformatted()

            applyMarking(movedMember, targetClass)
            addMovedMember(movedMember)
        } finally {
            clearMarking(markedElements)
        }
    }

    override fun postProcessMember(member: PsiMember) {
        val declaration = member.unwrapped as? KtNamedDeclaration ?: return
        KotlinRenameRefactoringSupport.getInstance().dropOverrideKeywordIfNecessary(declaration)
    }

    @OptIn(KaExperimentalApi::class)
    override fun moveFieldInitializations(movedFields: LinkedHashSet<PsiField>) {
        val psiFactory = KtPsiFactory(data.sourceClass.project)

        fun KtClassOrObject.getOrCreateClassInitializer(): KtAnonymousInitializer {
            getOrCreateBody().declarations.lastOrNull { it is KtAnonymousInitializer }?.let { return it as KtAnonymousInitializer }
            return addDeclaration(psiFactory.createAnonymousInitializer())
        }

        fun KtElement.getConstructorBodyBlock(): KtBlockExpression? = when (this) {
            is KtClassOrObject -> {
                getOrCreateClassInitializer().body
            }

            is KtPrimaryConstructor -> {
                getContainingClassOrObject().getOrCreateClassInitializer().body
            }

            is KtSecondaryConstructor -> {
                bodyExpression ?: add(psiFactory.createEmptyBody())
            }

            else -> null
        } as? KtBlockExpression

        fun KtClassOrObject.getDelegatorToSuperCall(): KtSuperTypeCallEntry? {
            return superTypeListEntries.singleOrNull { it is KtSuperTypeCallEntry } as? KtSuperTypeCallEntry
        }

        fun addUsedParameters(constructorElement: KtElement, info: InitializerInfo) {
            if (info.usedParameters.isEmpty()) return
            val constructor: KtConstructor<*> = when (constructorElement) {
                is KtConstructor<*> -> constructorElement
                is KtClass -> constructorElement.createPrimaryConstructorIfAbsent()
                else -> return
            }

            with(constructor.getValueParameterList()!!) {
                info.usedParameters.forEach {
                    val newParameter = addParameter(it)

                    val renderedType = analyze(it) {
                        val originalType = it.symbol.returnType
                        data.getSourceToTargetClassSubstitutor(analysisSession = this)
                            .substitute(originalType)
                            .render(position = Variance.INVARIANT)
                    }

                    newParameter.typeReference = KtPsiFactory(newParameter.project).createType(renderedType)
                    shortenReferences(newParameter.typeReference!!)
                }
            }
            targetToSourceConstructors[constructorElement]!!.forEach {
                val superCall: KtCallElement? = when (it) {
                    is KtClassOrObject -> it.getDelegatorToSuperCall()
                    is KtPrimaryConstructor -> it.getContainingClassOrObject().getDelegatorToSuperCall()
                    is KtSecondaryConstructor -> {
                        if (it.hasImplicitDelegationCall()) {
                            it.replaceImplicitDelegationCallWithExplicit(false)
                        } else {
                            it.getDelegationCall()
                        }
                    }

                    else -> null
                }
                superCall?.valueArgumentList?.let { args ->
                    info.usedParameters.forEach { parameter ->
                        args.addArgument(psiFactory.createArgument(psiFactory.createExpression(parameter.name ?: "_")))
                    }
                }
            }
        }

        for ((constructorElement, propertyToInitializerInfo) in targetConstructorToPropertyInitializerInfoMap.entries) {
            val properties = propertyToInitializerInfo.keys.sortedWith(
                Comparator { property1, property2 ->
                    val info1 = propertyToInitializerInfo[property1]!!
                    val info2 = propertyToInitializerInfo[property2]!!
                    when {
                        property2 in info1.usedProperties -> -1
                        property1 in info2.usedProperties -> 1
                        else -> 0
                    }
                })

            for (oldProperty in properties) {
                val info = propertyToInitializerInfo.getValue(oldProperty)

                allowAnalysisFromWriteActionInEdt(constructorElement) {
                    analyze(constructorElement) {
                        addUsedParameters(constructorElement, info)
                    }
                }

                info.initializer?.let {
                    val body = constructorElement.getConstructorBodyBlock()
                    body?.addAfter(it, body.statements.lastOrNull() ?: body.lBrace!!)
                }
                info.elementsToRemove.forEach { it.delete() }
            }
        }
    }

    override fun updateUsage(element: PsiElement): Unit = Unit
}

private fun KaSession.isSubClassOf(
    subClass: KaDeclarationSymbol?,
    superClass: KaDeclarationSymbol?,
): Boolean = subClass is KaClassSymbol && superClass is KaClassSymbol && subClass.isSubClassOf(superClass)

@OptIn(KaExperimentalApi::class)
private fun KaSession.addSuperTypeEntry(
    delegator: KtSuperTypeListEntry,
    targetClass: KtClassOrObject,
    substitutor: KaSubstitutor,
) {
    val referencedType = delegator.typeReference?.type
    val referencedClass = referencedType?.expandedSymbol ?: return

    val targetClassSymbol = targetClass.symbol as KaClassSymbol


    if (targetClassSymbol == referencedClass || targetClassSymbol.isDirectSubClassOf(referencedClass)) return

    val typeInTargetClass = substitutor.substitute(referencedType)
    if (typeInTargetClass is KaErrorType) return

    val renderedType = typeInTargetClass.render(position = Variance.INVARIANT)
    val newSpecifier = KtPsiFactory(targetClass.project).createSuperTypeEntry(renderedType)
    shortenReferences(targetClass.addSuperTypeListEntry(newSpecifier))
}
