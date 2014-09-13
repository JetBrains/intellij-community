package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightParameterListBuilder;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public class LombokLightMethodBuilder extends LightMethodBuilder {
  private final LightIdentifier myNameIdentifier;
  private final LombokLightReferenceListBuilder myThrowsList;
  private ASTNode myASTNode;
  private String myName;
  private PsiCodeBlock myBodyCodeBlock;

  public LombokLightMethodBuilder(@NotNull PsiManager manager, @NotNull String name) {
    super(manager, JavaLanguage.INSTANCE, name,
        new LightParameterListBuilder(manager, JavaLanguage.INSTANCE), new LombokLightModifierList(manager, JavaLanguage.INSTANCE));
    myName = name;
    myNameIdentifier = new LombokLightIdentifier(manager, name);
    myThrowsList = new LombokLightReferenceListBuilder(manager, JavaLanguage.INSTANCE, PsiReferenceList.Role.THROWS_LIST);
  }

  public LombokLightMethodBuilder withNavigationElement(PsiElement navigationElement) {
    setNavigationElement(navigationElement);
    return this;
  }

  public LombokLightMethodBuilder withModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
    addModifier(modifier);
    return this;
  }

  public LombokLightMethodBuilder withMethodReturnType(PsiType returnType) {
    setMethodReturnType(returnType);
    return this;
  }

  public LombokLightMethodBuilder withParameter(@NotNull String name, @NotNull PsiType type) {
    return withParameter(new LombokLightParameter(name, type, this, JavaLanguage.INSTANCE));
  }

  public LombokLightMethodBuilder withParameter(@NotNull PsiParameter psiParameter) {
    addParameter(psiParameter);
    return this;
  }

  public LightMethodBuilder addException(PsiClassType type) {
    myThrowsList.addReference(type);
    return this;
  }

  public LightMethodBuilder addException(String fqName) {
    myThrowsList.addReference(fqName);
    return this;
  }

  @Override
  @NotNull
  public PsiReferenceList getThrowsList() {
    return myThrowsList;
  }

  public LombokLightMethodBuilder withException(@NotNull PsiClassType type) {
    addException(type);
    return this;
  }

  public LombokLightMethodBuilder withException(@NotNull String fqName) {
    addException(fqName);
    return this;
  }

  public LombokLightMethodBuilder withContainingClass(@NotNull PsiClass containingClass) {
    setContainingClass(containingClass);
    return this;
  }

  public LombokLightMethodBuilder withTypeParameter(@NotNull PsiTypeParameter typeParameter) {
    addTypeParameter(typeParameter);
    return this;
  }

  public LombokLightMethodBuilder withConstructor(boolean isConstructor) {
    setConstructor(isConstructor);
    return this;
  }

  public LombokLightMethodBuilder withBody(@NotNull PsiCodeBlock codeBlock) {
    myBodyCodeBlock = codeBlock;
    return this;
  }

  @Override
  public PsiCodeBlock getBody() {
    return myBodyCodeBlock;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  @Override
  public PsiElement getParent() {
    PsiElement result = super.getParent();
    result = null != result ? result : getContainingClass();
    return result;
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    PsiClass containingClass = getContainingClass();
    return containingClass != null ? containingClass.getContainingFile() : null;
  }

  @Override
  public String getText() {
    ASTNode node = getNode();
    if (null != node) {
      return node.getText();
    }
    return "";
  }

  @Override
  public ASTNode getNode() {
    if (null == myASTNode) {
      myASTNode = rebuildMethodFromString().getNode();
    }
    return myASTNode;
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  private PsiMethod rebuildMethodFromString() {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(getAllModifierProperties((LightModifierList) getModifierList()));
      PsiType returnType = getReturnType();
      if (null != returnType) {
        builder.append(returnType.getCanonicalText()).append(' ');
      }
      builder.append(getName());
      builder.append('(');
      if (getParameterList().getParametersCount() > 0) {
        for (PsiParameter parameter : getParameterList().getParameters()) {
          builder.append(parameter.getType().getCanonicalText()).append(' ').append(parameter.getName()).append(',');
        }
        builder.deleteCharAt(builder.length() - 1);
      }
      builder.append(')');
      builder.append('{').append("  ").append('}');

      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getManager().getProject()).getElementFactory();
      return elementFactory.createMethodFromText(builder.toString(), getContainingClass());
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public String getAllModifierProperties(LightModifierList modifierList) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      for (String modifier : modifierList.getModifiers()) {
        if (!PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
          builder.append(modifier).append(' ');
        }
      }
      return builder.toString();
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public PsiElement copy() {
    return rebuildMethodFromString();
  }

  public String toString() {
    return "LombokLightMethodBuilder: " + getName();
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    // just add new element to the containing class
    final PsiClass containingClass = getContainingClass();
    if (null != containingClass) {
      CheckUtil.checkWritable(containingClass);
      return containingClass.add(newElement);
    }
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    myName = name;
    return this;
  }

  @Override
  public void delete() throws IncorrectOperationException {
    // simple do nothing
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    // simple do nothing
  }
}
