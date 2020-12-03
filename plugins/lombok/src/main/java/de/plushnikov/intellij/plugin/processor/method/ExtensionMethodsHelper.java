package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightParameter;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class ExtensionMethodsHelper {

  private static final Logger LOG = Logger.getInstance(ExtensionMethodsHelper.class);

  public static List<PsiExtensionMethod> getExtensionMethods(final @NotNull PsiClass targetClass,
                                                             final @NotNull String nameHint,
                                                             final @NotNull PsiElement place) {
    if (!(place instanceof PsiMethodCallExpression)) {
      return Collections.emptyList();
    }
    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)place).getMethodExpression();
    PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (qualifierExpression == null ||
        !nameHint.equals(methodExpression.getReferenceName()) ||
        qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).resolve() instanceof PsiClass) {
      return Collections.emptyList();
    }
    List<PsiExtensionMethod> result = new SmartList<>();

    @Nullable PsiClass context = PsiTreeUtil.getContextOfType(place, PsiClass.class);
    while (context != null) {
      final @Nullable PsiAnnotation annotation = context.getAnnotation(LombokClassNames.EXTENSION_METHOD);
      if (annotation != null) {

        final Set<PsiClass> providers = PsiAnnotationUtil.getAnnotationValues(annotation, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, PsiType.class).stream()
          .filter(PsiClassType.class::isInstance)
          .map(PsiClassType.class::cast)
          .map(PsiClassType::resolve)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());

        if (!providers.isEmpty()) {
          List<PsiExtensionMethod> extensionMethods = collectExtensionMethods(providers, ((PsiMethodCallExpression)place), targetClass);
          extensionMethods
            .stream()
            .map(method -> MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY))
            .distinct()
            .filter(methodSignature -> !targetClass.getVisibleSignatures().contains(methodSignature))
            .forEach(methodSignature -> result.add((PsiExtensionMethod)methodSignature.getMethod()));
        }
      }
      context = PsiTreeUtil.getContextOfType(context, PsiClass.class);
    }
    return result;
  }

  private static List<PsiExtensionMethod> collectExtensionMethods(final Set<PsiClass> providers,
                                                         final PsiMethodCallExpression callExpression,
                                                         final PsiClass targetClass) {
    List<PsiExtensionMethod> psiMethods = new ArrayList<>();
    providers.forEach(providerClass -> providerData(providerClass).forEach(function -> ContainerUtil.addIfNotNull(psiMethods, function.apply(targetClass, callExpression))));
    return psiMethods;
  }

  public static List<BiFunction<PsiClass, PsiMethodCallExpression, PsiExtensionMethod>> providerData(final PsiClass providerClass) {
    return CachedValuesManager.getCachedValue(providerClass, () -> CachedValueProvider.Result
      .create(createProviderCandidates(providerClass), PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static List<BiFunction<PsiClass, PsiMethodCallExpression, PsiExtensionMethod>> createProviderCandidates(final PsiClass providerClass) {
    final List<BiFunction<PsiClass, PsiMethodCallExpression, PsiExtensionMethod>> result = new ArrayList<>();
    for (PsiMethod providerStaticMethod : PsiClassUtil.collectClassStaticMethodsIntern(providerClass)) {
      if (providerStaticMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
        PsiParameter @NotNull [] parameters = providerStaticMethod.getParameterList().getParameters();
        if (parameters.length > 0 && !(parameters[0].getType() instanceof PsiPrimitiveType)) {
          result.add((targetClass, call) -> createLightMethodBySignature(providerStaticMethod, targetClass, call));
        }
      }
    }
    return result;
  }

  private static PsiExtensionMethod createLightMethodBySignature(PsiMethod staticMethod,
                                                                       PsiClass targetClass,
                                                                       PsiMethodCallExpression callExpression) {
    if (!staticMethod.getName().equals(callExpression.getMethodExpression().getReferenceName())) return null;
    PsiClass providerClass = Objects.requireNonNull(staticMethod.getContainingClass());
    PsiMethodCallExpression staticMethodCall;
    try {
      StringBuilder args = new StringBuilder(Objects.requireNonNull(callExpression.getMethodExpression().getQualifierExpression()).getText());
      PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
      if (expressions.length > 0) {
        args.append(", ");
      }
      args.append(StringUtil.join(expressions, expression -> expression.getText(), ","));

      staticMethodCall = (PsiMethodCallExpression)JavaPsiFacade.getElementFactory(staticMethod.getProject())
        .createExpressionFromText(providerClass.getQualifiedName() + "." + staticMethod.getName() + "(" + args + ")", callExpression);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return null;
    }

    JavaResolveResult result = staticMethodCall.resolveMethodGenerics();
    if (!(result instanceof MethodCandidateInfo)) return null;
    PsiMethod method = ((MethodCandidateInfo)result).getElement();
    if (!method.equals(staticMethod) || !((MethodCandidateInfo)result).isApplicable()) return null;
    PsiSubstitutor substitutor = result.getSubstitutor();

    final LombokExtensionMethod lightMethod = new LombokExtensionMethod(staticMethod);
    lightMethod
      .addModifiers(PsiModifier.PUBLIC);
    PsiParameter @NotNull [] parameters = staticMethod.getParameterList().getParameters();

    if (targetClass.isInterface()) {
      lightMethod.addModifier(PsiModifier.DEFAULT);
    }

    lightMethod.setMethodReturnType(substitutor.substitute(staticMethod.getReturnType()));

    for (int i = 1, length = parameters.length; i < length; i++) {
      PsiParameter parameter = parameters[i];
      lightMethod.addParameter(new LombokLightParameter(parameter.getName(), substitutor.substitute(parameter.getType()), lightMethod, JavaLanguage.INSTANCE));
    }

    PsiClassType[] thrownTypes = staticMethod.getThrowsList().getReferencedTypes();
    for (PsiClassType thrownType : thrownTypes) {
      lightMethod.addException((PsiClassType)substitutor.substitute(thrownType));
    }

    PsiTypeParameter[] staticMethodTypeParameters = staticMethod.getTypeParameters();
    HashSet<PsiTypeParameter> initialTypeParameters = ContainerUtil.newHashSet(staticMethodTypeParameters);
    Arrays.stream(staticMethodTypeParameters)
      .filter(typeParameter -> PsiTypesUtil.mentionsTypeParameters(substitutor.substitute(typeParameter), initialTypeParameters))
      .forEach(lightMethod::addTypeParameter);

    lightMethod.setNavigationElement(staticMethod);
    lightMethod.setContainingClass(targetClass);
    return lightMethod;
  }

  private static class LombokExtensionMethod extends LombokLightMethodBuilder implements PsiExtensionMethod {
    private final @NotNull PsiMethod myStaticMethod;

    LombokExtensionMethod(@NotNull PsiMethod staticMethod) {
      super(staticMethod.getManager(), staticMethod.getName());
      myStaticMethod = staticMethod;
    }

    @Override
    public boolean isEquivalentTo(final PsiElement another) { return myStaticMethod.isEquivalentTo(another); }

    @Override
    public @NotNull PsiMethod getTargetMethod() {
      return myStaticMethod;
    }

    @Override
    public @Nullable PsiParameter getTargetReceiverParameter() {
      return myStaticMethod.getParameterList().getParameter(0);
    }

    @Override
    public @Nullable PsiParameter getTargetParameter(int index) {
      return myStaticMethod.getParameterList().getParameter(index + 1);
    }
  }
}
