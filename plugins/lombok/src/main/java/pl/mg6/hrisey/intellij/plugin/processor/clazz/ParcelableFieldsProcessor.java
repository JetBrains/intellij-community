package pl.mg6.hrisey.intellij.plugin.processor.clazz;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import hrisey.Parcelable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ParcelableFieldsProcessor extends AbstractClassProcessor {

  public ParcelableFieldsProcessor() {
    super(PsiField.class, Parcelable.class);
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
    target.add(generateCreator(psiClass, psiAnnotation));
  }

  private PsiElement generateCreator(PsiClass psiClass, PsiAnnotation psiAnnotation) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    PsiType creatorType = elementFactory.createTypeFromText("android.os.Parcelable.Creator<" + psiClass.getName() + ">", psiClass);
    return new LombokLightFieldBuilder(psiClass.getManager(), "CREATOR", creatorType)
        .withModifier(PsiModifier.PUBLIC)
        .withModifier(PsiModifier.STATIC)
        .withModifier(PsiModifier.FINAL)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation);
  }
}
