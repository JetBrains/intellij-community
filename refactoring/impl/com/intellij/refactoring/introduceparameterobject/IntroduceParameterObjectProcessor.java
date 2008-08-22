package com.intellij.refactoring.introduceparameterobject;

import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.introduceparameterobject.usageInfo.MergeMethodArguments;
import com.intellij.refactoring.introduceparameterobject.usageInfo.ReplaceParameterAssignmentWithCall;
import com.intellij.refactoring.introduceparameterobject.usageInfo.ReplaceParameterIncrementDecrement;
import com.intellij.refactoring.introduceparameterobject.usageInfo.ReplaceParameterReferenceWithCall;
import com.intellij.refactoring.psi.PropertyUtils;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IntroduceParameterObjectProcessor extends FixableUsagesRefactoringProcessor {
  private static final Logger logger = Logger.getInstance("com.siyeh.rpp.introduceparameterobject.IntroduceParameterObjectProcessor");

  private final PsiMethod method;
  private final String className;
  private final String packageName;
  private final List<String> getterNames;
  private final boolean keepMethodAsDelegate;
  private final boolean myUseExistingClass;
  private final List<PsiParameter> parameters;
  private final int[] paramsToMerge;
  private final List<PsiTypeParameter> typeParams;
  private final Set<PsiParameter> paramsNeedingSetters = new HashSet<PsiParameter>();
  private PsiClass existingClass;
  private boolean myExistingClassCompatible = true;

  public IntroduceParameterObjectProcessor(String className,
                                           String packageName,
                                           PsiMethod method,
                                           List<PsiParameter> parameters,
                                           List<String> getterNames,
                                           boolean keepMethodAsDelegate,
                                           boolean previewUsages,
                                           final boolean useExistingClass) {
    super(method.getProject());
    this.method = method;
    this.className = className;
    this.packageName = packageName;
    this.getterNames = getterNames;
    this.keepMethodAsDelegate = keepMethodAsDelegate;
    myUseExistingClass = useExistingClass;
    this.parameters = new ArrayList<PsiParameter>(parameters);
    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] methodParams = parameterList.getParameters();
    paramsToMerge = new int[parameters.size()];
    int paramsToMergeCount = 0;
    for (int i = 0; i < methodParams.length; i++) {
      final PsiParameter methodParam = methodParams[i];
      if (parameters.contains(methodParam)) {
        paramsToMerge[paramsToMergeCount] = i;
        paramsToMergeCount++;
      }
    }
    final Set<PsiTypeParameter> typeParamSet = new HashSet<PsiTypeParameter>();
    final JavaRecursiveElementVisitor visitor = new TypeParametersVisitor(typeParamSet);
    for (PsiParameter parameter : parameters) {
      parameter.accept(visitor);
    }
    typeParams = new ArrayList<PsiTypeParameter>(typeParamSet);

    final String qualifiedName = StringUtil.getQualifiedName(packageName, className);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    existingClass = JavaPsiFacade.getInstance(myProject).findClass(qualifiedName, scope);

  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usageInfos) {
    return new IntroduceParameterObjectUsageViewDescriptor(method);
  }


  @Override
  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    ArrayList<String> conflicts = new ArrayList<String>();
    if (myUseExistingClass) {
      if (existingClass == null) {
        conflicts.add(RefactorJBundle.message("cannot.perform.the.refactoring") + "Could not find the selected class");
      }
      if (!myExistingClassCompatible) {
        conflicts
          .add(RefactorJBundle.message("cannot.perform.the.refactoring") + "Selected class is not compatible with chosenn parameters");

      }
    }
    else if (existingClass != null) {
      conflicts.add(RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("there.already.exists.a.class.with.the.chosen.name"));
    }
    if (!conflicts.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new RuntimeException(StringUtil.join(conflicts, "\n"));
      }
      return showConflicts(conflicts);
    }

    return super.preprocessUsages(refUsages);
  }

  public void findUsages(@NotNull List<FixableUsageInfo> usages) {
    if (myUseExistingClass && existingClass != null) {
      myExistingClassCompatible = existingClassIsCompatible(existingClass, parameters, getterNames);
      if (!myExistingClassCompatible) return;
    }
    findUsagesForMethod(method, usages);

    final PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method, method.getUseScope(), true).toArray(PsiMethod.EMPTY_ARRAY);
    for (PsiMethod siblingMethod : overridingMethods) {
      findUsagesForMethod(siblingMethod, usages);
    }
  }

  private void findUsagesForMethod(PsiMethod overridingMethod, List<FixableUsageInfo> usages) {
    final PsiManager psiManager = overridingMethod.getManager();
    final Project project = psiManager.getProject();
    final String fixedParamName = calculateNewParamNameForMethod(overridingMethod);

    usages.add(new MergeMethodArguments(overridingMethod, className, packageName, fixedParamName, paramsToMerge, typeParams, keepMethodAsDelegate));

    final ParamUsageVisitor visitor = new ParamUsageVisitor(overridingMethod, paramsToMerge);
    overridingMethod.accept(visitor);
    final Set<PsiReferenceExpression> values = visitor.getParameterUsages();
    for (PsiReferenceExpression paramUsage : values) {
      final PsiParameter parameter = (PsiParameter)paramUsage.resolve();
      assert parameter != null;
      final PsiMethod containingMethod = (PsiMethod)parameter.getDeclarationScope();
      final int index = containingMethod.getParameterList().getParameterIndex(parameter);
      final PsiParameter replacedParameter = method.getParameterList().getParameters()[index];
      final String capitalizedName = StringUtil.capitalize(calculateStrippedName(replacedParameter.getName(), project));
      @NonNls final String getter;
      if (getterNames == null) {
        if (PsiType.BOOLEAN.equals(parameter.getType())) {
          getter = "is" + capitalizedName;
        }
        else {
          getter = "get" + capitalizedName;
        }
      }
      else {
        final int getterIndex = parameters.indexOf(parameter);
        getter = getterNames.get(getterIndex);
      }
      @NonNls final String setter = "set" + capitalizedName;
      if (RefactoringUtil.isPlusPlusOrMinusMinus(paramUsage.getParent())) {
        usages.add(new ReplaceParameterIncrementDecrement(paramUsage, fixedParamName, setter, getter));
        paramsNeedingSetters.add(replacedParameter);
      }
      else if (RefactoringUtil.isAssignmentLHS(paramUsage)) {
        usages.add(new ReplaceParameterAssignmentWithCall(paramUsage, fixedParamName, setter, getter));
        paramsNeedingSetters.add(replacedParameter);
      }
      else {
        usages.add(new ReplaceParameterReferenceWithCall(paramUsage, fixedParamName, getter));
      }
    }
  }

  private static String calculateStrippedName(String name, final Project project) {
    final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    final CodeStyleSettings settings = settingsManager.getCurrentSettings();
    if (name.startsWith(settings.PARAMETER_NAME_PREFIX)) {
      name = name.substring(settings.PARAMETER_NAME_PREFIX.length());
    }
    if (name.endsWith(settings.PARAMETER_NAME_SUFFIX)) {
      name = name.substring(0, name.length() - settings.PARAMETER_NAME_SUFFIX.length());
    }
    return name;
  }

  private String calculateNewParamNameForMethod(PsiMethod testMethod) {
    final Project project = testMethod.getProject();
    final String baseParamName = calculateStrippedName(StringUtil.decapitalize(className), project);

    final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    final CodeStyleSettings settings = settingsManager.getCurrentSettings();
    if (!isParamNameUsed(settings.PARAMETER_NAME_PREFIX + baseParamName + settings.PARAMETER_NAME_SUFFIX, testMethod)) {
      return baseParamName;
    }
    int count = 1;
    while (true) {
      final String testParamName = settings.PARAMETER_NAME_PREFIX + baseParamName + count + settings.PARAMETER_NAME_SUFFIX;
      if (!isParamNameUsed(testParamName, testMethod)) {
        return testParamName;
      }
      count++;
    }
  }

  private boolean isParamNameUsed(String paramName, PsiMethod testMethod) {
    final PsiParameterList testParamList = testMethod.getParameterList();
    final PsiParameter[] testParameters = testParamList.getParameters();
    for (int i = 0; i < testParameters.length; i++) {
      if (!isParamToMerge(i)) {
        if (testParameters[i].getName().equals(paramName)) {
          return true;
        }
      }
    }
    final PsiCodeBlock body = testMethod.getBody();
    if (body == null) {
      return false;
    }
    final NameUsageVisitor visitor = new NameUsageVisitor(paramName);
    body.accept(visitor);
    return visitor.isNameUsed();
  }

  private boolean isParamToMerge(int i) {
    for (int j : paramsToMerge) {
      if (i == j) {
        return true;
      }
    }
    return false;
  }

  protected void performRefactoring(UsageInfo[] usageInfos) {
    buildClass();
    super.performRefactoring(usageInfos);
  }

  private void buildClass() {

    final PsiManager manager = method.getManager();
    final Project project = method.getProject();
    if (existingClass != null) {
      return;
    }
    final ParameterObjectBuilder beanClassBuilder = new ParameterObjectBuilder();
    final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    final CodeStyleSettings settings = settingsManager.getCurrentSettings();
    beanClassBuilder.setCodeStyleSettings(settings);
    beanClassBuilder.setTypeArguments(typeParams);
    beanClassBuilder.setClassName(className);
    beanClassBuilder.setPackageName(packageName);
    for (PsiParameter parameter : parameters) {
      final boolean setterRequired = paramsNeedingSetters.contains(parameter);
      beanClassBuilder.addField(parameter, setterRequired);
    }
    final String classString;
    try {
      classString = beanClassBuilder.buildBeanClass();
    }
    catch (IOException e) {
      logger.error(e);
      return;
    }

    try {
      final PsiFile containingFile = method.getContainingFile();

      final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
      final Module module = ModuleUtil.findModuleForPsiElement(containingFile);
      final PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, containingDirectory, true);

      if (directory != null) {
        final PsiFile newFile = PsiFileFactory.getInstance(project).createFileFromText(className + ".java", classString);

        final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
        final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(newFile.getProject()).shortenClassReferences(newFile);
        final PsiElement reformattedFile = codeStyleManager.reformat(shortenedFile);
        directory.add(reformattedFile);
      }
    }
    catch (IncorrectOperationException e) {
      logger.error(e);
    }
  }

  protected String getCommandName() {
    final PsiClass containingClass = method.getContainingClass();
    return RefactorJBundle.message("introduced.parameter.class.command.name", className, containingClass.getName(), method.getName());
  }


  private static class ParamUsageVisitor extends JavaRecursiveElementVisitor {
    private final Set<PsiParameter> paramsToMerge = new HashSet<PsiParameter>();
    private final Set<PsiReferenceExpression> parameterUsages = new HashSet<PsiReferenceExpression>(4);

    ParamUsageVisitor(PsiMethod method, int[] paramIndicesToMerge) {
      super();
      final PsiParameterList paramList = method.getParameterList();
      final PsiParameter[] parameters = paramList.getParameters();
      for (int i : paramIndicesToMerge) {
        paramsToMerge.add(parameters[i]);
      }
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement referent = expression.resolve();
      if (!(referent instanceof PsiParameter)) {
        return;
      }
      final PsiParameter parameter = (PsiParameter)referent;
      if (paramsToMerge.contains(parameter)) {
        parameterUsages.add(expression);
      }
    }

    public Set<PsiReferenceExpression> getParameterUsages() {
      return parameterUsages;
    }
  }

  private static class NameUsageVisitor extends JavaRecursiveElementVisitor {
    private boolean nameUsed = false;
    private final String paramName;

    NameUsageVisitor(String paramName) {
      super();
      this.paramName = paramName;
    }

    public void visitElement(PsiElement element) {
      if (nameUsed) {
        return;
      }
      super.visitElement(element);
    }

    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      final String variableName = variable.getName();
      if (paramName.equals(variableName)) {
        nameUsed = true;
      }
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression.getQualifier() == null) {
        return;
      }
      final String referenceName = expression.getReferenceName();
      if (paramName.equals(referenceName)) {
        nameUsed = true;
      }
    }

    public boolean isNameUsed() {
      return nameUsed;
    }
  }

  private static boolean existingClassIsCompatible(PsiClass aClass, List<PsiParameter> params, @NonNls List<String> getterNames) {
    if (params.size() == 1) {
      final PsiType paramType = params.get(0).getType();
      if (TypeConversionUtil.isPrimitiveWrapper(aClass.getQualifiedName())) {
        getterNames.add(paramType.getCanonicalText() + "Value");
        return true;
      }
    }
    final PsiMethod[] constructors = aClass.getConstructors();
    PsiMethod compatibleConstructor = null;
    for (PsiMethod constructor : constructors) {
      if (constructorIsCompatible(constructor, params)) {
        compatibleConstructor = constructor;
        break;
      }
    }
    if (compatibleConstructor == null) {
      return false;
    }
    final PsiParameterList parameterList = compatibleConstructor.getParameterList();
    final PsiParameter[] constructorParams = parameterList.getParameters();
    for (PsiParameter param : constructorParams) {
      final PsiField field = findFieldAssigned(param, compatibleConstructor);
      if (field == null) {
        return false;
      }
      final PsiMethod getter = PropertyUtils.findGetterForField(field);
      if (getter == null) {
        return false;
      }
      getterNames.add(getter.getName());
    }
    //TODO: this fails if there are any setters required
    return true;
  }

  private static boolean constructorIsCompatible(PsiMethod constructor, List<PsiParameter> params) {
    if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }
    final PsiParameterList parameterList = constructor.getParameterList();
    final PsiParameter[] constructorParams = parameterList.getParameters();
    if (constructorParams.length != params.size()) {
      return false;
    }
    for (int i = 0; i < constructorParams.length; i++) {
      if (!TypeConversionUtil.isAssignable(constructorParams[i].getType(), params.get(i).getType())) {
        return false;
      }
    }
    return true;
  }

  private static PsiField findFieldAssigned(PsiParameter param, PsiMethod constructor) {
    final ParamAssignmentFinder visitor = new ParamAssignmentFinder(param);
    constructor.accept(visitor);
    return visitor.getFieldAssigned();
  }

  private static class ParamAssignmentFinder extends JavaRecursiveElementVisitor {

    private final PsiParameter param;

    private PsiField fieldAssigned = null;

    ParamAssignmentFinder(PsiParameter param) {
      this.param = param;
    }

    public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);
      final PsiExpression lhs = assignment.getLExpression();
      final PsiExpression rhs = assignment.getRExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      if (!(rhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference)rhs).resolve();
      if (referent == null || !referent.equals(param)) {
        return;
      }
      final PsiElement assigned = ((PsiReference)lhs).resolve();
      if (assigned == null || !(assigned instanceof PsiField)) {
        return;
      }
      fieldAssigned = (PsiField)assigned;
    }

    public PsiField getFieldAssigned() {
      return fieldAssigned;
    }

  }
}
