package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.Language;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class LombokLightParameterListBuilder extends LightElement implements PsiParameterList {
  private final List<PsiParameter> myParameters = new ArrayList<PsiParameter>();
  private PsiParameter[] myCachedParameters;
  private final PsiMethod myParentMethod;

  public LombokLightParameterListBuilder(@NotNull PsiManager manager, @NotNull Language language, @NotNull PsiMethod parent) {
    super(manager, language);
    myParentMethod = parent;
  }

  public void addParameter(PsiParameter parameter) {
    myParameters.add(parameter);
    myCachedParameters = null;
  }

  @NotNull
  @Override
  public PsiParameter[] getParameters() {
    if (myCachedParameters == null) {
      if (myParameters.isEmpty()) {
        myCachedParameters = PsiParameter.EMPTY_ARRAY;
      } else {
        myCachedParameters = myParameters.toArray(new PsiParameter[myParameters.size()]);
      }
    }
    return myCachedParameters;
  }

  @Override
  public int getParameterIndex(PsiParameter parameter) {
    return myParameters.indexOf(parameter);
  }

  @Override
  public int getParametersCount() {
    return myParameters.size();
  }

  @Override
  public PsiElement getParent() {
    return myParentMethod;
  }

  @Override
  public PsiFile getContainingFile() {
    return myParentMethod.getContainingFile();
  }

  @Override
  public String toString() {
    return "Lombok LightParameterList";
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitParameterList(this);
    }
  }

}

