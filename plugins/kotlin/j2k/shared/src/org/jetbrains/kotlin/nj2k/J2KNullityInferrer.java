// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider;

import java.util.*;

import static org.jetbrains.kotlin.nj2k.NullabilityUtilsKt.*;

/**
 * This class is based on com.intellij.codeInspection.inferNullity.NullityInferrer
 * and extended for better J2K support.
 * TODO:
 *  support inference for type arguments in case the type parameter is used as both an input and an output type (Collections.emptyList())
 *  support method calls of external methods (especially Kotlin interop)
 *  improve support for array types and initializers
 *  support propagation of generic nullability from lambda parameters
 *  take overridden signatures into account when inferring parameter nullability
 *  support collections mutability inference
 *  try to migrate from direct usage of PsiType's
 *  additional profiling/optimization
 *  document how it works
 *  convert to Kotlin
 */
@SuppressWarnings("DuplicatedCode")
class J2KNullityInferrer {
    private static final int MAX_PASSES = 10;
    private int numAnnotationsAdded;

    // We need an identity set for comparing `PsiType` instances
    // to differentiate between different type arguments of the same type in the code.
    //
    // TODO: operating on `PsiType`s directly is not always correct,
    //  because a PsiType object can sometimes be recreated (for example, by PsiNewExpressionImpl.getType()),
    //  and we won't find it in our IdentityHashMap.
    //  We should try to migrate this logic to PsiTypeElements, which are more persistent.
    private final Set<PsiType> notNullTypes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<PsiType> nullableTypes = Collections.newSetFromMap(new IdentityHashMap<>());

    // Some types point to a PsiJavaCodeReferenceElement, in this case we mark them as well.
    // This helps to avoid the problem with recreated types (described above),
    // because two different types may point to the same PsiJavaCodeReferenceElement.
    private final Set<PsiJavaCodeReferenceElement> notNullElements = new HashSet<>();
    private final Set<PsiJavaCodeReferenceElement> nullableElements = new HashSet<>();

    // Caches
    private final Map<PsiVariable, Collection<PsiReference>> variableReferences = new HashMap<>();
    private final Map<PsiVariable, Collection<PsiExpression>> variableAssignmentRightHandSides = new HashMap<>();
    private final Map<PsiParameter, List<PsiReferenceExpression>> parameterReferences = new HashMap<>();

    Set<PsiType> getNotNullTypes() {
        return notNullTypes;
    }

    Set<PsiType> getNullableTypes() {
        return nullableTypes;
    }

    Set<PsiJavaCodeReferenceElement> getNotNullElements() {
        return notNullElements;
    }

    Set<PsiJavaCodeReferenceElement> getNullableElements() {
        return nullableElements;
    }

    private boolean expressionIsNeverNull(@Nullable PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        if (ExpressionUtils.nonStructuralChildren(expression).allMatch(
                expr -> expr instanceof PsiMethodCallExpression && isNotNull(((PsiMethodCallExpression) expr).resolveMethod()))) {
            return true;
        }
        return NullabilityUtil.getExpressionNullability(expression, true) == Nullability.NOT_NULL;
    }

    private boolean expressionIsSometimesNull(@Nullable PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        if (ExpressionUtils.nonStructuralChildren(expression).anyMatch(
                expr -> expr instanceof PsiMethodCallExpression callExpression && isNullable(callExpression.resolveMethod()))) {
            return true;
        }
        return NullabilityUtil.getExpressionNullability(expression, true) == Nullability.NULLABLE;
    }

    private boolean variableNeverAssignedNull(@NotNull PsiVariable variable) {
        final PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
            if (!expressionIsNeverNull(initializer)) {
                return false;
            }
        } else if (!variable.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
        } else if (variable instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiCatchSection) {
            return false;
        }

        final Collection<PsiReference> references = variableReferences.get(variable);
        for (final PsiReference reference : references) {
            final PsiElement element = reference.getElement();
            if (!(element instanceof PsiReferenceExpression)) {
                continue;
            }
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiAssignmentExpression assignment)) {
                continue;
            }

            if (assignment.getLExpression().equals(element) &&
                !expressionIsNeverNull(assignment.getRExpression())) {
                return false;
            }
        }

        return true;
    }

    private boolean variableSometimesAssignedNull(@NotNull PsiVariable variable) {
        final PsiExpression initializer = variable.getInitializer();
        if (initializer != null && expressionIsSometimesNull(initializer)) {
            return true;
        }

        SearchScope scope = getScope(variable);
        if (scope == null) {
            return false;
        }

        final Collection<PsiReference> references = variableReferences.get(variable);
        for (final PsiReference reference : references) {
            final PsiElement element = reference.getElement();
            if (!(element instanceof PsiReferenceExpression)) {
                continue;
            }
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiAssignmentExpression assignment)) {
                continue;
            }

            if (assignment.getLExpression().equals(element) && expressionIsSometimesNull(assignment.getRExpression())) {
                return true;
            }
        }

        return false;
    }

    private static @Nullable SearchScope getScope(@NotNull PsiVariable variable) {
        if (!(variable instanceof PsiField field)) {
            return variable.getUseScope();
        }

        if (isPrivate(field)) {
            return variable.getUseScope();
        }

        // Optimization: for public and protected fields, don't try to search for usages outside the file
        // because it can be very slow in large projects
        PsiFile containingFile = variable.getContainingFile();
        return containingFile != null ? new LocalSearchScope(containingFile) : null;
    }

    private static boolean isPrivate(PsiField field) {
        PsiModifierList modifierList = field.getModifierList();
        if (modifierList == null) return false;
        return modifierList.hasModifierProperty(PsiModifier.PRIVATE);
    }

    public void collect(@NotNull PsiFile file) {
        runPreprocessing(file);
        inferNullabilityIteratively(file);
        flushCaches();
    }

    // Mark all types with known nullability inferred from Java DFA
    // and cache some variable reference searches
    private void runPreprocessing(@NotNull PsiFile file) {
        file.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
                super.visitTypeElement(typeElement);
                PsiType type = typeElement.getType();
                PsiModifierListOwner owner =
                        typeElement.getParent() instanceof PsiModifierListOwner modifierListOwner ? modifierListOwner : null;
                if (owner instanceof PsiMethod) return;
                Nullability nullability = DfaPsiUtil.getElementNullability(type, owner);
                switch (nullability) {
                    case NULLABLE -> registerNullableType(type);
                    case NOT_NULL -> registerNotNullType(type);
                }
                if (type instanceof PsiArrayType arrayType) {
                    PsiType componentType = arrayType.getComponentType();
                    Nullability componentNullability = DfaPsiUtil.getTypeNullability(componentType);
                    switch (componentNullability) {
                        case NULLABLE -> registerNullableType(componentType);
                        case NOT_NULL -> registerNotNullType(componentType);
                    }
                }
            }

            @Override
            public void visitVariable(@NotNull PsiVariable variable) {
                super.visitVariable(variable);
                if (variable.getType() instanceof PsiPrimitiveType) return;

                SearchScope scope = getScope(variable);
                Collection<PsiReference> references = Collections.emptyList();
                if (scope != null) {
                    references = ReferencesSearch.search(variable, scope).findAll();
                }
                variableReferences.put(variable, references);

                Collection<PsiExpression> rightHandSides = DfaPsiUtil.getVariableAssignmentsInFile(variable, false, null);
                variableAssignmentRightHandSides.put(variable, rightHandSides);
            }

            @Override
            public void visitParameter(@NotNull PsiParameter parameter) {
                super.visitParameter(parameter);
                if (parameter.getType() instanceof PsiPrimitiveType) return;

                List<PsiReferenceExpression> references = Collections.emptyList();
                final PsiElement grandParent = parameter.getDeclarationScope();
                if (grandParent instanceof PsiMethod method) {
                    if (method.getBody() != null) {
                        references = VariableAccessUtils.getVariableReferences(parameter, method);
                    }
                }
                parameterReferences.put(parameter, references);
            }
        });
    }

    private void inferNullabilityIteratively(@NotNull PsiFile file) {
        int prevNumAnnotationsAdded;
        int pass = 0;
        do {
            final NullityInferrerVisitor visitor = new NullityInferrerVisitor();
            prevNumAnnotationsAdded = numAnnotationsAdded;
            file.accept(visitor);
            pass++;
        }
        while (prevNumAnnotationsAdded < numAnnotationsAdded && pass < MAX_PASSES);
    }

    private void flushCaches() {
        variableReferences.clear();
        variableAssignmentRightHandSides.clear();
        parameterReferences.clear();
    }

    private void registerNullableAnnotation(@NotNull PsiModifierListOwner declaration) {
        registerAnnotation(declaration, true);
    }

    private void registerNotNullAnnotation(@NotNull PsiModifierListOwner declaration) {
        registerAnnotation(declaration, false);
    }

    private void registerAnnotation(@NotNull PsiModifierListOwner declaration, boolean isNullable) {
        PsiType type = getType(declaration);
        if (type == null) {
            return;
        }
        registerTypeNullability(type, isNullable);
    }

    private static PsiType getType(PsiModifierListOwner declaration) {
        if (declaration instanceof PsiVariable) {
            return ((PsiVariable) declaration).getType();
        }
        if (declaration instanceof PsiMethod) {
            return ((PsiMethod) declaration).getReturnType();
        }
        return null;
    }

    private void registerNullableType(@Nullable PsiType type) {
        if (type != null) {
            registerTypeNullability(type, true);
        }
    }

    private void registerNotNullType(@Nullable PsiType type) {
        if (type != null) {
            registerTypeNullability(type, false);
        }
    }

    private void registerTypeNullability(@NotNull PsiType type, boolean isNullable) {
        if (isNullable(type)) {
            // If this type is already nullable:
            //   - don't try to make it nullable twice
            //   - or don't try to make it not-null because nullable is stronger than not-null
            return;
        }

        if (isNotNull(type) && !isNullable) {
            // Don't try to make the type not-null twice
            return;
        }

        // We conservatively skip type parameter references for now (their nullability is always considered unknown)
        // TODO support it
        if (type instanceof PsiClassType classType) {
            PsiClass psiClass = classType.resolve();
            if (psiClass instanceof PsiTypeParameter) {
                return;
            }
        }

        PsiJavaCodeReferenceElement element = null;
        if (type instanceof PsiClassReferenceType classReferenceType) {
            element = classReferenceType.getReference();
        }

        PsiType unwrappedType = unwrap(type);

        if (isNullable) {
            nullableTypes.add(unwrappedType);
            if (element != null) nullableElements.add(element);

            notNullTypes.remove(unwrappedType);
            if (element != null) notNullElements.remove(element);
        } else {
            notNullTypes.add(unwrappedType);
            if (element != null) notNullElements.add(element);
        }

        numAnnotationsAdded++;
    }

    private void registerAnnotationByNullAssignmentStatus(PsiVariable variable) {
        if (variableNeverAssignedNull(variable)) {
            registerNotNullAnnotation(variable);
        } else if (variableSometimesAssignedNull(variable)) {
            registerNullableAnnotation(variable);
        }
    }

    private boolean isNotNull(@Nullable PsiModifierListOwner owner) {
        if (owner == null) return false;
        if (NullableNotNullManager.isNotNull(owner)) {
            return true;
        }
        PsiType type = getType(owner);
        if (type == null) return false;
        return isNotNull(type);
    }

    private boolean isNotNull(@Nullable PsiType type) {
        if (type == null) return false;
        PsiType unwrappedType = unwrap(type);
        return notNullTypes.contains(unwrappedType);
    }

    private boolean isNullable(@Nullable PsiModifierListOwner owner) {
        if (owner == null) return false;
        if (NullableNotNullManager.isNullable(owner)) {
            return true;
        }
        PsiType type = getType(owner);
        if (type == null) return false;
        return isNullable(type);
    }

    private boolean isNullable(@Nullable PsiType type) {
        if (type == null) return false;
        PsiType unwrappedType = unwrap(type);
        return nullableTypes.contains(unwrappedType);
    }

    private static @NotNull PsiType unwrap(@NotNull PsiType type) {
        if (type instanceof PsiCapturedWildcardType capturedWildcardType) {
            return unwrap(capturedWildcardType.getWildcard());
        } else if (type instanceof PsiWildcardType wildcardType) {
            if (wildcardType.isExtends()) return wildcardType.getExtendsBound();
            if (wildcardType.isSuper()) return wildcardType.getSuperBound();
        }
        return type;
    }

    private boolean hasRawNullability(@NotNull PsiType type) {
        return isNullable(type) || isNotNull(type);
    }

    private boolean hasRawNullability(@NotNull PsiModifierListOwner owner) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
        NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
        if (info != null && !info.isInferred() && info.getNullability() != Nullability.UNKNOWN) return true;

        PsiType type = getType(owner);
        if (type == null) return false;
        return hasRawNullability(type);
    }

    private class NullityInferrerVisitor extends JavaRecursiveElementWalkingVisitor {

        @Override
        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (method.isConstructor() || method.getReturnType() instanceof PsiPrimitiveType) {
                return;
            }

            // TODO deal with overrides somehow
            unifyNullabilityOfMethodReturnTypeAndReturnedExpressions(method);

            final Collection<PsiMethod> overridingMethods = OverridingMethodsSearch.search(method).findAll();
            for (final PsiMethod overridingMethod : overridingMethods) {
                if (isNullable(overridingMethod)) {
                    registerNullableAnnotation(method);
                    return;
                }
            }

            final NullableNotNullManager manager = NullableNotNullManager.getInstance(method.getProject());
            if (!manager.isNotNull(method, false) && manager.isNotNull(method, true)) {
                registerNotNullAnnotation(method);
                return;
            }

            if (hasRawNullability(method)) {
                return;
            }

            Nullability nullability = getMethodNullabilityByDfa(method);
            switch (nullability) {
                case NULLABLE -> registerNullableAnnotation(method);

                case NOT_NULL -> {
                    for (final PsiMethod overridingMethod : overridingMethods) {
                        if (!isNotNull(overridingMethod)) {
                            return;
                        }
                    }
                    registerNotNullAnnotation(method);
                }
            }
        }

        private void unifyNullabilityOfMethodReturnTypeAndReturnedExpressions(@NotNull PsiMethod method) {
            if (KotlinPluginModeProvider.Companion.isK1Mode()) return;

            PsiReturnStatement[] statements = PsiUtil.findReturnStatements(method);
            if (statements.length == 0) {
                // Stick to nullable by default for empty methods (ex. in interfaces)
                return;
            }

            PsiType methodReturnType = method.getReturnType();
            if (methodReturnType == null) return;

            Nullability methodNullability = DfaPsiUtil.getElementNullability(methodReturnType, method);
            if (methodNullability == Nullability.NOT_NULL) {
                registerNotNullType(methodReturnType);
            }
            if (methodReturnType instanceof PsiArrayType arrayType) {
                PsiType componentType = arrayType.getComponentType();
                Nullability componentNullability = DfaPsiUtil.getTypeNullability(componentType);
                switch (componentNullability) {
                    case NULLABLE -> registerNullableType(componentType);
                    case NOT_NULL -> registerNotNullType(componentType);
                }
            }

            boolean allRawReturnValueTypesAreNotNull = true;
            boolean rawMethodReturnTypeIsNotNull = methodNullability == Nullability.NOT_NULL || isNotNull(methodReturnType);

            for (PsiReturnStatement statement : statements) {
                PsiExpression returnValue = statement.getReturnValue();
                if (returnValue == null) continue;

                PsiType returnValueType = getReferenceType(returnValue);
                if (returnValueType == null) continue;

                if (rawMethodReturnTypeIsNotNull) {
                    // TODO simplify
                    propagateRawNullabilityWithPsiContext(methodReturnType, returnValueType);
                    propagateRawNullabilityWithPsiContext(methodReturnType, returnValue.getType());
                }

                if (!isNotNull(returnValueType)) {
                    allRawReturnValueTypesAreNotNull = false;
                }

                // TODO Looks questionable to update parts of type arguments like this
                unifyGenericNullability(methodReturnType, returnValueType); // TODO extend to `returnValue.getType()`?
            }

            if (allRawReturnValueTypesAreNotNull) {
                registerNotNullType(methodReturnType);
            }
        }

        @Override
        public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);

            PsiType variableType = variable.getType();
            if (variableType instanceof PsiPrimitiveType) return;

            if (!hasRawNullability(variableType)) {
                registerAnnotationByNullAssignmentStatus(variable);
            }

            inferNullabilityFromVariableReferences(variable);
            propagateNullabilityFromVariable(variable);
        }

        private void inferNullabilityFromVariableReferences(@NotNull PsiVariable variable) {
            if (KotlinPluginModeProvider.Companion.isK1Mode()) {
                // Try not to break K1 nullability inference
                return;
            }

            final Collection<PsiReference> references = variableReferences.get(variable);
            for (final PsiReference reference : references) {
                final PsiElement element = reference.getElement();
                if (!(element instanceof PsiReferenceExpression referenceExpression)) {
                    continue;
                }

                final PsiElement refParent =
                        PsiTreeUtil.skipParentsOfType(referenceExpression, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);

                processReference(variable, referenceExpression, refParent);
            }
        }

        private void propagateNullabilityFromVariable(PsiVariable variable) {
            if (KotlinPluginModeProvider.Companion.isK1Mode()) return;

            PsiType variableType = variable.getType();
            Collection<PsiExpression> rightHandSides = variableAssignmentRightHandSides.get(variable);
            for (PsiExpression expr : rightHandSides) {
                PsiType exprType = expr.getType();
                PsiType referenceType = getReferenceType(expr);

                if (isNullable(exprType) || isNullable(referenceType)) {
                    registerNullableType(variableType);
                } else if (isNotNull(variableType)) {
                    propagateRawNullabilityWithPsiContext(variableType, exprType);
                    propagateRawNullabilityWithPsiContext(variableType, referenceType);
                }

                unifyGenericNullabilityWithPsiContext(variableType, exprType);
                unifyGenericNullabilityWithPsiContext(variableType, referenceType);
            }
        }

        // TODO rethink how to use this properly
        private static @Nullable PsiType getReferenceType(PsiExpression expression) {
            if (expression instanceof PsiReferenceExpression referenceExpression) {
                PsiElement target = referenceExpression.resolve();
                if (target instanceof PsiVariable variable) {
                    return variable.getType();
                }
            } else if (expression instanceof PsiMethodCallExpression methodCallExpression) {
                PsiMethod method = methodCallExpression.resolveMethod();
                if (method != null) {
                    return method.getReturnType();
                }
            }
            return expression.getType();
        }

        private void unifyGenericNullabilityWithPsiContext(@Nullable PsiType type1, @Nullable PsiType type2) {
            propagateGenericNullabilityWithPsiContext(type1, type2);
            propagateGenericNullabilityWithPsiContext(type2, type1);
        }

        private void propagateGenericNullabilityWithPsiContext(
                @Nullable PsiType originType,
                @Nullable PsiType targetType
        ) {
            if (originType == null || targetType == null) return;

            int prevCount = numAnnotationsAdded;
            propagateGenericNullability(originType, targetType, false);
            if (prevCount == numAnnotationsAdded) {
                // Nullability is already the same, we can stop here
                return;
            }

            PsiType psiContextType = getPsiContextType(targetType);
            if (psiContextType != null) {
                propagateGenericNullabilityWithPsiContext(originType, psiContextType);
            }
        }

        private static @Nullable PsiType getPsiContextType(@NotNull PsiType type) { // TODO it is not a chain!
            if (!(type instanceof PsiClassType classType)) return null;

            PsiElement psiContext = classType.getPsiContext();
            if (!(psiContext instanceof PsiJavaCodeReferenceElement javaCodeReferenceElement)) return null;

            PsiElement codeReferenceElementParent = javaCodeReferenceElement.getParent();
            if (!(codeReferenceElementParent instanceof PsiTypeElement typeElement)) return null;

            PsiType psiContextType = typeElement.getType();
            if (type == psiContextType) return null;

            return psiContextType;
        }

        @Override
        public void visitParameter(@NotNull PsiParameter parameter) {
            super.visitParameter(parameter);
            if (parameter.getType() instanceof PsiPrimitiveType) {
                return;
            }

            final PsiElement grandParent = parameter.getDeclarationScope();
            if (grandParent instanceof PsiMethod method) {
                if (method.getBody() != null) {
                    if (JavaSourceInference.inferNullability(parameter) == Nullability.NOT_NULL) {
                        registerNotNullAnnotation(parameter);
                    }

                    List<PsiReferenceExpression> references = parameterReferences.get(parameter);
                    for (PsiReferenceExpression expr : references) {
                        final PsiElement parent =
                                PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);

                        processReference(parameter, expr, parent);

                        if (isNotNull(method)) {
                            PsiElement toReturn = parent;
                            if (parent instanceof PsiConditionalExpression &&
                                ((PsiConditionalExpression) parent).getCondition() != expr) {  //todo check conditional operations
                                toReturn = parent.getParent();
                            }
                            if (toReturn instanceof PsiReturnStatement) {
                                registerNotNullAnnotation(parameter);
                            }
                        }
                    }
                }
            } else if (grandParent instanceof PsiForeachStatement foreachStatement) {
                for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(foreachStatement))) {
                    final PsiElement place = reference.getElement();
                    if (place instanceof PsiReferenceExpression expr) {
                        final PsiElement parent =
                                PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);
                        if (processReference(parameter, expr, parent)) {
                            propagateRawNullabilityToIterableComponentType(foreachStatement);
                        }
                    }
                }
            } else {
                registerAnnotationByNullAssignmentStatus(parameter);
            }
        }

        private void propagateRawNullabilityToIterableComponentType(@NotNull PsiForeachStatement foreachStatement) {
            if (KotlinPluginModeProvider.Companion.isK1Mode()) return;

            PsiType parameterType = foreachStatement.getIterationParameter().getType();
            if (!isNullable(parameterType) && !isNotNull(parameterType)) return;

            PsiExpression iteratedValue = foreachStatement.getIteratedValue();
            if (iteratedValue == null) return;

            PsiType componentType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
            if (componentType == null) return;

            propagateRawNullabilityWithPsiContext(parameterType, componentType);
        }

        private void propagateRawNullabilityWithPsiContext(@Nullable PsiType originType, @Nullable PsiType targetType) {
            if (originType == null || targetType == null) return;
            if (isNullable(targetType) || isNotNull(targetType)) return; // TODO is this too restrictive check?

            if (isNullable(originType)) {
                registerNullableType(targetType);
            } else if (isNotNull(originType)) {
                registerNotNullType(targetType);
            }

            PsiType psiContextType = getPsiContextType(targetType);
            if (psiContextType != null) {
                propagateRawNullabilityWithPsiContext(originType, psiContextType);
            }
        }

        private boolean processReference(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expr, PsiElement parent) {
            if (isUsedInAutoUnboxingContext(expr)) {
                registerNotNullAnnotation(variable);
                return true;
            }
            if (PsiUtil.isAccessedForWriting(expr)) return true;

            if (getExpressionDfaNullability(expr) == DfaNullability.NOT_NULL) {
                // If Java DFA can determine that the nullability of the reference is definitely not-null,
                // we are probably inside an "instance of" check (which is like a smart cast for Java DFA).
                // So we give up trying to guess the nullability of the declaration from this reference.
                //
                // However, try to process an argument reference to propagate generic nullability,
                // regardless of top-level nullability inferred from DFA,
                // since it is orthogonal to the nullability of type arguments.
                processArgumentReference(variable, expr, false);
                return false;
            }

            if (parent instanceof PsiThrowStatement) {
                registerNotNullAnnotation(variable);
                return true;
            } else if (parent instanceof PsiSynchronizedStatement) {
                registerNotNullAnnotation(variable);
                return true;
            } else if (parent instanceof PsiArrayAccessExpression) {
                // For both array and index expressions
                registerNotNullAnnotation(variable);
                return true;
            } else if (parent instanceof PsiBinaryExpression binOp) {
                PsiExpression opposite = null;
                final PsiExpression lOperand = binOp.getLOperand();
                final PsiExpression rOperand = binOp.getROperand();
                if (lOperand == expr) {
                    opposite = rOperand;
                } else if (rOperand == expr) {
                    opposite = lOperand;
                }

                if (opposite != null && opposite.getType() == PsiTypes.nullType()) {
                    if (DfaPsiUtil.isAssertionEffectively(binOp, binOp.getOperationTokenType() == JavaTokenType.NE)) {
                        registerNotNullAnnotation(variable);
                        return true;
                    }
                    registerNullableAnnotation(variable);
                    return true;
                }
            } else if (parent instanceof PsiReferenceExpression ref) {
                final PsiExpression qualifierExpression = ref.getQualifierExpression();
                if (qualifierExpression == expr) {
                    registerNotNullAnnotation(variable);
                    return true;
                } else {
                    PsiElement exprParent = expr.getParent();
                    while (exprParent instanceof PsiTypeCastExpression || exprParent instanceof PsiParenthesizedExpression) {
                        if (qualifierExpression == exprParent) {
                            registerNotNullAnnotation(variable);
                            return true;
                        }
                        exprParent = exprParent.getParent();
                    }
                }
            } else if (parent instanceof PsiAssignmentExpression assignment) {
                if (assignment.getRExpression() == expr &&
                    assignment.getLExpression() instanceof PsiReferenceExpression ref &&
                    ref.resolve() instanceof PsiVariable localVar && isNotNull(localVar)) {
                    registerNotNullAnnotation(variable);
                    return true;
                }
            } else if (parent instanceof PsiForeachStatement forEach) {
                if (forEach.getIteratedValue() == expr) {
                    registerNotNullAnnotation(variable);
                    return true;
                }
            } else if (parent instanceof PsiSwitchStatement switchStatement && switchStatement.getExpression() == expr) {
                registerNotNullAnnotation(variable);
                return true;
            }

            if (processArgumentReference(variable, expr, true)) {
                return true;
            }

            return false;
        }

        private boolean processArgumentReference(
                @NotNull PsiVariable variable,
                @NotNull PsiReferenceExpression expr,
                boolean updateRawNullability
        ) {
            final PsiCall call = PsiTreeUtil.getParentOfType(expr, PsiCall.class);
            if (call == null) return false;
            final PsiExpressionList argumentList = call.getArgumentList();
            if (argumentList == null) return false;

            final PsiExpression[] args = argumentList.getExpressions();
            int idx = ArrayUtil.find(args, expr);
            if (idx < 0) return false;

            final PsiMethod resolvedMethod = call.resolveMethod();
            if (resolvedMethod == null) return false;

            final PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
            //not vararg
            if (idx >= parameters.length) return false;

            PsiVariable resolvedToParam = parameters[idx];
            if (resolvedToParam instanceof LightRecordCanonicalConstructor.LightRecordConstructorParameter) {
                resolvedToParam = ((LightRecordCanonicalConstructor.LightRecordConstructorParameter) resolvedToParam).getRecordComponent();
            }
            if (resolvedToParam == null) return false;

            PsiType variableType = variable.getType();
            PsiType parameterType = resolvedToParam.getType();

            unifyGenericNullability(variableType, parameterType);

            if (!updateRawNullability) return false;

            if (isNotNull(resolvedToParam) ||
                (variableType instanceof PsiArrayType && !isVarArgs(variable) && isVarArgs(resolvedToParam))) {
                // In the case of varargs in Kotlin, the spread operator needs to be applied to a not-null array
                registerNotNullAnnotation(variable);
                return true;
            }

            return false;
        }

        private static boolean isVarArgs(PsiVariable variable) {
            if (variable instanceof PsiParameter) {
                return ((PsiParameter) variable).isVarArgs();
            } else if (variable instanceof PsiRecordComponent) {
                return ((PsiRecordComponent) variable).isVarArgs();
            }
            return false;
        }

        private void unifyGenericNullability(PsiType type1, PsiType type2) {
            propagateGenericNullability(type1, type2, false);
            propagateGenericNullability(type2, type1, false);
        }

        /**
         * Updates nullability of the target type and its type arguments recursively from the origin type.
         */
        private void propagateGenericNullability(PsiType originType, PsiType targetType, boolean updateRawType) {
            if (KotlinPluginModeProvider.Companion.isK1Mode()) return;

            if (updateRawType) {
                if (isNotNull(originType)) {
                    registerNotNullType(targetType);
                } else if (isNullable(originType)) {
                    registerNullableType(targetType);
                }
            }

            if (originType instanceof PsiArrayType originArrayType && targetType instanceof PsiArrayType targetArrayType) {
                PsiType originComponentType = originArrayType.getComponentType();
                PsiType targetComponentType = targetArrayType.getComponentType();
                propagateGenericNullability(originComponentType, targetComponentType, true);
            }

            if (!(originType instanceof PsiClassType originClassType)) return;
            if (!(targetType instanceof PsiClassType targetClassType)) return;

            PsiType[] originTypeArguments = originClassType.getParameters();
            PsiType[] targetTypeArguments = targetClassType.getParameters();
            if (originTypeArguments.length != targetTypeArguments.length) return;

            for (int i = 0; i < originTypeArguments.length; i++) {
                PsiType originTypeArgument = originTypeArguments[i];
                PsiType targetTypeArgument = targetTypeArguments[i];
                propagateGenericNullability(originTypeArgument, targetTypeArgument, true);
            }
        }

        @Override
        public void visitField(@NotNull PsiField field) {
            super.visitField(field);
            if (field instanceof PsiEnumConstant) return;

            PsiType fieldType = field.getType();
            if (fieldType instanceof PsiPrimitiveType) return;

            if (!hasRawNullability(fieldType)) {
                registerAnnotationByNullAssignmentStatus(field);
            }

            inferNullabilityFromVariableReferences(field);
            propagateNullabilityFromVariable(field);
        }

        @Override
        public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
            if (KotlinPluginModeProvider.Companion.isK1Mode()) return;

            // Update the nullability of type arguments in constructor calls
            PsiElement target = reference.resolve();
            if (!(target instanceof PsiClass klass)) return;

            PsiTypeParameter[] typeParameters = klass.getTypeParameters();
            PsiType[] typeArguments = reference.getTypeParameters();

            updateNullabilityOfTypeArguments(typeParameters, typeArguments);
        }

        // If type parameters come from Kotlin, we propagate their nullability to the type arguments
        // TODO support not only raw but generic propagation
        private void updateNullabilityOfTypeArguments(PsiTypeParameter[] typeParameters, PsiType[] typeArguments) {
            if (KotlinPluginModeProvider.Companion.isK1Mode()) return;
            if (typeParameters.length != typeArguments.length) return;

            for (int i = 0; i < typeParameters.length; i++) {
                PsiTypeParameter typeParameter = typeParameters[i];
                PsiType typeArgument = typeArguments[i];
                Nullability nullability = getTypeParameterNullability(typeParameter);

                switch (nullability) {
                    case NULLABLE -> registerNullableType(typeArgument);
                    case NOT_NULL -> registerNotNullType(typeArgument);
                }
            }
        }

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            if (KotlinPluginModeProvider.Companion.isK1Mode()) return;
            super.visitMethodCallExpression(expression);

            PsiMethod method = expression.resolveMethod();
            if (method == null) return;

            // Unify generic nullability of parameter-argument pairs
            PsiExpression[] argumentList = expression.getArgumentList().getExpressions();
            for (PsiExpression argument : argumentList) {
                PsiParameter parameter = MethodCallUtils.getParameterForArgument(argument);
                if (parameter == null) continue;
                PsiType parameterType = parameter.getType();
                PsiType argumentType = getReferenceType(argument);
                unifyGenericNullability(parameterType, argumentType);
            }

            // Update nullability of type arguments
            PsiTypeParameter[] typeParameters = method.getTypeParameters();
            PsiType[] typeArguments = expression.getTypeArguments();
            updateNullabilityOfTypeArguments(typeParameters, typeArguments);
        }

        @Override
        public void visitNewExpression(@NotNull PsiNewExpression expression) {
            if (KotlinPluginModeProvider.Companion.isK1Mode()) return;
            super.visitNewExpression(expression);

            // Update array initializer component type nullability
            // based on the nullability of initializing member expressions
            if (!(expression.getType() instanceof PsiArrayType arrayType)) return;
            PsiType componentType = arrayType.getComponentType();

            PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
            if (arrayInitializer == null) return;

            PsiType arrayInitializerType = arrayInitializer.getType();
            if (!(arrayInitializerType instanceof PsiArrayType arrayInitializerArrayType)) return;
            PsiType arrayInitializerComponentType = arrayInitializerArrayType.getComponentType();

            PsiExpression[] initializers = arrayInitializer.getInitializers();
            if (initializers.length == 0) return;

            for (PsiExpression initializer : initializers) {
                Nullability nullability = NullabilityUtil.getExpressionNullability(initializer, true);
                if (nullability == Nullability.NULLABLE) {
                    registerNullableType(componentType);
                    registerNullableType(arrayInitializerComponentType);
                    return;
                } else if (nullability == Nullability.UNKNOWN) {
                    return;
                }
            }

            // At this point, all array initializer expressions are not-null
            registerNotNullType(componentType);
            registerNotNullType(arrayInitializerComponentType);
        }
    }
}