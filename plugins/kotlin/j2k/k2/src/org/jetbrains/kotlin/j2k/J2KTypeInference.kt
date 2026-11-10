// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.codeInsight.NullabilitySource
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.codeInspection.dataFlow.DfaPsiUtil
import com.intellij.codeInspection.dataFlow.DfaUtil
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind.NullabilityProblem
import com.intellij.codeInspection.dataFlow.NullabilityUtil
import com.intellij.codeInspection.dataFlow.StandardDataFlowRunner
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener
import com.intellij.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor
import com.intellij.codeInspection.dataFlow.lang.DfaListener
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState
import com.intellij.codeInspection.dataFlow.lang.ir.FlushFieldsInstruction
import com.intellij.codeInspection.dataFlow.lang.ir.ReturnInstruction
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.JavaTokenType
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiArrayAccessExpression
import com.intellij.psi.PsiArrayInitializerExpression
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiCall
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiCapturedWildcardType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiConditionalExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiForeachStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiRecordComponent
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReferenceParameterList
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiSwitchExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiVariable
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.PsiExtensibleClass
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.JavaPsiRecordUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.JavaPsiConstructorUtil
import com.intellij.util.ThreeState
import com.siyeh.ig.psiutils.ExpressionUtils
import com.siyeh.ig.psiutils.MethodCallUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.j2k.conversions.toKotlinMutableTypesMap
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtParameter
import com.intellij.codeInsight.Nullability as JavaNullability

/**
 * Infers the nullability and the mutability of the types in [file] for J2K.
 * [inferredTypeArguments] are the type arguments that J2K prints for the calls without explicit type arguments.
 */
internal fun inferTypes(
    file: PsiJavaFile,
    inferredTypeArguments: Map<PsiMethodCallExpression, PsiReferenceParameterList> = emptyMap(),
): TypeInferenceInfo = J2KTypeInference(file, inferredTypeArguments).typeInferenceInfo()

@ApiStatus.Internal
class J2KTypeInference(
    private val file: PsiFile,
    private val inferredTypeArguments: Map<PsiMethodCallExpression, PsiReferenceParameterList> = emptyMap(),
) {
    /**
     * A type in the flow graph: the type of a declaration, of a call result, or of a type argument.
     * [platformNullability] comes from an annotation or a framework, and the engine keeps it.
     * [dataflowNullability] is the join of the values that flow in.
     * [needsNotNull] tells that a use needs a not-null value.
     * [mutable] tells that the collection must have a mutable Kotlin type, and then also each of [mutabilitySources] must have it.
     * [mutabilityFixed] tells that a Kotlin declaration gives [mutable], and the engine keeps it.
     */
    private class TypeNode(val type: PsiType, var platformNullability: Nullability?, val wildcard: PsiWildcardType? = null) {
        var dataflowNullability: Nullability? = null
        var needsNotNull = isOptional(type)
        var result = Nullability.Default
        var printedByCaller = false
        val className: String? = PsiUtil.resolveClassInClassTypeOnly(type)?.qualifiedName
        var mutable = className in toKotlinMutableTypesMap
        var mutabilityFixed = false
        val targets = LinkedHashSet<TypeNode>()
        val sources = LinkedHashSet<TypeNode>()
        val mutabilitySources = LinkedHashSet<TypeNode>()
        var elements: List<TypeNode> = emptyList()

        fun join(other: Nullability): Boolean {
            val current = dataflowNullability
            val joined = when {
                current == null || current == other -> other
                current == Nullability.Nullable || other == Nullability.Nullable -> Nullability.Nullable
                else -> Nullability.Default
            }
            if (joined == current) return false
            dataflowNullability = joined
            return true
        }

        fun makeReadOnlyCandidate() {
            mutable = false
            elements.forEach { it.makeReadOnlyCandidate() }
        }

        fun makeMutable() {
            if (!mutabilityFixed) mutable = isMappedCollection(type)
            elements.forEach { it.makeMutable() }
        }

        fun descendants(): List<TypeNode> = elements.flatMap { listOf(it) + it.descendants() }

        fun decide(): Nullability = platformNullability ?: when {
            dataflowNullability == Nullability.Nullable -> Nullability.Nullable
            needsNotNull || dataflowNullability == Nullability.NotNull -> Nullability.NotNull
            else -> Nullability.Default
        }
    }

    private class ElementLink(val source: TypeNode, val target: TypeNode, val owner: TypeNode, val index: Int)

    private val nodes = LinkedHashMap<PsiModifierListOwner, TypeNode>()

    /** Keyed by the method for a result without type arguments, otherwise by the call, the `new` expression, or the explicit type argument. */
    private val callNodes = HashMap<PsiElement, TypeNode>()
    private val notPrinted = LinkedHashSet<PsiModifierListOwner>()
    private val methods = ArrayList<PsiMethod>()
    private val sinks = HashMap<PsiExpression, TypeNode>()
    private val reached = HashSet<PsiExpression>()
    private val expressionDfTypes = HashMap<PsiExpression, DfType>()

    /** The expressions whose data flow facts the engine keeps, because the converter reads only them. */
    private val dataflowQueries = HashSet<PsiExpression>()

    /** The type arguments that give the types of lambda parameters, see [linkLambdaParameters]. */
    private val lambdaParameterTypeArguments = HashMap<PsiParameter, TypeNode>()

    /** The lambda parameters, for the mutability only: the converter prints their nullability from [lambdaParameterTypeArguments]. */
    private val lambdaParameterNodes = HashMap<PsiParameter, TypeNode>()
    private val elementLinks = ArrayList<ElementLink>()
    private val unseenArgumentOwners = ArrayList<TypeNode>()
    private val overriders = HashMap<PsiMethod, List<PsiMethod>>()
    private val nullComparisons = ArrayList<PsiBinaryExpression>()
    private val typeElements = ArrayList<PsiTypeElement>()
    private val returnStatements = HashMap<PsiMethod, MutableList<PsiReturnStatement>>()
    private val typeElementNodes = HashMap<PsiTypeElement, TypeNode>()
    private val originals = OriginalJavaSemanticResolver()
    private val originalFile by lazy { originals.originalElementOrSelf(file) }

    val decisions: Map<PsiModifierListOwner, Nullability> by lazy {
        val visitor = FileVisitor(inFile = true)
        file.accept(visitor)
        for (component in visitor.components) shareWithImplicitMembers(component)
        for (step in visitor.dataflowSteps) step()
        addDeclarationDataflow()
        for (analysis in visitor.bodyAnalyses) analysis()
        for (comparison in nullComparisons) addNullComparisonDataflow(comparison)
        for ((expression, node) in sinks) {
            if (expression !in reached) addStaticDataflow(expression, node)
        }
        for (reference in visitor.dereferences) {
            if (reference !in reached) variableNode(reference)?.needsNotNull = true
        }
        solve()
        solveMutability()
        resolve()
        nodes.filterKeys { it !in notPrinted }.mapValues { it.value.result }
    }

    internal fun typeInferenceInfo(): TypeInferenceInfo {
        val decided = decisions.keys
        val info = TypeInferenceInfo()
        writeAnnotatedTypes(info)
        for (owner in decided) {
            val node = nodes.getValue(owner)
            info.write(node.type, node.result)
            writeElements(info, node)
            info.writeMutability(node.type, node)
        }
        for ((parameter, typeArgument) in lambdaParameterTypeArguments) {
            when (typeArgument.result) {
                Nullability.Nullable -> info.nullableLambdaParameters += parameter
                Nullability.NotNull -> info.notNullLambdaParameters += parameter
                Nullability.Default -> {}
            }
        }
        for ((key, node) in callNodes) {
            if (key is PsiTypeElement && node.result != Nullability.Default) info.write(node.type, node.result)
            if (key is PsiNewExpression || key is PsiTypeElement) writeElements(info, node)
        }
        for ((expression, node) in sinks) {
            if (expression is PsiMethodCallExpression && !node.mutable && isMappedCollection(node.type)) info.readOnlyCalls += expression
        }
        for ((parameter, node) in lambdaParameterNodes) {
            if (!node.mutable && isMappedCollection(node.type)) info.readOnlyLambdaParameters += parameter
        }
        info.expressionDfTypes.putAll(expressionDfTypes)
        return info
    }

    private fun writeAnnotatedTypes(info: TypeInferenceInfo) {
        for (typeElement in typeElements) writeAnnotatedType(info, typeElement)
        val visitor = object : JavaRecursiveElementWalkingVisitor() {
            override fun visitTypeElement(typeElement: PsiTypeElement) {
                super.visitTypeElement(typeElement)
                writeAnnotatedType(info, typeElement)
            }
        }
        for (typeArgumentList in inferredTypeArguments.values) typeArgumentList.accept(visitor)
    }

    private fun writeAnnotatedType(info: TypeInferenceInfo, typeElement: PsiTypeElement) {
        val type = typeElement.type
        info.writeAnnotated(type, DfaPsiUtil.getElementNullabilityForWrite(type, typeElement.parent as? PsiModifierListOwner))
        if (type is PsiArrayType) info.writeAnnotated(type.componentType, type.componentType.nullability.nullability())
        writeAnnotatedArguments(info, type)
    }

    private fun writeAnnotatedArguments(info: TypeInferenceInfo, type: PsiType) {
        if (type is PsiArrayType) return writeAnnotatedArguments(info, type.componentType)
        for (argument in (type as? PsiClassType)?.parameters.orEmpty()) {
            val unwrapped = argument.unwrapWildcard()
            val nullability = unwrapped.nullability
            if (nullability.source() is NullabilitySource.ExplicitAnnotation) info.writeAnnotated(unwrapped, nullability.nullability())
            writeAnnotatedArguments(info, unwrapped)
        }
    }

    private fun PsiType.unwrapWildcard(): PsiType = when (this) {
        is PsiCapturedWildcardType -> wildcard.unwrapWildcard()
        is PsiWildcardType -> bound ?: this
        else -> this
    }

    private fun TypeInferenceInfo.writeAnnotated(type: PsiType, nullability: JavaNullability) {
        val result = nullability.toJ2K() ?: return
        val isTypeParameter = PsiUtil.resolveClassInClassTypeOnly(type) is PsiTypeParameter
        if (isTypeParameter && type.nullability.source() !is NullabilitySource.ExplicitAnnotation) return
        write(type, result)
    }

    private fun writeElements(info: TypeInferenceInfo, node: TypeNode) {
        for (element in node.elements) {
            val type = if (element.wildcard == null) element.type else element.wildcard.bound ?: continue
            val printed = !TypeConversionUtil.isPrimitiveAndNotNull(type) && PsiUtil.resolveClassInClassTypeOnly(type) !is PsiTypeParameter
            if (printed && !element.printedByCaller && element.result != Nullability.Default) info.write(type, element.result)
            info.writeMutability(type, element)
            writeElements(info, element)
        }
    }

    private fun TypeInferenceInfo.writeMutability(type: PsiType, node: TypeNode) {
        if (node.mutable || !isMappedCollection(type)) return
        readOnlyTypes += type
        (type as? PsiClassReferenceType)?.reference?.let { readOnlyElements += it }
    }

    private fun TypeInferenceInfo.write(type: PsiType, result: Nullability) {
        val element = (type as? PsiClassReferenceType)?.reference
        nullableTypes.remove(type)
        notNullTypes.remove(type)
        if (element != null) {
            nullableElements.remove(element)
            notNullElements.remove(element)
        }
        when (result) {
            Nullability.Nullable -> {
                nullableTypes.add(type)
                if (element != null) nullableElements.add(element)
            }

            Nullability.NotNull -> {
                notNullTypes.add(type)
                if (element != null) notNullElements.add(element)
            }

            Nullability.Default -> {}
        }
    }

    /** Visits the file once. A dataflow step waits until the end of the visit, because it can read the node of a later declaration. */
    @Suppress("UnsafeReturnStatementVisitor")
    private inner class FileVisitor(private val inFile: Boolean) : JavaRecursiveElementWalkingVisitor() {
        val components = ArrayList<PsiRecordComponent>()
        val dataflowSteps = ArrayList<() -> Unit>()
        val bodyAnalyses = ArrayList<() -> Unit>()
        val dereferences = ArrayList<PsiReferenceExpression>()

        private fun later(step: () -> Unit) {
            if (inFile) dataflowSteps += step else step()
        }

        override fun visitClass(aClass: PsiClass) {
            if (inFile && isOutsideBodies(aClass) && isConstructed(aClass)) bodyAnalyses += { analyzeConstruction(aClass) }
            super.visitClass(aClass)
            if (inFile) addGeneratedMethodNodes(aClass)
        }

        override fun visitField(field: PsiField) {
            super.visitField(field)
            if (!inFile || field is PsiEnumConstant) return
            val node = addNode(field, field.type) ?: return
            if (field.hasModifierProperty(PsiModifier.PRIVATE) || searchesAllUsers(field)) node.makeReadOnlyCandidate()
            shareTypeElement(field, node)
        }

        override fun visitMethod(method: PsiMethod) {
            if (inFile && isOutsideBodies(method) && !(method.isConstructor && isConstructed(method.containingClass))) {
                bodyAnalyses += { method.body?.let(::analyze) }
            }
            super.visitMethod(method)
            if (!inFile) return
            methods += method
            val returnType = method.returnType
            if (!method.isConstructor && returnType != null && method.containingClass?.isAnnotationType != true) {
                val node = addNode(method, returnType)
                if (node != null && (method.hasModifierProperty(PsiModifier.PRIVATE) || searchesAllUsers(method))) node.makeReadOnlyCandidate()
            }
        }

        override fun visitClassInitializer(initializer: PsiClassInitializer) {
            if (inFile && isOutsideBodies(initializer) && !isConstructed(initializer.containingClass)) bodyAnalyses += { analyze(initializer.body) }
            super.visitClassInitializer(initializer)
        }

        override fun visitParameter(parameter: PsiParameter) {
            super.visitParameter(parameter)
            if (!inFile) return
            when (val scope = parameter.declarationScope) {
                is PsiMethod -> if (!parameter.isVarArgs) {
                    val node = addNode(parameter, parameter.type)
                    if (node != null && !isImplementedByLambdas(scope)) {
                        node.makeReadOnlyCandidate()
                        if (!scope.hasModifierProperty(PsiModifier.PRIVATE)) unseenArgumentOwners += node
                    }
                }

                is PsiForeachStatement -> {
                    addNode(parameter, parameter.type)?.makeReadOnlyCandidate()
                    notPrinted += parameter
                }

                is PsiLambdaExpression -> {
                    val type = parameter.type
                    if (!TypeConversionUtil.isPrimitiveAndNotNull(type) && PsiUtil.resolveClassInClassTypeOnly(type) !is PsiTypeParameter) {
                        lambdaParameterNodes[parameter] = TypeNode(type, null).also {
                            it.elements = createElements(type)
                            it.makeReadOnlyCandidate()
                        }
                    }
                }
            }
        }

        override fun visitLocalVariable(variable: PsiLocalVariable) {
            super.visitLocalVariable(variable)
            if (!inFile) return
            val node = addNode(variable, variable.type) ?: return
            node.makeReadOnlyCandidate()
            shareTypeElement(variable, node)
        }

        override fun visitRecordComponent(recordComponent: PsiRecordComponent) {
            super.visitRecordComponent(recordComponent)
            if (!inFile) return
            addNode(recordComponent, recordComponent.type)
            components += recordComponent
        }

        override fun visitTypeElement(typeElement: PsiTypeElement) {
            super.visitTypeElement(typeElement)
            if (inFile) typeElements += typeElement
        }

        override fun visitReturnStatement(statement: PsiReturnStatement) {
            super.visitReturnStatement(statement)
            if (!inFile) return
            val method = PsiTreeUtil.getParentOfType(statement, true, PsiMethod::class.java, PsiLambdaExpression::class.java, PsiClass::class.java)
            if (method is PsiMethod) returnStatements.getOrPut(method) { ArrayList() } += statement
        }

        override fun visitReferenceExpression(expression: PsiReferenceExpression) {
            super.visitReferenceExpression(expression)
            if (inFile && isDereferenced(expression)) dereferences += expression
        }

        override fun visitVariable(variable: PsiVariable) {
            super.visitVariable(variable)
            later {
                val node = nodes[variable] ?: return@later
                variable.initializer?.let { addSink(it, node) }
            }
        }

        override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
            super.visitAssignmentExpression(expression)
            later {
                val node = expressionNode(expression.lExpression) ?: return@later
                if (expression.operationTokenType == JavaTokenType.EQ) {
                    expression.rExpression?.let { addSink(it, node) }
                } else {
                    node.join(Nullability.NotNull)
                    if (PsiPrimitiveType.getUnboxedType(expression.lExpression.type) != null) node.needsNotNull = true
                }
            }
        }

        override fun visitCallExpression(callExpression: PsiCallExpression) {
            super.visitCallExpression(callExpression)
            later {
                addArgumentSinks(callExpression)
                addMutabilityDataflow(callExpression)
                if (callExpression is PsiMethodCallExpression) {
                    addReceiverElementSinks(callExpression)
                    addEntryMutability(callExpression)
                    addLambdaParameterSources(callExpression)
                }
                // The nodes of the type arguments must exist before the DFA runs
                if (callExpression is PsiMethodCallExpression && typeArgumentElements(callExpression).isNotEmpty()) calleeNode(callExpression)
                // The converter reads the nullability of the arguments of a generic method call
                if (callExpression is PsiMethodCallExpression && callExpression.resolveMethod()?.hasTypeParameters() == true) {
                    dataflowQueries.addAll(callExpression.argumentList.expressions)
                }
            }
        }

        override fun visitTypeCastExpression(expression: PsiTypeCastExpression) {
            super.visitTypeCastExpression(expression)
            later {
                // The converter reads the nullability of the cast operand
                val operand = expression.operand ?: return@later
                dataflowQueries += operand
                (operand as? PsiParenthesizedExpression)?.expression?.let { dataflowQueries += it }
            }
        }

        override fun visitForeachStatement(statement: PsiForeachStatement) {
            super.visitForeachStatement(statement)
            later {
                val variable = nodes[statement.iterationParameter] ?: return@later
                val iterated = expressionNode(statement.iteratedValue) ?: return@later
                val element = if (iterated.type is PsiArrayType) iterated.elements.firstOrNull() else receiverElement(iterated, iterableElementType)
                if (element != null) {
                    edge(element, variable)
                    linkElements(element, variable)
                    flowMutability(element, variable)
                }
            }
        }

        override fun visitEnumConstant(enumConstant: PsiEnumConstant) {
            super.visitEnumConstant(enumConstant)
            later { addArgumentSinks(enumConstant) }
        }

        override fun visitNewExpression(expression: PsiNewExpression) {
            super.visitNewExpression(expression)
            later { if (expression.arrayInitializer != null) newExpressionNode(expression) }
        }

        override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
            super.visitMethodReferenceExpression(expression)
            later {
                val method = expression.resolve() as? PsiMethod ?: return@later
                for (parameter in method.parameterList.parameters) nodes[parameter]?.let(::joinUnknown)
                if (method.name in MUTATING_METHODS) markMutable(expression.qualifierExpression)
                nodes[method]?.mutable = true
            }
        }

        override fun visitBinaryExpression(expression: PsiBinaryExpression) {
            super.visitBinaryExpression(expression)
            later {
                val reference = comparedWithNull(expression) ?: return@later
                nullComparisons += expression
                dataflowQueries += reference
            }
        }
    }

    /** The DFA analyzes a body together with the classes and lambdas inside it, so the engine does not analyze them alone. */
    private fun isOutsideBodies(element: PsiElement): Boolean =
        PsiTreeUtil.getParentOfType(element, true, PsiMethod::class.java, PsiClassInitializer::class.java) == null

    /** The declarations of `List<String> a, b;` have one type object, so the converter prints one mutability for both. */
    private fun shareTypeElement(variable: PsiVariable, node: TypeNode) {
        val typeElement = variable.typeElement ?: return
        val other = typeElementNodes.putIfAbsent(typeElement, node) ?: return
        if (other.type === node.type) shareMutability(other, node)
    }

    private fun shareMutability(first: TypeNode, second: TypeNode) {
        first.mutabilitySources += second
        second.mutabilitySources += first
        for ((firstElement, secondElement) in first.elements.zip(second.elements)) shareMutability(firstElement, secondElement)
    }

    private fun addGeneratedMethodNodes(aClass: PsiClass) {
        if (aClass.isRecord) return
        val written = (aClass as? PsiExtensibleClass)?.ownMethods ?: return
        for (method in aClass.methods) {
            if (method in written || method.body == null) continue
            notPrinted += method
            notPrinted += method.parameterList.parameters
            methods += method
            val returnType = method.returnType
            if (!method.isConstructor && returnType != null) addNode(method, returnType)
            for (parameter in method.parameterList.parameters) {
                if (!parameter.isVarArgs) addNode(parameter, parameter.type)
            }
        }
    }

    private fun isImplementedByLambdas(method: PsiMethod): Boolean = method.body == null && LambdaUtil.isFunctionalClass(method.containingClass)

    private fun shareWithImplicitMembers(component: PsiRecordComponent) {
        val node = nodes[component] ?: return
        val record = component.containingClass ?: return
        val parameters = JavaPsiRecordUtil.findCanonicalConstructor(record)?.parameterList?.parameters.orEmpty()
            .filter { JavaPsiRecordUtil.getComponentForCanonicalConstructorParameter(it) == component }
        val members = listOfNotNull(JavaPsiRecordUtil.getFieldForComponent(component), JavaPsiRecordUtil.getAccessorForRecordComponent(component))
        for (member in members + parameters) {
            if (member in nodes) continue
            nodes[member] = node
            notPrinted += member
        }
    }

    private fun addNode(owner: PsiModifierListOwner, type: PsiType): TypeNode? {
        if (TypeConversionUtil.isPrimitiveAndNotNull(type) || PsiUtil.resolveClassInClassTypeOnly(type) is PsiTypeParameter) return null
        val node = TypeNode(type, DfaPsiUtil.getElementNullabilityForRead(type, owner).toJ2K())
        node.elements = createElements(type)
        nodes[owner] = node
        return node
    }

    private fun createElements(
        type: PsiType,
        receiver: TypeNode? = null,
        elementNullability: Nullability? = null,
        callee: PsiMethod? = null,
    ): List<TypeNode> {
        val arguments = when (type) {
            is PsiArrayType -> listOf(type.componentType)
            is PsiClassType -> type.parameters.asList()
            else -> emptyList()
        }
        val typeParameters = (type as? PsiClassType)?.resolve()?.typeParameters
        val argumentNullability = elementNullability ?: Nullability.NotNull.takeIf { isOptional(type) }
        return arguments.mapIndexed { index, argument ->
            val wildcard = argument as? PsiWildcardType
            val elementType = wildcard?.extendsBound ?: argument
            if (receiver != null && wildcard == null) receiverElement(receiver, elementType)?.let { return@mapIndexed it }
            val printedByCaller = callee != null && (PsiUtil.resolveClassInClassTypeOnly(elementType) as? PsiTypeParameter)?.owner == callee
            val platformNullability = when {
                TypeConversionUtil.isPrimitiveAndNotNull(elementType) -> Nullability.NotNull
                printedByCaller -> null
                else -> elementType.nullability.nullability().toJ2K() ?: argumentNullability ?: boundNullability(typeParameters?.getOrNull(index))
            }
            TypeNode(elementType, platformNullability, wildcard).also {
                it.printedByCaller = printedByCaller
                it.elements = createElements(elementType, receiver, callee = callee)
            }
        }
    }

    private fun boundNullability(typeParameter: PsiTypeParameter?): Nullability? =
        if (typeParameter != null && getTypeParameterNullability(typeParameter) == JavaNullability.NOT_NULL) Nullability.NotNull else null

    private fun linkElements(source: TypeNode, target: TypeNode) {
        if (source === target) return
        for ((sourceElement, targetElement) in source.elements.zip(target.elements)) {
            val wildcard = targetElement.wildcard
            if (wildcard?.isSuper != true) edge(sourceElement, targetElement)
            if (wildcard == null || wildcard.isSuper) edge(targetElement, sourceElement)
            linkElements(sourceElement, targetElement)
        }
    }

    private fun JavaNullability.toJ2K(): Nullability? = when (this) {
        JavaNullability.NOT_NULL -> Nullability.NotNull
        JavaNullability.NULLABLE -> Nullability.Nullable
        else -> null
    }

    private fun addDeclarationDataflow() {
        val generatedBodies = FileVisitor(inFile = false)
        for (owner in notPrinted) (owner as? PsiMethod)?.body?.accept(generatedBodies)

        for (method in methods) {
            val searchesAllUsers = searchesAllUsers(method)
            nodes[method]?.let { node ->
                if (method.body == null && !searchesAllUsers) node.join(Nullability.Default)
                val returns = if (method in notPrinted) PsiUtil.findReturnStatements(method).asList() else returnStatements[method].orEmpty()
                for (statement in returns) statement.returnValue?.let { addSink(it, node) }
            }
            addParameterDataflow(method)
            addOverrideDataflow(method)
            addOverriderDataflow(method)
            if (searchesAllUsers) addProjectDataflow(method)
        }
        for ((owner, node) in nodes) {
            if (owner is PsiField && searchesAllUsers(owner)) addProjectAssignmentDataflow(owner, node)
            if (owner is PsiField && NullabilityUtil.isImplicitlyInitializedNotNull(owner)) node.join(Nullability.NotNull)
        }
    }

    private fun searchesAllUsers(member: PsiMember): Boolean = member.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)

    private fun addSink(expression: PsiExpression, node: TypeNode) {
        when (val stripped = PsiUtil.skipParenthesizedExprDown(expression) ?: return) {
            is PsiConditionalExpression -> {
                stripped.thenExpression?.let { addSink(it, node) }
                stripped.elseExpression?.let { addSink(it, node) }
            }

            is PsiTypeCastExpression -> stripped.operand?.let { addSink(it, node) }
            is PsiSwitchExpression -> for (result in PsiUtil.getSwitchResultExpressions(stripped)) addSink(result, node)
            else -> {
                sinks[stripped] = node
                if (stripped is PsiLambdaExpression) addLambdaResultSinks(stripped, node)
                mutabilitySource(stripped)?.let { node.mutabilitySources += it }
                val source = expressionNode(stripped)
                source?.let { linkElements(it, node) }
                source?.let { flowElementMutability(it, node) }
                val initializer = (stripped as? PsiNewExpression)?.arrayInitializer ?: stripped as? PsiArrayInitializerExpression
                val element = node.elements.firstOrNull()
                if (source == null && initializer != null && element != null) {
                    for (initializerElement in initializer.initializers) addSink(initializerElement, element)
                }
                if (stripped is PsiNewExpression && initializer == null) addNewArrayDataflow(stripped, node)
            }
        }
    }

    private fun flowMutability(source: TypeNode, target: TypeNode) {
        if (source === target) return
        target.mutabilitySources += source
        flowElementMutability(source, target)
    }

    private fun flowElementMutability(source: TypeNode, target: TypeNode) {
        if (source === target) return
        for ((index, elements) in source.elements.zip(target.elements).withIndex()) {
            val (sourceElement, targetElement) = elements
            if (targetElement.wildcard?.isSuper == true) {
                sourceElement.mutabilitySources += targetElement
            } else {
                targetElement.mutabilitySources += sourceElement
                elementLinks += ElementLink(sourceElement, targetElement, target, index)
            }
            flowElementMutability(sourceElement, targetElement)
        }
    }

    /** `MutableMap.entries` has mutable entries, and `Map.entries` has read-only entries. */
    private fun addEntryMutability(call: PsiMethodCallExpression) {
        if (call.methodExpression.referenceName != "entrySet") return
        val map = mutabilitySource(call.methodExpression.qualifierExpression) ?: return
        val entry = expressionNode(call)?.elements?.firstOrNull() ?: return
        entry.mutabilitySources += map
        map.mutabilitySources += entry
    }

    private fun addLambdaParameterSources(call: PsiMethodCallExpression) {
        val receiver = expressionNode(call.methodExpression.qualifierExpression) ?: return
        if (receiver.elements.isEmpty()) return
        val parameters = call.resolveMethod()?.parameterList?.parameters ?: return
        for ((lambdaParameter, type) in lambdaParameterTypes(call, parameters)) {
            val node = lambdaParameterNodes[lambdaParameter] ?: continue
            receiverElement(receiver, type)?.let { flowMutability(it, node) }
        }
    }

    /** Returns each parameter of the lambda arguments of [call] with the type that the functional interface of [parameters] gives it. */
    private fun lambdaParameterTypes(call: PsiMethodCallExpression, parameters: Array<PsiParameter>): List<Pair<PsiParameter, PsiType>> = buildList {
        for ((position, argument) in call.argumentList.expressions.withIndex()) {
            val lambda = PsiUtil.skipParenthesizedExprDown(argument) as? PsiLambdaExpression ?: continue
            val functionalType = parameters.getOrNull(position)?.takeIf { !it.isVarArgs }?.type ?: continue
            val samMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalType) ?: continue
            val substitutor = LambdaUtil.getSubstitutor(samMethod, PsiUtil.resolveGenericsClassInType(functionalType))
            for ((index, lambdaParameter) in lambda.parameterList.parameters.withIndex()) {
                val samParameterType = samMethod.parameterList.parameters.getOrNull(index)?.type ?: continue
                add(lambdaParameter to substitutor.substitute(samParameterType).let { (it as? PsiWildcardType)?.bound ?: it })
            }
        }
    }

    private fun addLambdaResultSinks(lambda: PsiLambdaExpression, node: TypeNode) {
        val method = LambdaUtil.getFunctionalInterfaceMethod(node.type) ?: return
        val resultNode = nodes[method] ?: receiverElement(node, method.returnType) ?: return
        for (result in lambdaResults(lambda)) addSink(result, resultNode)
    }

    private fun lambdaResults(lambda: PsiLambdaExpression): List<PsiExpression> =
        if (LambdaUtil.getFunctionalInterfaceReturnType(lambda) == PsiTypes.voidType()) emptyList() else LambdaUtil.getReturnExpressions(lambda)

    private fun addNewArrayDataflow(expression: PsiNewExpression, node: TypeNode) {
        val dimensions = expression.arrayDimensions
        var element = node.elements.firstOrNull()
        for (index in dimensions.indices) {
            val current = element ?: return
            current.join(if (index == dimensions.lastIndex) Nullability.Nullable else Nullability.NotNull)
            element = current.elements.firstOrNull()
        }
    }

    private fun addReceiverElementSinks(call: PsiMethodCallExpression) {
        val receiver = expressionNode(call.methodExpression.qualifierExpression) ?: return
        if (receiver.elements.isEmpty()) return
        val method = call.resolveMethod() ?: return
        val arguments = call.argumentList.expressions
        for ((index, parameter) in method.parameterList.parameters.withIndex()) {
            if (parameter.isVarArgs) break
            val argument = arguments.getOrNull(index) ?: break
            if (PsiUtil.skipParenthesizedExprDown(argument) in sinks) continue
            receiverElement(receiver, parameter.type)?.let { addSink(argument, it) }
        }
    }

    private fun receiverElement(receiver: TypeNode, type: PsiType?): TypeNode? {
        val typeParameter = PsiUtil.resolveClassInClassTypeOnly(type) as? PsiTypeParameter ?: return null
        val owner = typeParameter.owner as? PsiClass ?: return null
        val receiverClass = PsiUtil.resolveClassInClassTypeOnly(receiver.type) ?: return null
        val mapped = if (owner == receiverClass) {
            typeParameter
        } else {
            if (!receiverClass.isInheritor(owner, true)) return null
            val substitutor = TypeConversionUtil.getSuperClassSubstitutor(owner, receiverClass, PsiSubstitutor.EMPTY)
            PsiUtil.resolveClassInClassTypeOnly(substitutor.substitute(typeParameter)) as? PsiTypeParameter ?: return null
        }
        return receiver.elements.getOrNull(receiverClass.typeParameters.indexOf(mapped))
    }

    private val iterableElementType: PsiType? by lazy {
        val iterable = JavaPsiFacade.getInstance(file.project).findClass(CommonClassNames.JAVA_LANG_ITERABLE, file.resolveScope)
        iterable?.typeParameters?.firstOrNull()?.let { JavaPsiFacade.getElementFactory(file.project).createType(it) }
    }

    private fun addArgumentSinks(call: PsiCall) {
        val arguments = call.argumentList?.expressions ?: return
        val isVarArgCall = MethodCallUtils.isVarArgCall(call)
        for (argument in arguments) {
            val parameter = MethodCallUtils.getParameterForArgument(argument) ?: continue
            if (!parameter.isVarArgs) {
                nodes[parameter]?.let { addSink(argument, it) }
            } else if (!isVarArgCall) {
                variableNode(argument)?.needsNotNull = true
            }
        }
    }

    private fun addMutabilityDataflow(call: PsiCallExpression) {
        val method = call.resolveMethod()
        if (call is PsiMethodCallExpression && method?.name in MUTATING_METHODS) markMutable(call.methodExpression.qualifierExpression)
        val argumentsKept by lazy { method != null && keepsArguments(method) }
        for (argument in call.argumentList?.expressions.orEmpty()) {
            val parameter = MethodCallUtils.getParameterForArgument(argument)
            if (parameter != null && parameter in nodes) continue
            val lambda = PsiUtil.skipParenthesizedExprDown(argument) as? PsiLambdaExpression
            when {
                lambda != null -> lambdaResults(lambda).forEach(::markMutable)
                (parameter == null || canHoldCollection(parameter.type)) && !argumentsKept -> markMutable(argument)
            }
        }
    }

    private fun keepsArguments(method: PsiMethod): Boolean {
        if (method.isConstructor) return method.containingClass?.qualifiedName?.startsWith("java.util.") == true
        return (listOf(method) + method.findDeepestSuperMethods()).any { declaration ->
            val qualifiedName = declaration.containingClass?.qualifiedName
            qualifiedName != null && JavaToKotlinClassMap.mapJavaToKotlin(FqName(qualifiedName)) != null
        }
    }

    private fun canHoldCollection(type: PsiType): Boolean {
        val aClass = PsiUtil.resolveClassInClassTypeOnly(type) ?: return false
        return aClass is PsiTypeParameter || COLLECTION_ROOTS.any { InheritanceUtil.isInheritor(aClass, it) }
    }

    private fun markMutable(expression: PsiExpression?) {
        when (val stripped = PsiUtil.skipParenthesizedExprDown(expression)) {
            is PsiConditionalExpression -> {
                markMutable(stripped.thenExpression)
                markMutable(stripped.elseExpression)
            }

            is PsiSwitchExpression -> PsiUtil.getSwitchResultExpressions(stripped).forEach(::markMutable)
            else -> mutabilitySource(stripped)?.mutable = true
        }
    }

    private fun mutabilitySource(expression: PsiExpression?): TypeNode? = when (val stripped = PsiUtil.skipParenthesizedExprDown(expression)) {
        is PsiTypeCastExpression -> mutabilitySource(stripped.operand)
        is PsiMethodCallExpression -> when {
            stripped.methodExpression.referenceName in VIEW_METHODS -> mutabilitySource(stripped.methodExpression.qualifierExpression)
            stripped.methodExpression.qualifierExpression?.let(::expressionNode) != null -> expressionNode(stripped)
            else -> stripped.resolveMethod()?.let { nodes[it] }
        }

        is PsiArrayAccessExpression -> expressionNode(stripped)
        is PsiAssignmentExpression -> mutabilitySource(stripped.lExpression)
        else -> variableNode(stripped) ?: ((stripped as? PsiReferenceExpression)?.resolve() as? PsiParameter)?.let { lambdaParameterNodes[it] }
    }

    /** Returns the reference that [expression] compares with `null`, like `x` in `x != null`. */
    private fun comparedWithNull(expression: PsiBinaryExpression): PsiReferenceExpression? {
        val operation = expression.operationTokenType
        if (operation != JavaTokenType.EQEQ && operation != JavaTokenType.NE) return null
        val left = expression.lOperand
        val right = expression.rOperand ?: return null
        val operand = when {
            ExpressionUtils.isNullLiteral(right) -> left
            ExpressionUtils.isNullLiteral(left) -> right
            else -> return null
        }
        return PsiUtil.skipParenthesizedExprDown(operand) as? PsiReferenceExpression
    }

    private fun addNullComparisonDataflow(expression: PsiBinaryExpression) {
        val reference = comparedWithNull(expression) ?: return
        val node = variableNode(reference) ?: return
        if (expressionDfTypes.dfaNullability(reference) == DfaNullability.NOT_NULL) return
        if (DfaPsiUtil.isAssertionEffectively(expression, expression.operationTokenType == JavaTokenType.NE)) {
            node.needsNotNull = true
        } else {
            node.join(Nullability.Nullable)
        }
    }

    private fun addParameterDataflow(method: PsiMethod) {
        val hasUnseenCallers = !method.hasModifierProperty(PsiModifier.PRIVATE) && !searchesAllUsers(method)
        for (parameter in method.parameterList.parameters) {
            val node = nodes[parameter] ?: continue
            if (hasUnseenCallers) joinUnknown(node)
            if (method.body != null && JavaSourceInference.inferNullability(parameter) == JavaNullability.NOT_NULL) node.needsNotNull = true
        }
    }

    private fun joinUnknown(node: TypeNode) {
        node.join(Nullability.Default)
        for (element in node.elements) joinUnknown(element)
    }

    private fun addOverrideDataflow(method: PsiMethod) {
        val returnNode = nodes[method]
        for (superMethod in method.findSuperMethods()) {
            val superReturnNode = nodes[superMethod]
            if (returnNode != null && superReturnNode != null) {
                edge(returnNode, superReturnNode)
                linkElements(returnNode, superReturnNode)
                flowMutability(returnNode, superReturnNode)
                keepSubstitutedElementsMutable(superReturnNode, returnNode)
            } else if (returnNode != null) {
                returnNode.makeMutable()
            }
            for ((parameter, superParameter) in method.parameterList.parameters.zip(superMethod.parameterList.parameters)) {
                val parameterNode = nodes[parameter] ?: continue
                val superParameterNode = nodes[superParameter]
                if (superParameterNode == null) {
                    val kotlinParameter = (superParameter as? KtLightElement<*, *>)?.kotlinOrigin as? KtParameter
                    if (kotlinParameter != null) {
                        analyze(kotlinParameter) { applyKotlinType(kotlinParameter.returnType, parameterNode) }
                    } else {
                        parameterNode.makeMutable()
                    }
                    continue
                }
                keepSubstitutedElementsMutable(superParameterNode, parameterNode)
                flowMutability(superParameterNode, parameterNode)
                flowMutability(parameterNode, superParameterNode)
                edge(parameterNode, superParameterNode)
                edge(superParameterNode, parameterNode)
                linkElements(parameterNode, superParameterNode)
                linkElements(superParameterNode, parameterNode)
            }
        }
    }

    /**
     * An override in another file must repeat the parameter types: a Kotlin override gives its types,
     * and a Java override keeps them mutable, because its own conversion does the same for a super method in another file.
     */
    private fun addOverriderDataflow(method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (method.isConstructor || method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC) ||
            method.hasModifierProperty(PsiModifier.FINAL) || containingClass.hasModifierProperty(PsiModifier.FINAL) ||
            PsiUtil.isLocalOrAnonymousClass(containingClass)
        ) return
        val parameters = method.parameterList.parameters
        if (parameters.none { parameter -> nodes[parameter]?.let(::containsMappedCollection) == true }) return
        for (overrider in overridersOutsideFile(method)) {
            for ((parameter, overriderParameter) in parameters.zip(overrider.parameterList.parameters)) {
                val node = nodes[parameter] ?: continue
                val kotlinParameter = (overriderParameter as? KtLightElement<*, *>)?.kotlinOrigin as? KtParameter
                if (kotlinParameter != null) analyze(kotlinParameter) { applyKotlinType(kotlinParameter.returnType, node) } else node.makeMutable()
            }
        }
    }

    /** The extends clause prints the type argument of a type parameter mutable, so the override must print it mutable too. */
    private fun keepSubstitutedElementsMutable(superNode: TypeNode, node: TypeNode) {
        for ((superElement, element) in superNode.elements.zip(node.elements)) {
            if (PsiUtil.resolveClassInClassTypeOnly(superElement.type) is PsiTypeParameter) element.makeMutable()
            else keepSubstitutedElementsMutable(superElement, element)
        }
    }

    private fun containsMappedCollection(node: TypeNode): Boolean = isMappedCollection(node.type) || node.elements.any(::containsMappedCollection)

    context(_: KaSession)
    private fun applyKotlinType(type: KaType, node: TypeNode) {
        if (type is KaErrorType) return
        if (type is KaTypeParameterType) return node.makeMutable()
        node.platformNullability = if (type.isMarkedNullable) Nullability.Nullable else Nullability.NotNull
        val classType = type as? KaClassType ?: return
        node.mutable = JavaToKotlinClassMap.mutabilityMappings.any { it.kotlinMutable == classType.classId }
        node.mutabilityFixed = true
        for ((element, argument) in node.elements.zip(classType.typeArguments)) {
            argument.type?.let { applyKotlinType(it, element) }
        }
    }

    private fun addProjectDataflow(method: PsiMethod) {
        val references by lazy { referencesOutsideFile(method) }
        val parameterNodes = method.parameterList.parameters.map { nodes[it] }
        if (parameterNodes.any { it != null }) {
            for (element in references) {
                val arguments = (element.parent as? PsiCall)?.argumentList?.expressions
                if (arguments == null && element !is PsiMethodReferenceExpression) continue
                for ((index, node) in parameterNodes.withIndex()) {
                    if (node == null) continue
                    val argument = arguments?.getOrNull(index)
                    if (argument == null) joinUnknown(node) else if (!isIgnoredBy(node, argument)) addStaticDataflow(argument, node)
                }
            }
        }
        val returnNode = nodes[method] ?: return
        if (!returnNode.mutable && isMappedCollection(returnNode.type) && references.any { mayChangeOutside(it.parent) }) returnNode.makeMutable()
        if (returnNode.platformNullability != null) return
        if (method.isConstructor || method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.FINAL)) return
        for (overrider in overridersOutsideFile(method)) {
            val nullability = NullableNotNullManager.getNullability(overrider).takeIf { it != JavaNullability.UNKNOWN }
                ?: DfaUtil.inferMethodNullability(overrider)
            returnNode.join(nullability.toJ2K() ?: Nullability.Default)
        }
    }

    private fun overridersOutsideFile(method: PsiMethod): List<PsiMethod> = overriders.getOrPut(method) {
        OverridingMethodsSearch.search(originals.originalElementOrSelf(method)).findAll().filter { it.containingFile != originalFile }
    }

    private fun mayChangeOutside(value: PsiElement): Boolean {
        if (value !is PsiExpression || value.containingFile !is PsiJavaFile) return true
        return when (val parent = PsiUtil.skipParenthesizedExprUp(value.parent)) {
            is PsiExpressionStatement, is PsiForeachStatement -> false
            is PsiAssignmentExpression -> parent.lExpression != value
            is PsiReferenceExpression -> parent.referenceName in MUTATING_METHODS || parent.referenceName in VIEW_METHODS
            else -> true
        }
    }

    private fun addProjectAssignmentDataflow(field: PsiField, node: TypeNode) {
        for (element in referencesOutsideFile(field)) {
            if (!node.mutable && isMappedCollection(node.type) && mayChangeOutside(element)) node.makeMutable()
            val assignment = element.parent as? PsiAssignmentExpression ?: continue
            if (assignment.lExpression != element) continue
            val value = assignment.rExpression
            if (assignment.operationTokenType != JavaTokenType.EQ || value == null) {
                node.join(Nullability.NotNull)
            } else if (!isIgnoredBy(node, value)) {
                addStaticDataflow(value, node)
            }
        }
    }

    /**
     * Returns `true` when [addStaticDataflow] of [expression] cannot change any result.
     * A node with a platform nullability keeps it, so only a link to the node of [expression] could matter.
     */
    private fun isIgnoredBy(node: TypeNode, expression: PsiExpression): Boolean {
        if (node.platformNullability == null) return false
        return when (val stripped = PsiUtil.skipParenthesizedExprDown(expression)) {
            is PsiReferenceExpression -> (stripped.resolve() as? PsiVariable)?.let { it in nodes } != true
            is PsiMethodCallExpression, is PsiArrayAccessExpression, is PsiNewExpression -> false
            else -> true
        }
    }

    private fun referencesOutsideFile(member: PsiMember): List<PsiElement> {
        val original = originals.originalElementOrSelf(member)
        return ReferencesSearch.search(original, original.useScope).findAll().map { it.element }.filter { it.containingFile != originalFile }
    }

    private fun isConstructed(aClass: PsiClass?): Boolean =
        aClass != null && aClass !is PsiTypeParameter && !aClass.isInterface && !aClass.isRecord &&
            !PsiUtil.isLocalOrAnonymousClass(aClass)

    private fun analyzeConstruction(aClass: PsiClass) {
        val runner = ConstructionRunner()
        val classDataflow = BodyDataflow()
        runner.checkAtReturn = false
        val checkedFields = aClass.fields.filter { it in nodes && !NullabilityUtil.isImplicitlyInitializedNotNull(it) }
        runner.fieldsToCheck = checkedFields.filter { it.hasModifierProperty(PsiModifier.STATIC) }
        if (runner.analyzeBlockRecursively(aClass, listOf(runner.defaultState(checkedFields)), classDataflow) != RunnerResult.OK) return
        classDataflow.commit()

        val instanceFields = checkedFields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }
        val initializedStates = classDataflow.endOfInitializerStates.ifEmpty { listOf(runner.freshState()) }
        val constructors = aClass.constructors
        if (constructors.isEmpty()) {
            for (state in initializedStates) runner.check(state, instanceFields)
        }
        for (constructor in constructors) {
            val body = constructor.body ?: continue
            val chained = JavaPsiConstructorUtil.isChainedConstructorCall(JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor))
            runner.checkAtReturn = true
            runner.fieldsToCheck = if (chained) emptyList() else instanceFields
            val states = if (chained) listOf(runner.freshState()) else initializedStates.map { it.createCopy() }
            val dataflow = BodyDataflow()
            if (runner.analyzeBlockRecursively(body, states, dataflow) == RunnerResult.OK) dataflow.commit()
        }
        for (field in runner.nullAtEnd) nodes[field]?.join(Nullability.Nullable)
    }

    private inner class ConstructionRunner : StandardDataFlowRunner(file.project, ThreeState.UNSURE) {
        var checkAtReturn = false
        var fieldsToCheck: List<PsiField> = emptyList()
        val nullAtEnd = HashSet<PsiField>()

        fun freshState(): DfaMemoryState = createMemoryState()

        // The data flow analysis gives no default value to a field that something outside the code writes,
        // but right after `new` such a field still holds its default value
        fun defaultState(fields: List<PsiField>): DfaMemoryState = freshState().also { state ->
            for (field in fields) {
                if (field.hasInitializer() || field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC)) continue
                state.setVarValue(PlainDescriptor.createVariableValue(factory, field), factory.fromDfType(DfTypes.defaultValue(field.type)))
            }
        }

        fun check(state: DfaMemoryState, fields: List<PsiField>) {
            for (field in fields) {
                val value = PlainDescriptor.createVariableValue(factory, field)
                if (DfaNullability.fromDfType(state.getDfType(value)) == DfaNullability.NULL) nullAtEnd += field
            }
        }

        override fun createInterpreter(listener: DfaListener, flow: ControlFlow): StandardDataFlowInterpreter {
            flow.keepVariables { it is PlainDescriptor && it.psiElement in fieldsToCheck }
            return object : StandardDataFlowInterpreter(flow, listener) {
                override fun acceptInstruction(instructionState: DfaInstructionState): Array<DfaInstructionState> {
                    val instruction = instructionState.instruction
                    val isEnd = if (checkAtReturn) instruction is ReturnInstruction else instruction is FlushFieldsInstruction
                    if (isEnd) check(instructionState.memoryState, fieldsToCheck)
                    return super.acceptInstruction(instructionState)
                }
            }
        }
    }

    private fun analyze(body: PsiCodeBlock) {
        val dataflow = BodyDataflow()
        val result = StandardDataFlowRunner(file.project, ThreeState.UNSURE).analyzeMethodRecursively(body, dataflow)
        if (result == RunnerResult.OK) dataflow.commit()
    }

    private inner class BodyDataflow : JavaDfaListener {
        private val joins = ArrayList<Pair<TypeNode, Nullability>>()
        private val edges = ArrayList<Pair<TypeNode, TypeNode>>()
        private val needsNotNull = ArrayList<TypeNode>()
        private val pushed = HashSet<PsiExpression>()
        private val types = HashMap<PsiExpression, DfType>()
        val endOfInitializerStates = ArrayList<DfaMemoryState>()

        override fun beforeInstanceInitializerEnd(state: DfaMemoryState) {
            endOfInitializerStates += state.createCopy()
        }

        override fun beforeExpressionPush(value: DfaValue, expression: PsiExpression, state: DfaMemoryState) {
            pushed += expression
            if (expression in dataflowQueries) {
                // Like CommonDataflow.getDfType, keep the facts of the states with enabled assertions
                val assertionsDisabled = AssertionDisabledDescriptor.getAssertionsDisabledVar(value.factory)
                if (assertionsDisabled == null || state.getDfType(assertionsDisabled) != DfTypes.TRUE) {
                    types.merge(expression, state.getDfTypeIncludingDerived(value), DfType::join)
                }
            }
            val target = sinks[expression] ?: return
            if (state.getDfType(value) == DfType.FAIL) return
            val nullability = if (TypeConversionUtil.isPrimitiveAndNotNull(expression.type)) {
                DfaNullability.NOT_NULL
            } else {
                DfaNullability.fromDfType(state.getDfTypeIncludingDerived(value))
            }
            when (nullability) {
                DfaNullability.NULL, DfaNullability.NULLABLE -> joins += target to Nullability.Nullable
                DfaNullability.NOT_NULL -> joins += target to Nullability.NotNull
                else -> when (NullabilityUtil.getExpressionNullability(expression, false)) {
                    JavaNullability.NOT_NULL -> joins += target to Nullability.NotNull
                    JavaNullability.NULLABLE -> joins += target to Nullability.Nullable
                    else -> {
                        val source = sourceNode(value, expression)
                        if (source != null) edges += source to target else joins += target to Nullability.Default
                    }
                }
            }
        }

        override fun onCondition(problem: UnsatisfiedConditionProblem, value: DfaValue, failed: ThreeState, state: DfaMemoryState) {
            if (problem !is NullabilityProblem<*> || problem.kind !in DEREFERENCE_KINDS) return
            val variable = (value as? DfaVariableValue)?.psiVariable as? PsiVariable ?: return
            val node = nodes[variable] ?: return
            val nullability = DfaNullability.fromDfType(state.getDfType(value))
            if (nullability == DfaNullability.UNKNOWN || nullability == DfaNullability.FLUSHED) needsNotNull += node
        }

        fun commit() {
            for ((node, nullability) in joins) node.join(nullability)
            for ((source, target) in edges) edge(source, target)
            for (node in needsNotNull) node.needsNotNull = true
            reached += pushed
            for ((expression, type) in types) expressionDfTypes.putIfAbsent(expression, type)
        }
    }

    private fun sourceNode(value: DfaValue, expression: PsiExpression): TypeNode? {
        val variable = (value as? DfaVariableValue)?.psiVariable as? PsiModifierListOwner
        if (variable != null) nodes[variable]?.let { return it }
        return expressionNode(expression)
    }

    private fun expressionNode(expression: PsiExpression?): TypeNode? = when (val stripped = PsiUtil.skipParenthesizedExprDown(expression)) {
        is PsiReferenceExpression -> (stripped.resolve() as? PsiVariable)?.let { nodes[it] }
        is PsiMethodCallExpression -> calleeNode(stripped)
        is PsiArrayAccessExpression -> expressionNode(stripped.arrayExpression)?.elements?.firstOrNull()
        is PsiNewExpression -> newExpressionNode(stripped)
        else -> null
    }

    private fun newExpressionNode(expression: PsiNewExpression): TypeNode? {
        callNodes[expression]?.let { return it }
        val initializer = expression.arrayInitializer
        if (initializer != null) {
            val type = expression.type as? PsiArrayType ?: return null
            return TypeNode(type, Nullability.NotNull).also {
                it.elements = createElements(type)
                callNodes[expression] = it
                for (value in initializer.initializers) addSink(value, it.elements.single())
            }
        }
        val typeArguments = expression.classReference?.parameterList?.typeParameterElements ?: return null
        if (typeArguments.isEmpty()) return null
        val type = expression.type as? PsiClassType ?: return null
        return TypeNode(type, Nullability.NotNull).also {
            it.elements = createElements(type)
            callNodes[expression] = it
        }
    }

    private fun variableNode(expression: PsiExpression?): TypeNode? {
        val reference = PsiUtil.skipParenthesizedExprDown(expression) as? PsiReferenceExpression ?: return null
        val variable = reference.resolve() as? PsiVariable ?: return null
        return nodes[variable]
    }

    private fun calleeNode(call: PsiMethodCallExpression): TypeNode? {
        val method = call.resolveMethod() ?: return null
        nodes[method]?.let { return it }
        callNodes[call]?.let { return it }
        val type = method.returnType ?: return null
        val receiver = expressionNode(call.methodExpression.qualifierExpression)
        if (receiver != null) {
            receiverElement(receiver, type)?.let { return it }
            val elements = if (receiver.elements.isEmpty()) emptyList() else createElements(type, receiver, callee = method)
            if (elements.isNotEmpty()) {
                val nullability = effectiveNullability(method)
                return TypeNode(type, nullability).also {
                    if (nullability == null) it.join(Nullability.Default)
                    it.elements = elements
                    // The entries follow the map, see addEntryMutability
                    if (method.name == "entrySet") elements.first().mutable = false
                    callNodes[call] = it
                    linkTypeArguments(call, method, it)
                }
            }
        }
        callNodes[method]?.let { return it }
        val enumValues = isEnumValues(method)
        if (method.containingFile == file && !enumValues) return null
        val nullability = if (enumValues) Nullability.NotNull else effectiveNullability(method) ?: return null
        val elementNullability = if (enumValues) Nullability.NotNull else null
        val elements = createElements(type, elementNullability = elementNullability, callee = method)
        return TypeNode(type, nullability).also {
            it.elements = elements
            callNodes[if (elements.isEmpty()) method else call] = it
            if (elements.isNotEmpty()) linkTypeArguments(call, method, it)
        }
    }

    /** Returns the explicit type arguments of [call], otherwise the type arguments that J2K prints for it. */
    private fun typeArgumentElements(call: PsiMethodCallExpression): Array<PsiTypeElement> =
        call.typeArgumentList.typeParameterElements.takeIf { it.isNotEmpty() }
            ?: inferredTypeArguments[call]?.typeParameterElements
            ?: PsiTypeElement.EMPTY_ARRAY

    private fun linkTypeArguments(call: PsiMethodCallExpression, method: PsiMethod, node: TypeNode) {
        val typeElements = typeArgumentElements(call)
        val typeParameters = method.typeParameters
        if (typeElements.size != typeParameters.size) return
        val arguments = typeElements.map { typeElement ->
            callNodes.getOrPut(typeElement) {
                val type = typeElement.type
                TypeNode(type, type.nullability.nullability().toJ2K()).also { it.elements = createElements(type) }
            }
        }

        fun indexOf(type: PsiType?): Int = (PsiUtil.resolveClassInClassTypeOnly(type) as? PsiTypeParameter)?.let(typeParameters::indexOf) ?: -1

        fun link(element: TypeNode) {
            val index = indexOf(element.type)
            if (element.printedByCaller && index >= 0) {
                edge(element, arguments[index])
                edge(arguments[index], element)
            }
            element.elements.forEach(::link)
        }
        node.elements.forEach(::link)

        val parameters = method.parameterList.parameters
        val isVarArgCall = MethodCallUtils.isVarArgCall(call)
        for ((position, argument) in call.argumentList.expressions.withIndex()) {
            val parameter = parameters.getOrNull(position) ?: parameters.lastOrNull()?.takeIf { isVarArgCall } ?: continue
            val index = indexOf(parameter.type)
            val componentIndex = indexOf((parameter.type as? PsiArrayType)?.componentType)
            when {
                parameter.isVarArgs && isVarArgCall -> if (componentIndex >= 0) addSink(argument, arguments[componentIndex])
                index >= 0 -> addSink(argument, arguments[index])
                componentIndex >= 0 -> {
                    val typeArgument = arguments[componentIndex]
                    if (ExpressionUtils.isNullLiteral(argument)) typeArgument.join(Nullability.Nullable)
                    expressionNode(argument)?.elements?.firstOrNull()?.let {
                        edge(it, typeArgument)
                        edge(typeArgument, it)
                    }
                }
            }
        }
        linkLambdaParameters(call, parameters, typeParameters, arguments)
    }

    /**
     * Gives the parameters of the lambda arguments of [call] the type arguments that give their types,
     * like `o` in `findAllAsArray(passes, o -> o.isDumbAware())` gets `T` of `findAllAsArray(T[], Condition<? super T>)`.
     * A parameter only follows its type argument: the engine does not know all the arguments that constrain the type argument.
     */
    private fun linkLambdaParameters(
        call: PsiMethodCallExpression,
        parameters: Array<PsiParameter>,
        typeParameters: Array<PsiTypeParameter>,
        arguments: List<TypeNode>,
    ) {
        for ((lambdaParameter, type) in lambdaParameterTypes(call, parameters)) {
            val typeParameter = PsiUtil.resolveClassInClassTypeOnly(type) as? PsiTypeParameter ?: continue
            val typeArgument = arguments.getOrNull(typeParameters.indexOf(typeParameter)) ?: continue
            lambdaParameterTypeArguments[lambdaParameter] = typeArgument
        }
    }

    private fun isDereferenced(expression: PsiExpression): Boolean = when (val parent = expression.parent) {
        is PsiReferenceExpression -> parent.qualifierExpression === expression
        is PsiArrayAccessExpression -> parent.arrayExpression === expression
        is PsiForeachStatement -> parent.iteratedValue === expression
        else -> false
    }

    private fun isEnumValues(method: PsiMethod): Boolean =
        method.name == "values" && method.parameterList.isEmpty && method.hasModifierProperty(PsiModifier.STATIC) &&
            method.containingClass?.isEnum == true

    private fun effectiveNullability(method: PsiMethod): Nullability? =
        DfaPsiUtil.getElementNullabilityForRead(method.returnType, method).toJ2K()

    private fun addStaticDataflow(expression: PsiExpression, node: TypeNode) {
        when (NullabilityUtil.getExpressionNullability(expression, true)) {
            JavaNullability.NOT_NULL -> node.join(Nullability.NotNull)
            JavaNullability.NULLABLE -> node.join(Nullability.Nullable)
            else -> {
                val source = expressionNode(expression)
                if (source != null) {
                    edge(source, node)
                    linkElements(source, node)
                } else {
                    node.join(Nullability.Default)
                }
            }
        }
    }

    private fun edge(source: TypeNode, target: TypeNode) {
        if (source === target || isOptional(target.type)) return
        source.targets += target
        target.sources += source
    }

    private fun allNodes(): Set<TypeNode> = buildSet {
        addAll(nodes.values)
        addAll(callNodes.values)
        addAll(elementNodes())
    }

    private fun elementNodes(): Set<TypeNode> {
        val elements = LinkedHashSet<TypeNode>()
        fun collect(node: TypeNode) {
            for (element in node.elements) if (elements.add(element)) collect(element)
        }
        for (node in nodes.values) collect(node)
        for (node in callNodes.values) collect(node)
        return elements
    }

    private fun solve() {
        val queue = ArrayDeque(allNodes())
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nullability = node.platformNullability ?: node.dataflowNullability ?: continue
            for (target in node.targets) {
                if (target.platformNullability == null && target.join(nullability)) queue += target
            }
        }
    }

    private fun solveMutability() {
        val all = allNodes() + lambdaParameterNodes.values + lambdaParameterNodes.values.flatMap { it.descendants() }
        val queue = ArrayDeque(all.filter { it.mutable })
        val linksBySource = elementLinks.groupBy { it.source }
        val linksByOwner = elementLinks.groupBy { it.owner }
        val unseen = unseenArgumentOwners.flatMapTo(HashSet()) { listOf(it) + it.descendants() }

        fun mark(node: TypeNode) {
            if (node.mutable || node.mutabilityFixed) return
            node.mutable = true
            queue += node
        }

        fun markUnseenInvariantElements(owner: TypeNode) {
            for ((index, element) in owner.elements.withIndex()) {
                if (element.wildcard?.isSuper == true || isInvariant(owner, index)) mark(element)
            }
        }

        unseen.forEach(::markUnseenInvariantElements)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            node.mutabilitySources.forEach(::mark)
            linksBySource[node]?.forEach { if (isInvariant(it.owner, it.index)) mark(it.target) }
            linksByOwner[node]?.forEach { if (it.source.mutable && isInvariant(it.owner, it.index)) mark(it.target) }
            if (node in unseen) markUnseenInvariantElements(node)
        }
    }

    private fun isInvariant(owner: TypeNode, index: Int): Boolean {
        if (owner.elements[index].wildcard != null) return false
        if (owner.className !in toKotlinMutableTypesMap) return true
        return owner.mutable || owner.className == CommonClassNames.JAVA_UTIL_MAP && index == 0
    }

    private fun resolve() {
        val all = allNodes()
        for (node in all) node.result = node.decide()
        val queue = ArrayDeque(all.filter { it.result == Nullability.NotNull })
        while (queue.isNotEmpty()) {
            for (source in queue.removeFirst().sources) {
                if (source.platformNullability != null || source.needsNotNull || source.dataflowNullability == Nullability.Nullable) continue
                source.needsNotNull = true
                source.result = source.decide()
                queue += source
            }
        }
    }

    companion object {
        private val DEREFERENCE_KINDS: Set<NullabilityProblemKind<*>> = setOf(
            NullabilityProblemKind.callNPE,
            NullabilityProblemKind.callMethodRefNPE,
            NullabilityProblemKind.innerClassNPE,
            NullabilityProblemKind.templateNPE,
            NullabilityProblemKind.fieldAccessNPE,
            NullabilityProblemKind.arrayAccessNPE,
            NullabilityProblemKind.unboxingNullable,
            NullabilityProblemKind.unboxingMethodRefParameter,
            NullabilityProblemKind.passingToNotNullParameter,
            NullabilityProblemKind.passingToNotNullMethodRefParameter,
            NullabilityProblemKind.assigningToNotNull,
            NullabilityProblemKind.storingToNotNullArray,
        )

        private val MUTATING_METHODS: Set<String> = setOf(
            "add", "addAll", "addFirst", "addLast", "clear", "compute", "computeIfAbsent", "computeIfPresent", "merge", "put", "putAll",
            "putIfAbsent", "remove", "removeAll", "removeFirst", "removeIf", "removeLast", "replace", "replaceAll", "retainAll", "set",
            "setValue", "sort",
        )

        private val VIEW_METHODS: Set<String> = setOf("entrySet", "iterator", "keySet", "listIterator", "reversed", "subList", "values")

        private val COLLECTION_ROOTS: List<String> = listOf(
            CommonClassNames.JAVA_LANG_ITERABLE,
            CommonClassNames.JAVA_UTIL_ITERATOR,
            CommonClassNames.JAVA_UTIL_MAP,
            CommonClassNames.JAVA_UTIL_MAP_ENTRY,
        )
    }
}

private fun isOptional(type: PsiType): Boolean = PsiTypesUtil.classNameEquals(type, CommonClassNames.JAVA_UTIL_OPTIONAL)

private fun isMappedCollection(type: PsiType): Boolean = PsiUtil.resolveClassInClassTypeOnly(type)?.qualifiedName in toKotlinMutableTypesMap
