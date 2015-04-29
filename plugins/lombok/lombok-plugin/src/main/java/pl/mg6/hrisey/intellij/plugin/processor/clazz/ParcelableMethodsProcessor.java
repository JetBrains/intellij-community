package pl.mg6.hrisey.intellij.plugin.processor.clazz;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import hrisey.Parcelable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ParcelableMethodsProcessor extends AbstractClassProcessor {

  public ParcelableMethodsProcessor() {
    super(Parcelable.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return true;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    target.add(generateDecribeContents(psiClass, psiAnnotation));
    target.add(generateWriteToParcel(psiClass, psiAnnotation));
    target.add(generateParcelConstructor(psiClass, psiAnnotation));
  }

  private PsiElement generateDecribeContents(PsiClass psiClass, PsiAnnotation psiAnnotation) {
    return new LombokLightMethodBuilder(psiClass.getManager(), "describeContents")
        .withModifier(PsiModifier.PUBLIC)
        .withMethodReturnType(PsiType.INT)
        .withBody(PsiMethodUtil.createCodeBlockFromText("return 0;", psiClass))
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation);
  }

  private PsiElement generateWriteToParcel(PsiClass psiClass, PsiAnnotation psiAnnotation) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    PsiClassType classType = elementFactory.createTypeByFQClassName("android.os.Parcel");
    return new LombokLightMethodBuilder(psiClass.getManager(), "writeToParcel")
        .withModifier(PsiModifier.PUBLIC)
        .withMethodReturnType(PsiType.VOID)
        .withParameter("dest", classType)
        .withParameter("flags", PsiType.INT)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation);
  }

  private PsiElement generateParcelConstructor(PsiClass psiClass, PsiAnnotation psiAnnotation) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      if (!psiClass.hasModifierProperty("final")) {
        builder.append("protected ");
      }
      builder.append(psiClass.getName());
      builder.append("(android.os.Parcel source) {\n}");

      return PsiMethodUtil.createMethod(psiClass, builder.toString(), psiAnnotation);
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
}
