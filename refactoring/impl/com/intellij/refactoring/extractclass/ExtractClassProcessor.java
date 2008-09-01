package com.intellij.refactoring.extractclass;

import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.extractclass.usageInfo.*;
import com.intellij.refactoring.psi.MethodInheritanceUtils;
import com.intellij.refactoring.psi.TypeParametersVisitor;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

public class ExtractClassProcessor extends FixableUsagesRefactoringProcessor {
  private static final Logger logger = Logger.getInstance("com.siyeh.rpp.extractclass.ExtractClassProcessor");

  private final PsiClass sourceClass;
  private final List<PsiField> fields;
  private final List<PsiMethod> methods;
  private final List<PsiClass> innerClasses;
  private final List<PsiClassInitializer> initializersToMove;
  private final Set<PsiClass> innerClassesToMakePublic = new HashSet<PsiClass>();
  private final List<PsiTypeParameter> typeParams = new ArrayList<PsiTypeParameter>();
  private final String newPackageName;
  private final String newClassName;
  private final String delegateFieldName;
  private final Set<PsiField> fieldsRequiringGetters = new HashSet<PsiField>();
  private final Set<PsiField> fieldsRequiringSetters = new HashSet<PsiField>();
  private final Set<PsiMethod> methodsRequiringDelegation = new HashSet<PsiMethod>();
  private final boolean requiresBackpointer;

  public ExtractClassProcessor(PsiClass sourceClass,
                               List<PsiField> fields,
                               List<PsiMethod> methods,
                               List<PsiClass> innerClasses,
                               String newPackageName,
                               String newClassName) {
    super(sourceClass.getProject());
    this.sourceClass = sourceClass;
    this.newPackageName = newPackageName;
    this.fields = new ArrayList<PsiField>(fields);
    this.methods = new ArrayList<PsiMethod>(methods);
    this.innerClasses = new ArrayList<PsiClass>(innerClasses);
    initializersToMove = calculateInitializersToMove();
    this.newClassName = newClassName;
    delegateFieldName = calculateDelegateFieldName();
    requiresBackpointer = new BackpointerUsageVisitor(fields, innerClasses, methods, sourceClass).backpointerRequired();
    if (requiresBackpointer) {
      typeParams.addAll(Arrays.asList(sourceClass.getTypeParameters()));
    }
    else {
      final Set<PsiTypeParameter> typeParamSet = new HashSet<PsiTypeParameter>();
      final TypeParametersVisitor visitor = new TypeParametersVisitor(typeParamSet);
      for (PsiField field : fields) {
        field.accept(visitor);
      }
      for (PsiMethod method : methods) {
        method.accept(visitor);
      }
      typeParams.addAll(typeParamSet);
    }
  }

  @Override
  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    final List<String> conflicts = new ArrayList<String>();
    final Project project = sourceClass.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass existingClass =
      JavaPsiFacade.getInstance(project).findClass(StringUtil.getQualifiedName(newPackageName, newClassName), scope);
    if (existingClass != null) {
      conflicts.add(RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("there.already.exists.a.class.with.the.chosen.name"));
    }
    return showConflicts(conflicts);
  }

  @Override
  protected boolean showConflicts(final List<String> conflicts) {
    if (!conflicts.isEmpty() && ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(StringUtil.join(conflicts, "\n"));
    }
    return super.showConflicts(conflicts);
  }

  private List<PsiClassInitializer> calculateInitializersToMove() {
    final List<PsiClassInitializer> out = new ArrayList<PsiClassInitializer>();
    final PsiClassInitializer[] initializers = sourceClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      if (initialierShouldBeMoved(initializer)) {
        out.add(initializer);
      }
    }
    return out;
  }

  private boolean initialierShouldBeMoved(PsiClassInitializer initializer) {
    final BackpointerUsageVisitor visitor = new BackpointerUsageVisitor(fields, innerClasses, methods, sourceClass);
    initializer.accept(visitor);
    return !visitor.isBackpointerRequired();
  }

  private String calculateDelegateFieldName() {
    final Project project = sourceClass.getProject();
    final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    final CodeStyleSettings settings = settingsManager.getCurrentSettings();

    final String baseName = StringUtil.toLowerCase(newClassName);
    String name = settings.FIELD_NAME_PREFIX + baseName + settings.FIELD_NAME_SUFFIX;
    if (!existsFieldWithName(name) && !JavaPsiFacade.getInstance(project).getNameHelper().isKeyword(name)) {
      return name;
    }
    int counter = 1;
    while (true) {
      name = settings.FIELD_NAME_PREFIX + baseName + counter + settings.FIELD_NAME_SUFFIX;
      if (!existsFieldWithName(name) && !JavaPsiFacade.getInstance(project).getNameHelper().isKeyword(name)) {
        return name;
      }
      counter++;
    }
  }


  private boolean existsFieldWithName(String name) {
    final PsiField[] allFields = sourceClass.getAllFields();
    for (PsiField field : allFields) {
      if (name.equals(field.getName()) && !fields.contains(field)) {
        return true;
      }
    }
    return false;
  }

  protected String getCommandName() {
    return RefactorJBundle.message("extracted.class.command.name", newClassName);
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usageInfos) {
    return new ExtractClassUsageViewDescriptor(sourceClass);
  }

  protected void performRefactoring(UsageInfo[] usageInfos) {
    buildClass();
    if (!(fieldsRequiringGetters.isEmpty() && methodsRequiringDelegation.isEmpty())) {
      buildDelegate();
    }
    buildNecessaryGettersAndSetters();
    super.performRefactoring(usageInfos);
  }

  private void buildNecessaryGettersAndSetters() {
    try {
      final NecessaryAccessorsVisitor visitor = new NecessaryAccessorsVisitor();
      for (PsiMethod method : methods) {
        method.accept(visitor);
      }
      for (PsiClass innerClass : innerClasses) {
        innerClass.accept(visitor);
      }

      final Set<PsiField> fieldsNeedingGetter = visitor.getFieldsNeedingGetter();
      for (PsiField field : fieldsNeedingGetter) {
        createGetterIfNecessary(field);
      }
      final Set<PsiField> fieldsNeedingSetter = visitor.getFieldsNeedingSetter();
      for (PsiField field : fieldsNeedingSetter) {
        createSetterIfNecessary(field);
      }
      final Set<PsiMethod> methodsNeedingPublic = visitor.getMethodsNeedingPublic();
      for (PsiMethod method : methodsNeedingPublic) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
          method.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        }

        final PsiMethod[] superMethods = method.findSuperMethods(); //todo
        for (PsiMethod superMethod : superMethods) {
          final PsiClass containingSuperClass = superMethod.getContainingClass();
          if (!containingSuperClass.isInterface() && !superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
            superMethod.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      logger.error(e);
    }
  }

  private void createGetterIfNecessary(PsiField field) {

    final PsiManager manager = sourceClass.getManager();
    final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
    try {
      final PsiMethod method = PropertyUtil.generateGetterPrototype(field);
      final PsiElement newMethod = sourceClass.add(method);
      codeStyleManager.reformat(newMethod);
    }
    catch (IncorrectOperationException e) {
      logger.error(e);
    }
  }

  private void createSetterIfNecessary(PsiField field) {

    final PsiManager manager = sourceClass.getManager();
    final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
    try {

      final PsiMethod setter = PropertyUtil.generateSetterPrototype(field);
      final PsiElement newMethod = sourceClass.add(setter);
      codeStyleManager.reformat(newMethod);
    }
    catch (IncorrectOperationException e) {
      logger.error(e);
    }
  }


  private void buildDelegate() {
    final PsiManager manager = sourceClass.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
    @NonNls final StringBuilder fieldBuffer = new StringBuilder();
    final String delegateVisibility = calculateDelegateVisibility();
    fieldBuffer.append(delegateVisibility + ' ');
    fieldBuffer.append("final ");
    final String fullyQualifiedName = StringUtil.getQualifiedName(newPackageName, newClassName);
    fieldBuffer.append(fullyQualifiedName);
    if (!typeParams.isEmpty()) {
      fieldBuffer.append('<');
      for (PsiTypeParameter typeParameter : typeParams) {
        fieldBuffer.append(typeParameter.getName());
      }
      fieldBuffer.append('>');
    }
    fieldBuffer.append(' ');
    fieldBuffer.append(delegateFieldName);
    fieldBuffer.append('=');
    fieldBuffer.append("new " + fullyQualifiedName);
    if (!typeParams.isEmpty()) {
      fieldBuffer.append('<');
      for (PsiTypeParameter typeParameter : typeParams) {
        fieldBuffer.append(typeParameter.getName());
      }
      fieldBuffer.append('>');
    }
    fieldBuffer.append('(');
    boolean isFirst = true;
    if (requiresBackpointer) {
      fieldBuffer.append("this");
      isFirst = false;
    }
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }
      if (!field.hasInitializer()) {
        continue;
      }
      final PsiExpression initializer = field.getInitializer();
      if (PsiUtil.isConstantExpression(initializer)) {
        continue;
      }
      if (!isFirst) {
        fieldBuffer.append(", ");
      }
      isFirst = false;
      assert initializer != null;
      fieldBuffer.append(initializer.getText());
    }

    fieldBuffer.append(");");
    try {
      final String fieldString = fieldBuffer.toString();
      final PsiField field = factory.createFieldFromText(fieldString, sourceClass);
      final PsiElement newField = sourceClass.add(field);
      codeStyleManager.reformat(newField);
    }
    catch (IncorrectOperationException e) {
      logger.error(e);
    }
  }

  @NonNls
  private String calculateDelegateVisibility() {
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.PUBLIC) && !field.hasModifierProperty(PsiModifier.STATIC)) {
        return "public";
      }
    }
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.PROTECTED) && !field.hasModifierProperty(PsiModifier.STATIC)) {
        return "protected";
      }
    }
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && !field.hasModifierProperty(PsiModifier.STATIC)) {
        return "";
      }
    }
    return "private";
  }

  public void findUsages(@NotNull List<FixableUsageInfo> usages) {
    for (PsiField field : fields) {
      findUsagesForField(field, usages);
      usages.add(new RemoveField(field));
    }
    for (PsiClass innerClass : innerClasses) {
      findUsagesForInnerClass(innerClass, usages);
      usages.add(new RemoveInnerClass(innerClass));
    }
    for (PsiClassInitializer initializer : initializersToMove) {
      usages.add(new RemoveInitializer(initializer));
    }
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        findUsagesForStaticMethod(method, usages);
      }
      else {
        findUsagesForMethod(method, usages);
      }
    }
  }

  private void findUsagesForInnerClass(PsiClass innerClass, List<FixableUsageInfo> usages) {
    final PsiManager psiManager = innerClass.getManager();
    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final Iterable<PsiReference> calls = ReferencesSearch.search(innerClass, scope);
    final String innerName = innerClass.getQualifiedName();
    final String newInnerClassName = StringUtil.getQualifiedName(newPackageName, newClassName) + innerName.substring(sourceClass.getQualifiedName().length());
    boolean hasExternalReference = false;
    for (PsiReference reference : calls) {
      final PsiElement referenceElement = reference.getElement();
      if (referenceElement instanceof PsiJavaCodeReferenceElement) {
        if (!isInMovedElement(referenceElement)) {

          usages.add(new ReplaceClassReference((PsiJavaCodeReferenceElement)referenceElement, newInnerClassName));
          hasExternalReference = true;
        }
      }
    }
    if (hasExternalReference) {
      innerClassesToMakePublic.add(innerClass);
    }
  }

  private void findUsagesForMethod(PsiMethod method, List<FixableUsageInfo> usages) {
    final PsiManager psiManager = method.getManager();
    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final Iterable<PsiReference> calls = ReferencesSearch.search(method, scope);
    for (PsiReference reference : calls) {
      final PsiElement referenceElement = reference.getElement();
      final PsiElement parent = referenceElement.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression)parent;
        if (isInMovedElement(call)) {
          continue;
        }
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiThisExpression) {
          usages.add(new ReplaceThisCallWithDelegateCall(call, delegateFieldName));
        }
        methodsRequiringDelegation.add(method);
      }
    }

    if (methodsRequiringDelegation.contains(method) || MethodInheritanceUtils.hasSiblingMethods(method)) {
      usages.add(new MakeMethodDelegate(method, delegateFieldName));
    }
    else {
      usages.add(new RemoveMethod(method));
    }
  }

  private void findUsagesForStaticMethod(PsiMethod method, List<FixableUsageInfo> usages) {
    final PsiManager psiManager = method.getManager();
    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final Iterable<PsiReference> calls = ReferencesSearch.search(method, scope);
    for (PsiReference reference : calls) {
      final PsiElement referenceElement = reference.getElement();

      final PsiElement parent = referenceElement.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression)parent;
        if (!isInMovedElement(call)) {
          final String fullyQualifiedName = StringUtil.getQualifiedName(newPackageName, newClassName);
          usages.add(new RetargetStaticMethodCall(call, fullyQualifiedName));
        }
      }
    }
    usages.add(new RemoveMethod(method));
  }

  private boolean isInMovedElement(PsiElement exp) {
    for (PsiField field : fields) {
      if (PsiTreeUtil.isAncestor(field, exp, false)) {
        return true;
      }
    }
    for (PsiMethod method : methods) {
      if (PsiTreeUtil.isAncestor(method, exp, false)) {
        return true;
      }
    }
    return false;
  }

  private void findUsagesForField(PsiField field, List<FixableUsageInfo> usages) {
    final PsiManager psiManager = field.getManager();
    final Project project = psiManager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);

    for (PsiReference reference : ReferencesSearch.search(field, scope)) {
      final PsiReferenceExpression exp = (PsiReferenceExpression)reference.getElement();
      if (isInMovedElement(exp)) {
        continue;
      }
      final String qualifiedName = StringUtil.getQualifiedName(newPackageName, newClassName);
      @NonNls final String getter = PropertyUtil.suggestGetterName(myProject, field);
      @NonNls final String setter = PropertyUtil.suggestSetterName(myProject, field);
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        final boolean isPublic = field.hasModifierProperty(PsiModifier.PUBLIC);
        if (RefactoringUtil.isPlusPlusOrMinusMinus(exp)) {
          usages.add(new ReplaceStaticVariableIncrementDecrement(exp, qualifiedName, setter, getter, isPublic));
          if (!isPublic) {
            fieldsRequiringSetters.add(field);
          }
        }
        else if (RefactoringUtil.isAssignmentLHS(exp)) {
          usages.add(new ReplaceStaticVariableAssignment(exp, qualifiedName, setter, getter, isPublic));
          if (!isPublic) {
            fieldsRequiringSetters.add(field);
          }
        }
        else {
          usages.add(new ReplaceStaticVariableAccess(exp, qualifiedName, getter, isPublic));
        }
        if (!isPublic) {
          fieldsRequiringGetters.add(field);
        }
      }
      else {
        if (RefactoringUtil.isPlusPlusOrMinusMinus(exp)) {
          usages.add(new ReplaceInstanceVariableIncrementDecrement(exp, delegateFieldName, setter, getter));
          fieldsRequiringSetters.add(field);
        }
        else if (RefactoringUtil.isAssignmentLHS(exp)) {
          final PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(exp, PsiAssignmentExpression.class);
          usages.add(new ReplaceInstanceVariableAssignment(assignment, delegateFieldName, setter, getter));
          fieldsRequiringSetters.add(field);
        }
        else {
          usages.add(new ReplaceInstanceVariableAccess(exp, delegateFieldName, getter));
        }
        fieldsRequiringGetters.add(field);
      }
    }
  }


  private void buildClass() {
    final PsiManager manager = sourceClass.getManager();
    final Project project = sourceClass.getProject();
    final ExtractedClassBuilder extractedClassBuilder = new ExtractedClassBuilder();
    final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    final CodeStyleSettings settings = settingsManager.getCurrentSettings();
    extractedClassBuilder.setCodeStyleSettings(settings);
    extractedClassBuilder.setClassName(newClassName);
    extractedClassBuilder.setPackageName(newPackageName);
    extractedClassBuilder.setOriginalClassName(sourceClass.getQualifiedName());
    extractedClassBuilder.setRequiresBackPointer(requiresBackpointer);
    for (PsiField field : fields) {
      extractedClassBuilder.addField(field, fieldsRequiringGetters.contains(field), fieldsRequiringSetters.contains(field));
    }
    for (PsiMethod method : methods) {
      extractedClassBuilder.addMethod(method);
    }
    for (PsiClass innerClass : innerClasses) {
      extractedClassBuilder.addInnerClass(innerClass, innerClassesToMakePublic.contains(innerClass));
    }
    for (PsiClassInitializer initializer : initializersToMove) {
      extractedClassBuilder.addInitializer(initializer);
    }
    extractedClassBuilder.setTypeArguments(typeParams);
    final List<PsiClass> interfaces = calculateInterfacesSupported();
    extractedClassBuilder.setInterfaces(interfaces);

    final String classString;
    try {
      classString = extractedClassBuilder.buildBeanClass();
    }
    catch (IOException e) {
      logger.error(e);
      return;
    }

    try {
      final PsiFile containingFile = sourceClass.getContainingFile();

      final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
      final Module module = ModuleUtil.findModuleForPsiElement(containingFile);
      final PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(module, newPackageName, containingDirectory, true);
      if (directory != null) {
        final PsiFile newFile = PsiFileFactory.getInstance(project).createFileFromText(newClassName + ".java", classString);
        final PsiElement addedFile = directory.add(newFile);
        final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
        final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedFile);
        codeStyleManager.reformat(shortenedFile);
      }
    }
    catch (IncorrectOperationException e) {
      logger.error(e);
    }
  }

  private List<PsiClass> calculateInterfacesSupported() {
    final List<PsiClass> out = new ArrayList<PsiClass>();
    final PsiClass[] supers = sourceClass.getSupers();
    for (PsiClass superClass : supers) {
      if (!superClass.isInterface()) {
        continue;
      }
      final PsiMethod[] superclassMethods = superClass.getMethods();
      if (superclassMethods.length == 0) {
        continue;
      }
      boolean allMethodsCovered = true;

      for (PsiMethod method : superclassMethods) {
        boolean isCovered = false;
        for (PsiMethod movedMethod : methods) {
          if (isSuperMethod(method, movedMethod)) {
            isCovered = true;
            break;
          }
        }
        if (!isCovered) {
          allMethodsCovered = false;
          break;
        }
      }
      if (allMethodsCovered) {
        out.add(superClass);
      }
    }
    final Project project = sourceClass.getProject();
    final PsiManager manager = sourceClass.getManager();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    if (usesDefaultSerialization(sourceClass)) {
      final PsiClass serializable = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.io.Serializable", scope);
      out.add(serializable);
    }
    if (usesDefaultClone(sourceClass)) {
      final PsiClass cloneable = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Cloneable", scope);
      out.add(cloneable);
    }
    return out;
  }

  private static boolean isSuperMethod(PsiMethod method, PsiMethod movedMethod) {
    final PsiMethod[] superMethods = movedMethod.findSuperMethods();
    for (PsiMethod testMethod : superMethods) {
      if (testMethod.equals(method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean usesDefaultClone(PsiClass aClass) {
    final Project project = aClass.getProject();
    final PsiManager manager = aClass.getManager();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass cloneable = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Cloneable", scope);
    if (!InheritanceUtil.isCorrectDescendant(aClass, cloneable, true)) {
      return false;
    }
    final PsiMethod[] methods = aClass.findMethodsByName("clone", false);
    for (PsiMethod method : methods) {
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length == 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean usesDefaultSerialization(PsiClass aClass) {
    final Project project = aClass.getProject();
    final PsiManager manager = aClass.getManager();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass serializable = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.io.Serializable", scope);
    if (!InheritanceUtil.isCorrectDescendant(aClass, serializable, true)) {
      return false;
    }
    final PsiMethod[] methods = aClass.findMethodsByName("writeObject", false);
    for (PsiMethod method : methods) {
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length == 1) {
        final PsiType type = parameters[0].getType();
        final String text = type.getCanonicalText();
        if ("java.io.DataOutputStream".equals(text)) {
          return false;
        }
      }
    }
    return true;
  }

  private class NecessaryAccessorsVisitor extends JavaRecursiveElementVisitor {
    private final Set<PsiMethod> methodsNeedingPublic = new HashSet<PsiMethod>();
    private final Set<PsiField> fieldsNeedingGetter = new HashSet<PsiField>();
    private final Set<PsiField> fieldsNeedingSetter = new HashSet<PsiField>();

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiMethod method = expression.resolveMethod();
      if (method != null && method.getContainingClass().equals(sourceClass) && !method.hasModifierProperty(PsiModifier.PUBLIC)) {
        methodsNeedingPublic.add(method);
      }
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (isBackpointerReference(expression)) {
        fieldsNeedingGetter.add(getReferencedField(expression));
      }
    }

    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);

      final PsiExpression lhs = expression.getLExpression();
      if (isBackpointerReference(lhs)) {
        fieldsNeedingSetter.add(getReferencedField(lhs));
      }
    }

    public void visitPostfixExpression(PsiPostfixExpression expression) {
      super.visitPostfixExpression(expression);
      checkSetterNeeded(expression.getOperand(), expression.getOperationSign());
    }

    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      checkSetterNeeded(expression.getOperand(), expression.getOperationSign());
    }

    private void checkSetterNeeded(final PsiExpression operand, final PsiJavaToken sign) {
      final IElementType tokenType = sign.getTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return;
      }
      if (isBackpointerReference(operand)) {
        fieldsNeedingSetter.add(getReferencedField(operand));
      }
    }

    public Set<PsiField> getFieldsNeedingGetter() {
      return fieldsNeedingGetter;
    }

    public Set<PsiField> getFieldsNeedingSetter() {
      return fieldsNeedingSetter;
    }

    public Set<PsiMethod> getMethodsNeedingPublic() {
      return methodsNeedingPublic;
    }

    private boolean isBackpointerReference(PsiExpression expression) {
      return BackpointerUtil.isBackpointerReference(expression, new Condition<PsiField>() {
        public boolean value(final PsiField field) {
          if (fields.contains(field)) {
            return false;
          }
          if (innerClasses.contains(field.getContainingClass())) {
            return false;
          }
          return true;
        }
      });
    }

    private PsiField getReferencedField(PsiExpression expression) {
      if (expression instanceof PsiParenthesizedExpression) {
        final PsiExpression contents = ((PsiParenthesizedExpression)expression).getExpression();
        return getReferencedField(contents);
      }
      final PsiReferenceExpression reference = (PsiReferenceExpression)expression;
      return (PsiField)reference.resolve();
    }
  }
}
