/*
 * User: anna
 * Date: 18-Aug-2009
 */
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.*;

public class AtomicConversionRule extends TypeConversionRule {
  private static final Logger LOG = Logger.getInstance("#" + AtomicConversionRule.class.getName());


  @Override
  public TypeConversionDescriptor findConversion(PsiType from,
                                                 PsiType to,
                                                 PsiMember member,
                                                 PsiElement context,
                                                 TypeMigrationLabeler labeler) {
    if (to instanceof PsiClassType && isAtomicTypeMigration(from, (PsiClassType)to)) {
      return findDirectConversion(context, to, from);
    }
    else if (from instanceof PsiClassType && isAtomicTypeMigration(to, (PsiClassType)from)) {
      return findReverseConversion(context);
    }
    if (PsiUtil.resolveClassInType(to) instanceof PsiTypeParameter) return findReverseConversion(context);
    return null;
  }

  private static boolean isAtomicTypeMigration(PsiType from, PsiClassType to) {
    if (from == PsiType.INT && to.getCanonicalText().equals(AtomicInteger.class.getName())) {
      return true;
    }
    if (from.equals(PsiType.INT.createArrayType()) && to.getCanonicalText().equals(AtomicIntegerArray.class.getName())) {
      return true;
    }
    if (from == PsiType.LONG && to.getCanonicalText().equals(AtomicLong.class.getName())) {
      return true;
    }
    if (from.equals(PsiType.LONG.createArrayType()) && to.getCanonicalText().equals(AtomicLongArray.class.getName())) {
      return true;
    }
    if (from == PsiType.BOOLEAN && to.getCanonicalText().equals(AtomicBoolean.class.getName())) {
      return true;
    }
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(to);
    final PsiClass atomicClass = resolveResult.getElement();

    if (atomicClass != null) {
      final String typeQualifiedName = atomicClass.getQualifiedName();
      if (!Comparing.strEqual(typeQualifiedName, AtomicReference.class.getName()) &&
          !Comparing.strEqual(typeQualifiedName, AtomicReferenceArray.class.getName())) {
        return false;
      }
      final PsiTypeParameter[] typeParameters = atomicClass.getTypeParameters();
      if (typeParameters.length != 1) return false;
      final PsiType toTypeParameterValue = resolveResult.getSubstitutor().substitute(typeParameters[0]);
      if (toTypeParameterValue != null) {
        if (from.getDeepComponentType() instanceof PsiPrimitiveType) {
          final PsiPrimitiveType unboxedInitialType = PsiPrimitiveType.getUnboxedType(toTypeParameterValue);
          if (unboxedInitialType != null) {
            return TypeConversionUtil.areTypesConvertible(from.getDeepComponentType(), unboxedInitialType);
          }
        }
        else {
          return TypeConversionUtil.isAssignable(toTypeParameterValue, from.getDeepComponentType());
        }
      }
    }
    return false;
  }

  @Nullable
  public static TypeConversionDescriptor findDirectConversion(PsiElement context, PsiType to, PsiType from) {
    final PsiClass toTypeClass = PsiUtil.resolveClassInType(to);
    LOG.assertTrue(toTypeClass != null);
    final String qualifiedName = toTypeClass.getQualifiedName();
    if (qualifiedName != null) {
      if (qualifiedName.equals(AtomicInteger.class.getName()) || qualifiedName.equals(AtomicLong.class.getName())) {

        if (context instanceof PsiPostfixExpression) {
          final IElementType operationSign = ((PsiPostfixExpression)context).getOperationTokenType();
          if (operationSign == JavaTokenType.MINUSMINUS) {
            return new TypeConversionDescriptor("$qualifier$--", "$qualifier$.getAndDecrement()");
          }
          if (operationSign == JavaTokenType.PLUSPLUS) {
            return new TypeConversionDescriptor("$qualifier$++", "$qualifier$.getAndIncrement()");
          }

        }
        else if (context instanceof PsiPrefixExpression) {
          final IElementType operationSign = ((PsiPrefixExpression)context).getOperationTokenType();
          if (operationSign == JavaTokenType.MINUSMINUS) {
            return new TypeConversionDescriptor("--$qualifier$", "$qualifier$.decrementAndGet()");
          }
          if (operationSign == JavaTokenType.PLUSPLUS) {
            return new TypeConversionDescriptor("++$qualifier$", "$qualifier$.incrementAndGet()");
          }

        }
        else if (context instanceof PsiBinaryExpression) {
          final IElementType operationSign = ((PsiBinaryExpression)context).getOperationTokenType();
          if (operationSign == JavaTokenType.PLUS) {
            return new TypeConversionDescriptor("$qualifier$ + $delta$", "$qualifier$.addAndGet($delta$)");
          }
          if (operationSign == JavaTokenType.MINUS) {
            return new TypeConversionDescriptor("$qualifier$ - $delta$", "$qualifier$.addAndGet(-($delta$))");
          }

        }
        else if (context instanceof PsiAssignmentExpression) {
          final PsiJavaToken signToken = ((PsiAssignmentExpression)context).getOperationSign();
          final IElementType operationSign = signToken.getTokenType();
          final String sign = signToken.getText();
          if (operationSign == JavaTokenType.PLUSEQ || operationSign == JavaTokenType.MINUSEQ) {
            return new TypeConversionDescriptor("$qualifier$ " + sign + " $val$", "$qualifier$.getAndAdd(" +
                                                                                  (operationSign == JavaTokenType.MINUSEQ ? "-" : "") +
                                                                                  "($val$))");
          }
        }
      }
      else if (qualifiedName.equals(AtomicIntegerArray.class.getName()) || qualifiedName.equals(AtomicLongArray.class.getName())) {
        PsiElement parentExpression = context.getParent();
        if (parentExpression instanceof PsiPostfixExpression) {
          final IElementType operationSign = ((PsiPostfixExpression)parentExpression).getOperationTokenType();
          if (operationSign == JavaTokenType.MINUSMINUS) {
            return new TypeConversionDescriptor("$qualifier$[$idx$]--", "$qualifier$.getAndDecrement($idx$)",
                                                (PsiExpression)parentExpression);
          }
          if (operationSign == JavaTokenType.PLUSPLUS) {
            return new TypeConversionDescriptor("$qualifier$[$idx$]++", "$qualifier$.getAndIncrement($idx$)",
                                                (PsiExpression)parentExpression);
          }

        }
        else if (parentExpression instanceof PsiPrefixExpression) {
          final IElementType operationSign = ((PsiPrefixExpression)parentExpression).getOperationTokenType();
          if (operationSign == JavaTokenType.MINUSMINUS) {
            return new TypeConversionDescriptor("--$qualifier$[$idx$]", "$qualifier$.decrementAndGet($idx$)",
                                                (PsiExpression)parentExpression);
          }
          if (operationSign == JavaTokenType.PLUSPLUS) {
            return new TypeConversionDescriptor("++$qualifier$[$idx$]", "$qualifier$.incrementAndGet($idx$)",
                                                (PsiExpression)parentExpression);
          }

        }
        else if (parentExpression instanceof PsiBinaryExpression) {
          final IElementType operationSign = ((PsiBinaryExpression)parentExpression).getOperationTokenType();
          if (operationSign == JavaTokenType.PLUS) {
            return new TypeConversionDescriptor("$qualifier$[$idx$] + $delta$", "$qualifier$.addAndGet($idx$, $delta$)",
                                                (PsiExpression)parentExpression);
          }
          if (operationSign == JavaTokenType.MINUS) {
            return new TypeConversionDescriptor("$qualifier$[$idx$] - $delta$", "$qualifier$.addAndGet($idx$, -($delta$))",
                                                (PsiExpression)parentExpression);
          }

        }
        else if (parentExpression instanceof PsiAssignmentExpression) {
          final PsiJavaToken signToken = ((PsiAssignmentExpression)parentExpression).getOperationSign();
          final IElementType operationSign = signToken.getTokenType();
          final String sign = signToken.getText();
          if (operationSign == JavaTokenType.PLUSEQ || operationSign == JavaTokenType.MINUSEQ) {
            return new TypeConversionDescriptor("$qualifier$[$idx$] " + sign + " $val$", "$qualifier$.getAndAdd($idx$, " +
                                                                                         (operationSign == JavaTokenType.MINUSEQ
                                                                                          ? "-"
                                                                                          : "") +
                                                                                         "($val$))", (PsiExpression)parentExpression);
          }
        }
      }
    }
    return from instanceof PsiArrayType
           ? findDirectConversionForAtomicReferenceArray(context, to, from)
           : findDirectConversionForAtomicReference(context, to, from);
  }

  @Nullable
  private static TypeConversionDescriptor findDirectConversionForAtomicReference(PsiElement context, PsiType to, PsiType from) {
    final PsiElement parent = context.getParent();
    if (parent instanceof PsiVariable) {
      String typeText = to.getPresentableText();
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(to);
      final PsiClass atomicClass = resolveResult.getElement();
      LOG.assertTrue(atomicClass != null);
      final PsiTypeParameter[] typeParameters = atomicClass.getTypeParameters();
      if (typeParameters.length == 1) {
        final PsiType initial = resolveResult.getSubstitutor().substitute(typeParameters[0]);
        final PsiPrimitiveType unboxedInitialType = PsiPrimitiveType.getUnboxedType(initial);
        if (unboxedInitialType != null) {
          LOG.assertTrue(initial != null);
          if (from instanceof PsiPrimitiveType) {
            final PsiClassType boxedFromType = ((PsiPrimitiveType)from).getBoxedType(atomicClass);
            LOG.assertTrue(boxedFromType != null);
            if (!TypeConversionUtil.isAssignable(initial, boxedFromType)) {
              return new TypeConversionDescriptor("$val$", "new " + typeText + "((" + unboxedInitialType.getCanonicalText() + ")$val$)");
            }
          }
        }
      }
      return new TypeConversionDescriptor("$val$", "new " + typeText + "($val$)");
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final IElementType operationSign = ((PsiAssignmentExpression)parent).getOperationTokenType();
      if (operationSign == JavaTokenType.EQ) {
        return new TypeConversionDescriptor("$qualifier$ = $val$", "$qualifier$.set($val$)", (PsiAssignmentExpression)parent);
      }
    }

    if (context instanceof PsiReferenceExpression) {
      return new TypeConversionDescriptor("$qualifier$", "$qualifier$.get()");
    }
    else if (context instanceof PsiBinaryExpression) {
      if (((PsiBinaryExpression)context).getOperationTokenType() == JavaTokenType.EQEQ) {
        return new TypeConversionDescriptor("$qualifier$==$val$", "$qualifier$.get()==$val$");
      }
    }

    if (parent instanceof PsiExpressionStatement) {
      if (context instanceof PsiPostfixExpression) {
        final String sign = ((PsiPostfixExpression)context).getOperationSign().getText();
        return new TypeConversionDescriptor("$qualifier$" + sign, "$qualifier$.getAndSet(" +
                                                                  getBoxedWrapper(from, to, "$qualifier$.get() " + sign.charAt(0) + " 1") +
                                                                  ")");
      }
      else if (context instanceof PsiPrefixExpression) {
        final String sign = ((PsiPrefixExpression)context).getOperationSign().getText();
        return new TypeConversionDescriptor(sign + "$qualifier$", "$qualifier$.set(" +
                                                                  getBoxedWrapper(from, to, "$qualifier$.get() " + sign.charAt(0) + " 1") +
                                                                  ")");
      }
      else if (context instanceof PsiBinaryExpression) {
        final String sign = ((PsiBinaryExpression)context).getOperationSign().getText();
        return new TypeConversionDescriptor("$qualifier$" + sign + "$val$",
                                            "$qualifier$.set(" + getBoxedWrapper(from, to, "$qualifier$.get() " + sign + " $val$)") + ")");
      }
      else if (context instanceof PsiAssignmentExpression) {
        final PsiJavaToken signToken = ((PsiAssignmentExpression)context).getOperationSign();
        final IElementType operationSign = signToken.getTokenType();
        final String sign = signToken.getText();
        if (operationSign == JavaTokenType.EQ) {
          return new TypeConversionDescriptor("$qualifier$ = $val$", "$qualifier$.set($val$)");
        }
        else {
          return new TypeConversionDescriptor("$qualifier$" + sign + "$val$", "$qualifier$.set(" +
                                                                              getBoxedWrapper(from, to, "$qualifier$.get() " +
                                                                                                        sign.charAt(0) +
                                                                                                        " $val$") +
                                                                              ")");
        }
      }
    }
    return null;
  }

  @Nullable
  private static TypeConversionDescriptor findDirectConversionForAtomicReferenceArray(PsiElement context, PsiType to, PsiType from) {
    LOG.assertTrue(from instanceof PsiArrayType);
    from = ((PsiArrayType)from).getComponentType();
    final PsiElement parentExpression = context.getParent();
    final PsiElement parent = parentExpression.getParent();

    if (parent instanceof PsiAssignmentExpression) {
      final IElementType operationSign = ((PsiAssignmentExpression)parent).getOperationTokenType();
      if (operationSign == JavaTokenType.EQ) {
        return new TypeConversionDescriptor("$qualifier$[$idx$] = $val$", "$qualifier$.set($idx$, $val$)", (PsiAssignmentExpression)parent);
      }
    }

    else if (parent instanceof PsiMethodCallExpression) {
      return new TypeConversionDescriptor("$qualifier$[$idx$]", "$qualifier$.get($idx$)", (PsiExpression)parent);
    }

    if (parentExpression instanceof PsiReferenceExpression) {
      return new TypeConversionDescriptor("$qualifier$[$idx$]", "$qualifier$.get($idx$)", (PsiExpression)parentExpression);
    }

    else if (parentExpression instanceof PsiBinaryExpression) {
      if (((PsiBinaryExpression)parentExpression).getOperationTokenType() == JavaTokenType.EQEQ) {
        return new TypeConversionDescriptor("$qualifier$[$idx$]==$val$", "$qualifier$.get($idx$)==$val$", (PsiExpression)parentExpression);
      }
    }

    else if (parentExpression instanceof PsiVariable) {
      LOG.assertTrue(context instanceof PsiNewExpression);
      final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)context).getArrayInitializer();
      String typeText = to.getPresentableText();
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(to);
      final PsiClass atomicClass = resolveResult.getElement();
      LOG.assertTrue(atomicClass != null);
      final PsiTypeParameter[] typeParameters = atomicClass.getTypeParameters();
      if (typeParameters.length == 1) {
        final PsiType initial = resolveResult.getSubstitutor().substitute(typeParameters[0]);
        final PsiPrimitiveType unboxedInitialType = PsiPrimitiveType.getUnboxedType(initial);
        if (unboxedInitialType != null) {
          LOG.assertTrue(initial != null);
          if (from instanceof PsiPrimitiveType) {
            final PsiClassType boxedFromType = ((PsiPrimitiveType)from).getBoxedType(atomicClass);
            LOG.assertTrue(boxedFromType != null);
            final String qualifiedName = atomicClass.getQualifiedName();
            LOG.assertTrue(qualifiedName != null);
            if (qualifiedName.equals(AtomicReferenceArray.class.getName())) {
              return arrayInitializer != null ?
                     new TypeConversionDescriptor("new $type$[]{$initializer$}", "new " + typeText + "(new " + boxedFromType.getClassName() + "[] {$initializer$})") :
                     new TypeConversionDescriptor("new $type$ [$length$]", "new " + typeText + "(new " + boxedFromType.getClassName() + "[$length$])");
            }
          }
        }
      }
      return arrayInitializer != null ? new TypeConversionDescriptor("new $type$[] {$initializer$}", "new " + typeText + "(new $type$[] {$initializer$})")
                                      : new TypeConversionDescriptor("new $type$ [$length$]", "new " + typeText + "(new $type$[$length$])");
    }



    if (parent instanceof PsiExpressionStatement) {
      if (parentExpression instanceof PsiPostfixExpression) {
        final String sign = ((PsiPostfixExpression)parentExpression).getOperationSign().getText();
        return new TypeConversionDescriptor("$qualifier$[$idx$]" + sign, "$qualifier$.getAndSet($idx$, " +
                                                                         getBoxedWrapper(from, to,
                                                                                         "$qualifier$.get($idx$) " + sign.charAt(0) + " 1") +
                                                                         ")", (PsiExpression)parentExpression);
      }
      else if (parentExpression instanceof PsiPrefixExpression) {
        final String sign = ((PsiPrefixExpression)parentExpression).getOperationSign().getText();
        return new TypeConversionDescriptor(sign + "$qualifier$[$idx$]", "$qualifier$.set($idx$, " +
                                                                         getBoxedWrapper(from, to,
                                                                                         "$qualifier$.get($idx$) " + sign.charAt(0) + " 1") +
                                                                         ")", (PsiExpression)parentExpression);
      }
      else if (parentExpression instanceof PsiBinaryExpression) {
        final String sign = ((PsiBinaryExpression)parentExpression).getOperationSign().getText();
        return new TypeConversionDescriptor("$qualifier$[$idx$]" + sign + "$val$", "$qualifier$.set($idx$, " +
                                                                                   getBoxedWrapper(from, to, "$qualifier$.get($idx$) " +
                                                                                                             sign +
                                                                                                             " $val$)") +
                                                                                   ")", (PsiExpression)parentExpression);
      }
      else if (parentExpression instanceof PsiAssignmentExpression) {
        final PsiJavaToken signToken = ((PsiAssignmentExpression)parentExpression).getOperationSign();
        final IElementType operationSign = signToken.getTokenType();
        final String sign = signToken.getText();
        if (operationSign == JavaTokenType.EQ) {
          return new TypeConversionDescriptor("$qualifier$[$idx$] = $val$", "$qualifier$.set($idx$, $val$)", (PsiExpression)parentExpression);
        }
        else {
          return new TypeConversionDescriptor("$qualifier$[$idx$]" + sign + "$val$", "$qualifier$.set($idx$, " +
                                                                                     getBoxedWrapper(from, to, "$qualifier$.get($idx$) " +
                                                                                                               sign.charAt(0) +
                                                                                                               " $val$") +
                                                                                     ")", (PsiExpression)parentExpression);
        }
      }
    }
    return null;
  }

  private static String getBoxedWrapper(final PsiType from, final PsiType to, @NotNull String arg) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(to);
    final PsiClass atomicClass = resolveResult.getElement();
    LOG.assertTrue(atomicClass != null);
    final PsiTypeParameter[] typeParameters = atomicClass.getTypeParameters();
    if (typeParameters.length == 1) {
      final PsiType initial = resolveResult.getSubstitutor().substitute(typeParameters[0]);
      final PsiPrimitiveType unboxedInitialType = PsiPrimitiveType.getUnboxedType(initial);
      if (unboxedInitialType != null) {
        LOG.assertTrue(initial != null);
        if (from instanceof PsiPrimitiveType) {
          final PsiClassType boxedFromType = ((PsiPrimitiveType)from).getBoxedType(atomicClass);
          LOG.assertTrue(boxedFromType != null);
          return "new " + initial.getPresentableText() + "((" + unboxedInitialType.getCanonicalText() + ")(" + arg + "))";
        }
      }
    }
    return arg;
  }

  @Nullable
  private static TypeConversionDescriptor findReverseConversion(PsiElement context) {
    if (context instanceof PsiReferenceExpression) {
      if (context.getParent() instanceof PsiMethodCallExpression) {
        return findReverseConversionForMethodCall(context);
      }
    }
    else if (context instanceof PsiNewExpression) {
      return new TypeConversionDescriptor("new $type$($qualifier$)", "$qualifier$");
    }
    else if (context instanceof PsiMethodCallExpression) {
      return findReverseConversionForMethodCall(((PsiMethodCallExpression)context).getMethodExpression());
    }
    return null;
  }

  @Nullable
  private static TypeConversionDescriptor findReverseConversionForMethodCall(PsiElement context) {
    final PsiElement resolved = ((PsiReferenceExpression)context).resolve();
    if (resolved instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)resolved;
      final int parametersCount = method.getParameterList().getParametersCount();
      final String resolvedName = method.getName();
      if (Comparing.strEqual(resolvedName, "get")) {
        return parametersCount == 0 ?
               new TypeConversionDescriptor("$qualifier$.get()", "$qualifier$") :
               new TypeConversionDescriptor("$qualifier$.get($idx$)", "$qualifier$[$idx$]");
      }
      else if (Comparing.strEqual(resolvedName, "set")) {
        return parametersCount == 1 ?
               new TypeConversionDescriptor("$qualifier$.set($val$)", "$qualifier$ = $val$") :
               new TypeConversionDescriptor("$qualifier$.set($idx$, $val$)", "$qualifier$[$idx$] = $val$");
      }
      else if (Comparing.strEqual(resolvedName, "addAndGet")) {
        return parametersCount == 1 ?
               new TypeConversionDescriptor("$qualifier$.addAndGet($delta$)", "$qualifier$ + $delta$") :
               new TypeConversionDescriptor("$qualifier$.addAndGet($idx$, $delta$)", "$qualifier$[$idx$] + $delta$");
      }
      else if (Comparing.strEqual(resolvedName, "incrementAndGet")) {
        return parametersCount == 0 ?
               new TypeConversionDescriptor("$qualifier$.incrementAndGet()", "++$qualifier$") :
               new TypeConversionDescriptor("$qualifier$.incrementAndGet($idx$)", "++$qualifier$[$idx$]");
      }
      else if (Comparing.strEqual(resolvedName, "decrementAndGet")) {
        return parametersCount == 0 ?
               new TypeConversionDescriptor("$qualifier$.decrementAndGet()", "--$qualifier$") :
               new TypeConversionDescriptor("$qualifier$.decrementAndGet($idx$)", "--$qualifier$[$idx$]");
      }
      else if (Comparing.strEqual(resolvedName, "getAndIncrement")) {
        return parametersCount == 0 ?
               new TypeConversionDescriptor("$qualifier$.getAndIncrement()", "$qualifier$++") :
               new TypeConversionDescriptor("$qualifier$.getAndIncrement($idx$)", "$qualifier$[$idx$]++");
      }
      else if (Comparing.strEqual(resolvedName, "getAndDecrement")) {
        return parametersCount == 0 ?
               new TypeConversionDescriptor("$qualifier$.getAndDecrement()", "$qualifier$--") :
               new TypeConversionDescriptor("$qualifier$.getAndDecrement($idx$)", "$qualifier$[$idx$]--");
      }
      else if (Comparing.strEqual(resolvedName, "getAndAdd")) {
        return parametersCount == 1?
               new TypeConversionDescriptor("$qualifier$.getAndAdd($val$)", "$qualifier$ += $val$") :
               new TypeConversionDescriptor("$qualifier$.getAndAdd($idx$, $val$)", "$qualifier$[$idx$] += $val$");
      }
      else if (Comparing.strEqual(resolvedName, "getAndSet")) {
        return parametersCount == 1 ?
               new TypeConversionDescriptor("$qualifier$.getAndSet($val$)", "$qualifier$ = $val$") :
               new TypeConversionDescriptor("$qualifier$.getAndSet($idx$, $val$)", "$qualifier$[$idx$] = $val$");
      }
    }
    return null;
  }

}