// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k;

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This is a copy of com.intellij.codeInspection.inferNullity.NullityInferrer
 * with some modifications to better support J2K. These modifications may be later
 * ported back and this copy may be deleted.
 */
@SuppressWarnings("DuplicatedCode")
class J2KNullityInferrer {
    private static final int MAX_PASSES = 10;
    private int numAnnotationsAdded;
    private final List<SmartPsiElementPointer<? extends PsiModifierListOwner>> myNotNullSet = new ArrayList<>();
    private final List<SmartPsiElementPointer<? extends PsiModifierListOwner>> myNullableSet = new ArrayList<>();
    private final SmartPointerManager myPointerManager;

    J2KNullityInferrer(@NotNull Project project) {
        myPointerManager = SmartPointerManager.getInstance(project);
    }

    public List<SmartPsiElementPointer<? extends PsiModifierListOwner>> getNotNullSet() {
        return myNotNullSet;
    }

    public List<SmartPsiElementPointer<? extends PsiModifierListOwner>> getNullableSet() {
        return myNullableSet;
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
                expr -> expr instanceof PsiMethodCallExpression && isNullable(((PsiMethodCallExpression) expr).resolveMethod()))) {
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

        SearchScope scope = getScope(variable);
        if (scope == null) {
            return false;
        }

        final Query<PsiReference> references = ReferencesSearch.search(variable, scope);
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

        final Query<PsiReference> references = ReferencesSearch.search(variable, scope);
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

    private void registerNullableAnnotation(@NotNull PsiModifierListOwner declaration) {
        registerAnnotation(declaration, true);
    }

    private void registerNotNullAnnotation(@NotNull PsiModifierListOwner declaration) {
        registerAnnotation(declaration, false);
    }

    private void registerAnnotation(@NotNull PsiModifierListOwner declaration, boolean isNullable) {
        final SmartPsiElementPointer<PsiModifierListOwner> declarationPointer = myPointerManager.createSmartPsiElementPointer(declaration);
        if (isNullable) {
            myNullableSet.add(declarationPointer);
        } else {
            myNotNullSet.add(declarationPointer);
        }
        numAnnotationsAdded++;
    }

    private boolean registerAnnotationByNullAssignmentStatus(PsiVariable variable) {
        if (variableNeverAssignedNull(variable)) {
            registerNotNullAnnotation(variable);
            return true;
        } else if (variableSometimesAssignedNull(variable)) {
            registerNullableAnnotation(variable);
            return true;
        }

        return false;
    }

    private boolean isNotNull(@Nullable PsiModifierListOwner owner) {
        if (owner == null) return false;
        if (NullableNotNullManager.isNotNull(owner)) {
            return true;
        }
        final SmartPsiElementPointer<PsiModifierListOwner> pointer = myPointerManager.createSmartPsiElementPointer(owner);
        return myNotNullSet.contains(pointer);
    }

    private boolean isNullable(@Nullable PsiModifierListOwner owner) {
        if (owner == null) return false;
        if (NullableNotNullManager.isNullable(owner)) {
            return true;
        }
        final SmartPsiElementPointer<PsiModifierListOwner> pointer = myPointerManager.createSmartPsiElementPointer(owner);
        return myNullableSet.contains(pointer);
    }

    private boolean hasNullability(@NotNull PsiModifierListOwner owner) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
        NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
        if (info != null && !info.isInferred() && info.getNullability() != Nullability.UNKNOWN) return true;
        final SmartPsiElementPointer<PsiModifierListOwner> pointer = myPointerManager.createSmartPsiElementPointer(owner);
        return myNotNullSet.contains(pointer) || myNullableSet.contains(pointer);
    }

    private class NullityInferrerVisitor extends JavaRecursiveElementWalkingVisitor {

        @Override
        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (method.isConstructor() || method.getReturnType() instanceof PsiPrimitiveType) {
                return;
            }

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

            if (hasNullability(method)) {
                return;
            }

            Nullability nullability = DfaUtil.inferMethodNullability(method);
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

        @Override
        public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            if (variable.getType() instanceof PsiPrimitiveType || hasNullability(variable)) {
                return;
            }

            if (registerAnnotationByNullAssignmentStatus(variable)) return;
            inferNullabilityFromVariableReferences(variable);
        }

        private void inferNullabilityFromVariableReferences(@NotNull PsiVariable variable) {
            if (KotlinPluginModeProvider.Companion.isK1Mode()) {
                // Try not to break K1 nullability inference
                return;
            }

            SearchScope scope = getScope(variable);
            if (scope == null) return;

            final Query<PsiReference> references = ReferencesSearch.search(variable, scope);
            for (final PsiReference reference : references) {
                final PsiElement element = reference.getElement();
                if (!(element instanceof PsiReferenceExpression referenceExpression)) {
                    continue;
                }

                final PsiElement refParent =
                        PsiTreeUtil.skipParentsOfType(referenceExpression, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);

                processReference(variable, referenceExpression, refParent);

                if (isNullable(variable)) {
                    // We determined that the variable is nullable, so there is no need to check the rest of references.
                    // But if it is not-null, we keep going because we may find that it is nullable later, after all.
                    break;
                }
            }
        }

        @Override
        public void visitParameter(@NotNull PsiParameter parameter) {
            super.visitParameter(parameter);
            if (parameter.getType() instanceof PsiPrimitiveType || hasNullability(parameter)) {
                return;
            }

            final PsiElement grandParent = parameter.getDeclarationScope();
            if (grandParent instanceof PsiMethod method) {
                if (method.getBody() != null) {
                    if (JavaSourceInference.inferNullability(parameter) == Nullability.NOT_NULL) {
                        registerNotNullAnnotation(parameter);
                        return;
                    }

                    for (PsiReferenceExpression expr : VariableAccessUtils.getVariableReferences(parameter, method)) {
                        final PsiElement parent =
                                PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);
                        if (processReference(parameter, expr, parent)) return;
                        if (isNotNull(method)) {
                            PsiElement toReturn = parent;
                            if (parent instanceof PsiConditionalExpression &&
                                ((PsiConditionalExpression) parent).getCondition() != expr) {  //todo check conditional operations
                                toReturn = parent.getParent();
                            }
                            if (toReturn instanceof PsiReturnStatement) {
                                registerNotNullAnnotation(parameter);
                                return;
                            }
                        }
                    }
                }
            } else if (grandParent instanceof PsiForeachStatement) {
                for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(grandParent))) {
                    final PsiElement place = reference.getElement();
                    if (place instanceof PsiReferenceExpression expr) {
                        final PsiElement parent =
                                PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);
                        if (processReference(parameter, expr, parent)) return;
                    }
                }
            } else {
                registerAnnotationByNullAssignmentStatus(parameter);
            }
        }

        private boolean processReference(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expr, PsiElement parent) {
            if (NullabilityKt.isUsedInAutoUnboxingContext(expr)) {
                registerNotNullAnnotation(variable);
                return true;
            }

            if (PsiUtil.isAccessedForWriting(expr)) return true;

            if (NullabilityKt.getDfaNullability(expr) == DfaNullability.NOT_NULL) {
                // If Java DFA can determine that the nullability of the reference is definitely not-null,
                // we are probably inside an "instance of" check (which is like a smart cast for Java DFA).
                // So we give up trying to guess the nullability of the declaration from this reference.
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

            if (processArgumentReference(variable, expr)) {
                return true;
            }

            return false;
        }

        private boolean processArgumentReference(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expr) {
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

            final PsiParameter resolvedToParam = parameters[idx];
            boolean isArray = variable.getType() instanceof PsiArrayType;
            boolean isVarArgs = variable instanceof PsiParameter parameter && parameter.isVarArgs();

            if (isNotNull(resolvedToParam) || (isArray && !isVarArgs && resolvedToParam.isVarArgs())) {
                // In the case of varargs in Kotlin, the spread operator needs to be applied to a not-null array
                registerNotNullAnnotation(variable);
                return true;
            }

            return false;
        }

        @Override
        public void visitField(@NotNull PsiField field) {
            super.visitField(field);
            if (field instanceof PsiEnumConstant || field.getType() instanceof PsiPrimitiveType || hasNullability(field)) {
                return;
            }

            if (registerAnnotationByNullAssignmentStatus(field)) return;
            if (!isPrivate(field)) return;
            inferNullabilityFromVariableReferences(field);
        }
    }
}