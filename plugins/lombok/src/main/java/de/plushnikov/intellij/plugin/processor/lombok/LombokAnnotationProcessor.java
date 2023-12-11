package de.plushnikov.intellij.plugin.processor.lombok;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.psi.LombokLightAnnotationMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightArrayInitializerMemberValue;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static de.plushnikov.intellij.plugin.LombokClassNames.*;

public final class LombokAnnotationProcessor {
  private static final Map<String, Pair<Collection<String>, Collection<String>>> config = initializeConfig();

  private static Map<String, Pair<Collection<String>, Collection<String>>> initializeConfig() {
    return Map.of(
      "onParam_", createAnnotationsPair(SETTER, EQUALS_AND_HASHCODE, WITH, WITHER),
      "onMethod_", createAnnotationsPair(GETTER, SETTER, WITH, WITHER),
      "onConstructor_", createAnnotationsPair(ALL_ARGS_CONSTRUCTOR, REQUIRED_ARGS_CONSTRUCTOR, NO_ARGS_CONSTRUCTOR)
    );
  }

  private static Pair<Collection<String>, Collection<String>> createAnnotationsPair(String... fqns) {
    Collection<String> fqnList = Arrays.asList(fqns);
    Collection<String> shortNamesList = ContainerUtil.map(fqnList, StringUtil::getShortName);
    return Pair.pair(shortNamesList, fqnList);
  }

  /**
   * Processes the given PsiClass if it's a specific lombok annotation with onX functionality
   * and returns a list of phantom underscored "onX_" methods
   *
   * @param psiClass the PsiClass to process
   * @param nameHint the name hint to filter the results (optional)
   * @return the list of augmented PsiMethods or empty list
   * @see <a href="https://projectlombok.org/features/experimental/onX">Lombok onX</a>
   */
  @NotNull
  public static List<PsiMethod> process(@NotNull PsiClass psiClass, @Nullable String nameHint) {
    if (nameHint != null && !config.containsKey(nameHint)) {
      return Collections.emptyList();
    }

    List<PsiMethod> result = new ArrayList<>();

    if (nameHint != null) {
      final Pair<Collection<String>, Collection<String>> hintConfig = config.get(nameHint);
      addAnnotationMethods(psiClass, hintConfig.getFirst(), hintConfig.getSecond(), nameHint, result);
    }
    else {
      for (Map.Entry<String, Pair<Collection<String>, Collection<String>>> entry : config.entrySet()) {
        addAnnotationMethods(psiClass, entry.getValue().getFirst(), entry.getValue().getSecond(), entry.getKey(), result);
      }
    }

    return result;
  }

  private static void addAnnotationMethods(@NotNull PsiClass psiClass,
                                           @NotNull Collection<String> onXAnnotationNames,
                                           @NotNull Collection<String> onXAnnotationFQNs,
                                           @NotNull String methodName,
                                           @NotNull List<PsiMethod> result) {
    if (onXAnnotationNames.contains(psiClass.getName()) && onXAnnotationFQNs.contains(psiClass.getQualifiedName())) {
      result.add(createAnnotationMethod(psiClass, methodName));
    }
  }

  private static LombokLightMethodBuilder createAnnotationMethod(@NotNull PsiClass psiClass, @NotNull String methodName) {
    final PsiType myAnnotationType =
      PsiType.getJavaLangObject(psiClass.getManager(), GlobalSearchScope.projectScope(psiClass.getProject()));
    return new LombokLightAnnotationMethodBuilder(psiClass.getManager(), methodName)
      .withDefaultValue(new LombokLightArrayInitializerMemberValue(psiClass.getManager(), psiClass.getLanguage()))
      .withContainingClass(psiClass)
      .withMethodReturnType(new PsiArrayType(myAnnotationType));
  }
}