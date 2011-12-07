package de.plushnikov.intellij.lombok.psi;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.lombok.util.IntelliJVersionRangeUtil;

/**
 * @author Plushnikov Michail
 */
public abstract class LombokPsiElementFactory {
  private LombokPsiElementFactory() {
  }

  private static LombokPsiElementFactory ourInstance;

  public static LombokPsiElementFactory getInstance() {
    if (null == ourInstance) {
      initOurInstance();
    }
    return ourInstance;
  }

  private static void initOurInstance() {
    final BuildNumber buildNumber = ApplicationInfo.getInstance().getBuild();

    switch (IntelliJVersionRangeUtil.getIntelliJVersion(buildNumber)) {
      case INTELLIJ_8:
        throw new RuntimeException(String.format("This version (%s) of IntelliJ is not supported!", buildNumber.asString()));
      case INTELLIJ_9:
        ourInstance = new LombokPsiElementFactory9();
        break;
      case INTELLIJ_10:
      case INTELLIJ_10_5:
        ourInstance = new LombokPsiElementFactory10();
        break;
      case INTELLIJ_11:
      default:
        ourInstance = new LombokPsiElementFactory11();
        break;
    }
  }

  public abstract LombokLightFieldBuilder createLightField(@NotNull PsiManager manager, @NotNull String fieldName, @NotNull PsiType fieldType);

  public abstract LombokLightMethodBuilder createLightMethod(@NotNull PsiManager manager, @NotNull String methodName);

  public abstract LombokLightMethod createLightMethod(PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass);

  static class LombokPsiElementFactory9 extends LombokPsiElementFactory {
    public LombokLightFieldBuilder createLightField(@NotNull PsiManager manager, @NotNull String fieldName, @NotNull PsiType fieldType) {
      return new LombokLightFieldBuilder9Impl(manager, fieldName, fieldType);
    }

    public LombokLightMethodBuilder createLightMethod(@NotNull PsiManager manager, @NotNull String methodName) {
      return new LombokLightMethodBuilder9Impl(manager, methodName);
    }

    public LombokLightMethod createLightMethod(PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass) {
      return new LombokLightMethod9Impl(manager, valuesMethod, psiClass);
    }
  }

  static class LombokPsiElementFactory10 extends LombokPsiElementFactory {
    public LombokLightFieldBuilder createLightField(@NotNull PsiManager manager, @NotNull String fieldName, @NotNull PsiType fieldType) {
      return new LombokLightFieldBuilder10Impl(manager, fieldName, fieldType);
    }

    public LombokLightMethodBuilder createLightMethod(@NotNull PsiManager manager, @NotNull String methodName) {
      return new LombokLightMethodBuilder10Impl(manager, methodName);
    }

    public LombokLightMethod createLightMethod(PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass) {
      return new LombokLightMethod10Impl(manager, valuesMethod, psiClass);
    }
  }

  static class LombokPsiElementFactory11 extends LombokPsiElementFactory {
    public LombokLightFieldBuilder createLightField(@NotNull PsiManager manager, @NotNull String fieldName, @NotNull PsiType fieldType) {
      return new LombokLightFieldBuilder11Impl(manager, fieldName, fieldType);
    }

    public LombokLightMethodBuilder createLightMethod(@NotNull PsiManager manager, @NotNull String methodName) {
      return new LombokLightMethodBuilder11Impl(manager, methodName);
    }

    public LombokLightMethod createLightMethod(PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass) {
      return new LombokLightMethod11Impl(manager, valuesMethod, psiClass);
    }
  }
}
