package pl.mg6.hrisey.intellij.plugin.processor.clazz;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import hrisey.Parcelable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ParcelableMethodsProcessor extends AbstractClassProcessor {

  public ParcelableMethodsProcessor() {
    super(PsiMethod.class, Parcelable.class);
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_THIRD_PARTY_ENABLED);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return true;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    target.add(generateDescribeContents(psiClass, psiAnnotation));
    target.add(generateWriteToParcel(psiClass, psiAnnotation));
    target.add(generateParcelConstructor(psiClass, psiAnnotation));
  }

  private PsiElement generateDescribeContents(PsiClass psiClass, PsiAnnotation psiAnnotation) {
    return new LombokLightMethodBuilder(psiClass.getManager(), "describeContents")
        .withModifier(PsiModifier.PUBLIC)
        .withMethodReturnType(PsiType.INT)
        .withBody(PsiMethodUtil.createCodeBlockFromText("return 0;", psiClass))
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation);
  }

  private PsiElement generateWriteToParcel(PsiClass psiClass, PsiAnnotation psiAnnotation) {
    PsiClassType classType = getParcelClassType(psiClass);
    return new LombokLightMethodBuilder(psiClass.getManager(), "writeToParcel")
        .withModifier(PsiModifier.PUBLIC)
        .withMethodReturnType(PsiType.VOID)
        .withParameter("dest", classType)
        .withParameter("flags", PsiType.INT)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation);
  }

  private PsiElement generateParcelConstructor(PsiClass psiClass, PsiAnnotation psiAnnotation) {
    String modifier = PsiModifier.PACKAGE_LOCAL;
    if (!psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      modifier = PsiModifier.PROTECTED;
    }

    return new LombokLightMethodBuilder(psiClass.getManager(), psiClass.getName())
        .withConstructor(true)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(modifier)
        .withParameter("source", getParcelClassType(psiClass));
  }

  @NotNull
  private PsiClassType getParcelClassType(@NotNull PsiClass psiClass) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    return elementFactory.createTypeByFQClassName("android.os.Parcel");
  }
}
