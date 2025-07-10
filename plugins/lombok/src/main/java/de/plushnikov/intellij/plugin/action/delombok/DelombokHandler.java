package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import de.plushnikov.intellij.plugin.processor.AbstractProcessor;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsPredefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.provider.LombokUserDataKeys;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DelombokHandler {
  private final boolean processInnerClasses;
  private final Collection<AbstractProcessor> lombokProcessors;

  protected DelombokHandler(AbstractProcessor... lombokProcessors) {
    this(false, lombokProcessors);
  }

  protected DelombokHandler(boolean processInnerClasses, AbstractProcessor... lombokProcessors) {
    this.processInnerClasses = processInnerClasses;
    this.lombokProcessors = Arrays.asList(lombokProcessors);
  }

  public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiClass psiClass) {
    if (psiFile.isWritable()) {
      invoke(project, psiClass, processInnerClasses);
      finish(project, psiFile);
    }
  }

  public void invoke(@NotNull Project project, @NotNull PsiJavaFile psiFile) {
    if (psiFile.isWritable()) {
      for (PsiClass psiClass : psiFile.getClasses()) {
        invoke(project, psiClass, true);
      }
      finish(project, psiFile);
    }
  }

  private void invoke(Project project, PsiClass psiClass, boolean processInnerClasses) {
    Collection<PsiAnnotation> processedAnnotations = new HashSet<>();

    processModifierList(psiClass);

    // get all inner classes before first lombok processing
    final PsiClass[] allInnerClasses = psiClass.getAllInnerClasses();

    for (AbstractProcessor lombokProcessor : lombokProcessors) {
      processedAnnotations.addAll(processClass(project, psiClass, lombokProcessor));
    }

    postProcessAugmentedAnnotations(psiClass);

    CodeStyleManager.getInstance(project).reformat(psiClass);

    if (processInnerClasses) {
      for (PsiClass innerClass : allInnerClasses) {
        //skip our self generated classes
        if (!(innerClass instanceof LombokLightClassBuilder) && innerClass.isWritable()) {
          invoke(project, innerClass, true);
        }
      }
    }

    processedAnnotations.forEach(PsiAnnotation::delete);
  }

  private static void finish(Project project, PsiFile psiFile) {
    JavaCodeStyleManager.getInstance(project).optimizeImports(psiFile);
    UndoUtil.markPsiFileForUndo(psiFile);
  }

  private static Collection<PsiAnnotation> processClass(@NotNull Project project,
                                                        @NotNull PsiClass psiClass,
                                                        @NotNull AbstractProcessor lombokProcessor) {
    Collection<PsiAnnotation> psiAnnotations = lombokProcessor.collectProcessedAnnotations(psiClass);

    final List<? super PsiElement> psiElements = lombokProcessor.process(psiClass);

    if (lombokProcessor instanceof FieldNameConstantsPredefinedInnerClassFieldProcessor) {
      rebuildElementsBeforeExistingFields(project, psiClass, psiElements);
    }
    else {
      rebuildElements(project, psiClass, psiElements);
    }

    return psiAnnotations;
  }

  private static void processModifierList(@NotNull PsiClass psiClass) {
    rebuildModifierList(psiClass);
    PsiClassUtil.collectClassFieldsIntern(psiClass).forEach(DelombokHandler::rebuildModifierList);
    PsiClassUtil.collectClassMethodsIntern(psiClass).forEach(DelombokHandler::rebuildModifierList);
    PsiClassUtil.collectClassStaticMethodsIntern(psiClass).forEach(DelombokHandler::rebuildModifierList);
  }

  private static void rebuildModifierList(@NotNull PsiModifierListOwner modifierListOwner) {
    final PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (null != modifierList) {
      final Set<String> lombokModifiers = new HashSet<>();
      LombokProcessorManager.getLombokModifierProcessors().forEach(modifierProcessor -> {
        if (modifierProcessor.isSupported(modifierList)) {
          modifierProcessor.transformModifiers(modifierList, lombokModifiers);
          lombokModifiers.forEach(modifier -> modifierList.setModifierProperty(modifier, true));
          lombokModifiers.clear();
        }
      });
    }
  }

  private static void postProcessAugmentedAnnotations(@NotNull PsiModifierListOwner psiModifierListOwner) {
    final Collection<String> augmentedAnnotations = psiModifierListOwner.getUserData(LombokUserDataKeys.AUGMENTED_ANNOTATIONS);
    final PsiModifierList psiModifierList = psiModifierListOwner.getModifierList();

    if (null != augmentedAnnotations && !augmentedAnnotations.isEmpty() && psiModifierList != null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiModifierListOwner.getProject());
      for (String augmentedAnnotation : augmentedAnnotations) {
        psiModifierList.addAfter(elementFactory.createAnnotationFromText(augmentedAnnotation, psiModifierListOwner), null);
      }
    }
  }

  private static void rebuildElementsBeforeExistingFields(Project project, PsiClass psiClass, List<? super PsiElement> psiElements) {
    //add generated elements in generated/declaration order, but before existing Fields
    if (!psiElements.isEmpty()) {
      final PsiField existingField = PsiClassUtil.collectClassFieldsIntern(psiClass).stream().findFirst().orElse(null);
      Iterator<? super PsiElement> iterator = psiElements.iterator();
      PsiElement prev = psiClass.addBefore(rebuildPsiElement(project, (PsiElement)iterator.next()), existingField);
      while (iterator.hasNext()) {
        PsiElement curr = rebuildPsiElement(project, (PsiElement)iterator.next());
        if (curr != null) {
          prev = psiClass.addAfter(curr, prev);
        }
      }
    }
  }

  private static void rebuildElements(Project project, PsiClass psiClass, List<? super PsiElement> psiElements) {
    for (Object psiElement : psiElements) {
      final PsiElement element = rebuildPsiElement(project, (PsiElement)psiElement);
      if (null != element) {
        psiClass.add(element);
      }
    }
  }

  public Collection<PsiAnnotation> collectProcessableAnnotations(@NotNull PsiClass psiClass) {
    Collection<PsiAnnotation> result = new ArrayList<>();

    for (AbstractProcessor lombokProcessor : lombokProcessors) {
      result.addAll(lombokProcessor.collectProcessedAnnotations(psiClass));
    }

    return result;
  }

  private static PsiElement rebuildPsiElement(@NotNull Project project, PsiElement psiElement) {
    if (psiElement instanceof PsiMethod) {
      return rebuildMethod(project, (PsiMethod)psiElement);
    }
    else if (psiElement instanceof PsiField) {
      return rebuildField(project, (PsiField)psiElement);
    }
    else if (psiElement instanceof PsiClass) {
      if (((PsiClass)psiElement).isEnum()) {
        return rebuildEnum(project, (PsiClass)psiElement);
      }
      else {
        return rebuildClass(project, (PsiClass)psiElement);
      }
    }
    return null;
  }

  private static PsiClass rebuildEnum(@NotNull Project project, @NotNull PsiClass fromClass) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

    final PsiClass resultClass = elementFactory.createEnum(StringUtil.defaultIfEmpty(fromClass.getName(), "UnknownClassName"));
    copyModifiers(fromClass.getModifierList(), resultClass.getModifierList());
    rebuildTypeParameter(fromClass, resultClass);

    final List<PsiField> fields = Arrays.asList(fromClass.getFields());

    if (!fields.isEmpty()) {
      final Iterator<PsiField> iterator = fields.iterator();
      PsiElement prev = resultClass.add(rebuildField(project, iterator.next()));
      while (iterator.hasNext()) {
        PsiField curr = iterator.next();
        //guarantees order of enum constants, should match declaration order
        prev = resultClass.addAfter(rebuildField(project, curr), prev);
      }
    }

    for (PsiMethod psiMethod : fromClass.getMethods()) {
      final String psiMethodName = psiMethod.getName();
      //skip Enum virtual methods
      if ("values".equals(psiMethodName) || "valueOf".equals(psiMethodName)) {
        continue;
      }
      resultClass.add(rebuildMethod(project, psiMethod));
    }

    return resultClass;
  }

  private static PsiClass rebuildClass(@NotNull Project project, @NotNull PsiClass fromClass) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

    PsiClass resultClass = elementFactory.createClass(StringUtil.defaultIfEmpty(fromClass.getName(), "UnknownClassName"));
    copyModifiers(fromClass.getModifierList(), resultClass.getModifierList());
    copyAnnotations(fromClass.getModifierList(), resultClass.getModifierList());
    rebuildTypeParameter(fromClass, resultClass);

    // rebuild extends part
    final PsiReferenceList extendsList = fromClass.getExtendsList();
    final PsiReferenceList resultExtendsList = resultClass.getExtendsList();
    if (null != extendsList && null != resultExtendsList) {
      Stream.of(extendsList.getReferencedTypes()).map(elementFactory::createReferenceElementByType).forEach(resultExtendsList::add);
    }

    for (PsiField psiField : fromClass.getFields()) {
      resultClass.add(rebuildField(project, psiField));
    }
    for (PsiMethod psiMethod : fromClass.getMethods()) {
      resultClass.add(rebuildMethod(project, psiMethod));
    }

    return resultClass;
  }

  private static PsiMethod rebuildMethod(@NotNull Project project, @NotNull PsiMethod fromMethod) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

    final PsiMethod resultMethod;
    final PsiType returnType = fromMethod.getReturnType();
    if (null == returnType) {
      resultMethod = elementFactory.createConstructor(fromMethod.getName());
    }
    else {
      resultMethod = elementFactory.createMethod(fromMethod.getName(), returnType);
    }

    rebuildTypeParameter(fromMethod, resultMethod);

    final PsiClassType[] referencedTypes = fromMethod.getThrowsList().getReferencedTypes();
    if (referencedTypes.length > 0) {
      PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[referencedTypes.length];
      for (int i = 0; i < refs.length; i++) {
        refs[i] = elementFactory.createReferenceElementByType(referencedTypes[i]);
      }
      resultMethod.getThrowsList().replace(elementFactory.createReferenceList(refs));
    }

    for (PsiParameter fromParameter : fromMethod.getParameterList().getParameters()) {
      PsiParameter toParameter = elementFactory.createParameter(fromParameter.getName(), fromParameter.getType());
      final PsiModifierList fromParameterModifierList = fromParameter.getModifierList();
      if (fromParameterModifierList != null) {
        final PsiModifierList toParameterModifierList = toParameter.getModifierList();
        copyAnnotations(fromParameterModifierList, toParameterModifierList);
        toParameterModifierList.setModifierProperty(PsiModifier.FINAL, fromParameterModifierList.hasModifierProperty(PsiModifier.FINAL));
      }
      resultMethod.getParameterList().add(toParameter);
    }

    final PsiModifierList fromMethodModifierList = fromMethod.getModifierList();
    final PsiModifierList resultMethodModifierList = resultMethod.getModifierList();
    copyModifiers(fromMethodModifierList, resultMethodModifierList);
    copyAnnotations(fromMethodModifierList, resultMethodModifierList);

    PsiCodeBlock body = fromMethod.getBody();
    if (null != body) {
      resultMethod.getBody().replace(body);
    }
    else {
      resultMethod.getBody().delete();
    }

    return resultMethod;
  }

  private static void copyAnnotations(@NotNull PsiModifierList fromModifierList, @NotNull PsiModifierList toModifierList) {
    for (PsiAnnotation originalAnnotation : fromModifierList.getAnnotations()) {
      final String annotationQualifiedName = originalAnnotation.getQualifiedName();
      if (!StringUtil.isEmptyOrSpaces(annotationQualifiedName)) {
        AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(annotationQualifiedName,
                                                          originalAnnotation.getParameterList().getAttributes(),
                                                          toModifierList);
      }
    }
  }

  private static void rebuildTypeParameter(@NotNull PsiTypeParameterListOwner listOwner, @NotNull PsiTypeParameterListOwner resultOwner) {
    final PsiTypeParameterList resultOwnerTypeParameterList = resultOwner.getTypeParameterList();
    if (null != resultOwnerTypeParameterList) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(resultOwner.getProject());

      for (PsiTypeParameter typeParameter : listOwner.getTypeParameters()) {
        String typeParameterAsString = getTypeParameterAsString(typeParameter);
        resultOwnerTypeParameterList.add(elementFactory.createTypeParameterFromText(typeParameterAsString, resultOwner));
      }
    }
  }

  private static @NotNull String getTypeParameterAsString(@NotNull PsiTypeParameter typeParameter) {
    final PsiClassType[] referencedTypes = typeParameter.getExtendsList().getReferencedTypes();
    String extendsText = "";
    if (referencedTypes.length > 0) {
      extendsText = Stream.of(referencedTypes).map(DelombokHandler::getTypeWithParameter)
        .collect(Collectors.joining(" & ", " extends ", ""));
    }
    return typeParameter.getName() + extendsText;
  }

  private static @NotNull String getTypeWithParameter(@NotNull PsiClassType psiClassType) {
    if (psiClassType instanceof PsiClassReferenceType) {
      return ((PsiClassReferenceType)psiClassType).getReference().getText();
    }
    return psiClassType.getName();
  }

  private static void copyModifiers(PsiModifierList fromModifierList, PsiModifierList resultModifierList) {
    for (String modifier : PsiModifier.MODIFIERS) {
      resultModifierList.setModifierProperty(modifier, fromModifierList.hasExplicitModifier(modifier));
    }
  }

  private static PsiField rebuildField(@NotNull Project project, @NotNull PsiField fromField) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

    final PsiField resultField;
    if (fromField instanceof PsiEnumConstant) {
      resultField = elementFactory.createEnumConstantFromText(fromField.getName(), fromField.getContext());
    }
    else {
      resultField = elementFactory.createField(fromField.getName(), fromField.getType());
      resultField.setInitializer(fromField.getInitializer());
    }
    copyModifiers(fromField.getModifierList(), resultField.getModifierList());

    return resultField;
  }
}
