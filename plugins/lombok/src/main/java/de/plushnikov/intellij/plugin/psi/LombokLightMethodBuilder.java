package de.plushnikov.intellij.plugin.psi;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import de.plushnikov.intellij.plugin.extension.LombokInferredAnnotationProvider;
import icons.LombokIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * @author Plushnikov Michail
 */
public class LombokLightMethodBuilder extends LightMethodBuilder implements SyntheticElement {
  private PsiMethod myMethod;
  private ASTNode myASTNode;
  private String myBodyAsText;
  private PsiCodeBlock myBodyCodeBlock;
  // used to simplify comparing of returnType in equal method
  private String myReturnTypeAsText;
  private Function<LombokLightMethodBuilder, String> myBuilderBodyFunction;

  private boolean myReadWriteAccess = false;

  public LombokLightMethodBuilder(@NotNull PsiManager manager, @NotNull String name) {
    super(manager, JavaLanguage.INSTANCE, name,
          new LombokLightParameterListBuilder(manager, JavaLanguage.INSTANCE),
          new LombokLightModifierList(manager),
          new LombokLightReferenceListBuilder(manager, JavaLanguage.INSTANCE, PsiReferenceList.Role.THROWS_LIST),
          new LightTypeParameterListBuilder(manager, JavaLanguage.INSTANCE));
    getModifierList().withParent(this);
    setBaseIcon(LombokIcons.Nodes.LombokMethod);
  }

  public LombokLightMethodBuilder withNavigationElement(PsiElement navigationElement) {
    setNavigationElement(navigationElement);
    return this;
  }

  public LombokLightMethodBuilder withModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
    addModifier(modifier);
    return this;
  }

  public LombokLightMethodBuilder withModifier(@PsiModifier.ModifierConstant @NonNls String @NotNull ... modifiers) {
    for (String modifier : modifiers) {
      addModifier(modifier);
    }
    return this;
  }

  public LombokLightMethodBuilder withMethodReturnType(PsiType returnType) {
    setMethodReturnType(returnType);
    return this;
  }

  @Override
  public LightMethodBuilder setMethodReturnType(PsiType returnType) {
    myReturnTypeAsText = returnType.getPresentableText();
    return super.setMethodReturnType(returnType);
  }

  public LombokLightMethodBuilder withFinalParameter(@NotNull String name, @NotNull PsiType type) {
    final LombokLightParameter lombokLightParameter = createParameter(name, type);
    lombokLightParameter.setModifiers(PsiModifier.FINAL);
    return withParameter(lombokLightParameter);
  }

  public LombokLightMethodBuilder withParameter(@NotNull String name, @NotNull PsiType type) {
    return withParameter(createParameter(name, type));
  }

  private @NotNull LombokLightParameter createParameter(@NotNull String name, @NotNull PsiType type) {
    return new LombokLightParameter(name, type, this, JavaLanguage.INSTANCE);
  }

  public LombokLightMethodBuilder withParameter(@NotNull LombokLightParameter psiParameter) {
    addParameter(psiParameter);
    psiParameter.setParent(this);
    return this;
  }

  public LombokLightMethodBuilder withException(@NotNull PsiClassType type) {
    addException(type);
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

  public LombokLightMethodBuilder withBodyText(@NotNull String codeBlockText) {
    myBodyAsText = codeBlockText;
    myBodyCodeBlock = null;
    return this;
  }

  public LombokLightMethodBuilder withBodyText(@NotNull Function<LombokLightMethodBuilder, String> builderStringFunction) {
    myBuilderBodyFunction = builderStringFunction;
    myBodyCodeBlock = null;
    return this;
  }

  public LombokLightMethodBuilder withPureContract() {
    Project project = myManager.getProject();
    PsiAnnotation annotation = CachedValuesManager.getManager(project).getCachedValue(project, () ->
      CachedValueProvider.Result.create(JavaPsiFacade.getElementFactory(project).
          createAnnotationFromText('@' + JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT + "(pure=true)", null),
        ModificationTracker.NEVER_CHANGED));
    putUserData(LombokInferredAnnotationProvider.CONTRACT_ANNOTATION, annotation);
    return this;
  }

  public LombokLightMethodBuilder withMutatesThisContract() {
    Project project = myManager.getProject();
    PsiAnnotation annotation = CachedValuesManager.getManager(project).getCachedValue(project, () ->
      CachedValueProvider.Result.create(JavaPsiFacade.getElementFactory(project).
          createAnnotationFromText('@' + JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT + "(mutates=\"this\")", null),
        ModificationTracker.NEVER_CHANGED));
    putUserData(LombokInferredAnnotationProvider.CONTRACT_ANNOTATION, annotation);
    return this;
  }

  public LombokLightMethodBuilder withAnnotation(@NotNull String annotation) {
    getModifierList().addAnnotation(annotation);
    return this;
  }

  public LombokLightMethodBuilder withAnnotations(Iterable<String> annotations) {
    final PsiModifierList modifierList = getModifierList();
    annotations.forEach(modifierList::addAnnotation);
    return this;
  }

  public LombokLightMethodBuilder withWriteAccess() {
    myReadWriteAccess = true;
    return this;
  }

  public boolean hasWriteAccess() {
    return myReadWriteAccess;
  }

  @Override
  public @NotNull LombokLightModifierList getModifierList() {
    return (LombokLightModifierList)super.getModifierList();
  }

  @Override
  public @NotNull LombokLightParameterListBuilder getParameterList() {
    return (LombokLightParameterListBuilder)super.getParameterList();
  }

  @Override
  public PsiCodeBlock getBody() {
    String bodyAsText = myBodyAsText;
    Function<LombokLightMethodBuilder, String> builderBodyFunction = myBuilderBodyFunction;
    if (null == myBodyCodeBlock && (bodyAsText != null || builderBodyFunction != null)) {
      if (bodyAsText == null) {
        DumbService dumbService = DumbService.getInstance(getProject());
        if (dumbService.isDumb() && !dumbService.isAlternativeResolveEnabled()) {
          bodyAsText = dumbService.computeWithAlternativeResolveEnabled(
            () -> builderBodyFunction.apply(this)
          );
        }
        else {
          bodyAsText = builderBodyFunction.apply(this);
        }
      }
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getProject());
      myBodyCodeBlock = elementFactory.createCodeBlockFromText("{" + bodyAsText + "}", this);
      myBodyAsText = null;
      myBuilderBodyFunction = null;
    }
    return myBodyCodeBlock;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return new LombokLightIdentifier(myManager, getName());
  }

  @Override
  public PsiElement getParent() {
    PsiElement result = super.getParent();
    result = null != result ? result : getContainingClass();
    return result;
  }

  @Override
  public @Nullable PsiFile getContainingFile() {
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
      final PsiElement myPsiMethod = getOrCreateMyPsiMethod();
      myASTNode = null == myPsiMethod ? null : myPsiMethod.getNode();
    }
    return myASTNode;
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  private static String getAllModifierProperties(LightModifierList modifierList) {
    final StringBuilder builder = new StringBuilder();
    for (String modifier : modifierList.getModifiers()) {
      if (!PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
        builder.append(modifier).append(' ');
      }
    }
    return builder.toString();
  }

  private PsiMethod rebuildMethodFromString() {
    PsiMethod result;
    try {
      final StringBuilder methodTextDeclaration = new StringBuilder();
      methodTextDeclaration.append(getAllModifierProperties(getModifierList()));
      PsiType returnType = getReturnType();
      if (null != returnType && returnType.isValid()) {
        methodTextDeclaration.append(returnType.getCanonicalText()).append(' ');
      }
      methodTextDeclaration.append(getName());
      methodTextDeclaration.append('(');
      if (getParameterList().getParametersCount() > 0) {
        for (PsiParameter parameter : getParameterList().getParameters()) {
          methodTextDeclaration.append(parameter.getType().getCanonicalText()).append(' ').append(parameter.getName()).append(',');
        }
        methodTextDeclaration.deleteCharAt(methodTextDeclaration.length() - 1);
      }
      methodTextDeclaration.append(')');
      methodTextDeclaration.append('{').append("  ").append('}');

      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getManager().getProject());

      result = elementFactory.createMethodFromText(methodTextDeclaration.toString(), getContainingClass());
      if (null != getBody()) {
        result.getBody().replace(getBody());
      }
    }
    catch (Exception ex) {
      result = null;
    }
    return result;
  }

  @Override
  public PsiElement copy() {
    final PsiElement myPsiMethod = getOrCreateMyPsiMethod();
    return null == myPsiMethod ? null : myPsiMethod.copy();
  }

  private PsiElement getOrCreateMyPsiMethod() {
    if (null == myMethod) {
      myMethod = rebuildMethodFromString();
    }
    return myMethod;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    final PsiElement myPsiMethod = getOrCreateMyPsiMethod();
    return null == myPsiMethod ? PsiElement.EMPTY_ARRAY : myPsiMethod.getChildren();
  }

  @Override
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

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    //just do nothing here
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LombokLightMethodBuilder that = (LombokLightMethodBuilder)o;

    if (!getName().equals(that.getName())) {
      return false;
    }
    if (isConstructor() != that.isConstructor()) {
      return false;
    }
    final PsiClass containingClass = getContainingClass();
    final PsiClass thatContainingClass = that.getContainingClass();
    if (!Objects.equals(containingClass, thatContainingClass)) {
      return false;
    }
    if (!getModifierList().equals(that.getModifierList())) {
      return false;
    }
    if (!getParameterList().equals(that.getParameterList())) {
      return false;
    }

    return Objects.equals(myReturnTypeAsText, that.myReturnTypeAsText);
  }

  @Override
  public int hashCode() {
    // should be constant because of RenameJavaMethodProcessor#renameElement and fixNameCollisionsWithInnerClassMethod(...)
    return 1;
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
