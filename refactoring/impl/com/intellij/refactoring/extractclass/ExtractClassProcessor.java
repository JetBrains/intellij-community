package com.intellij.refactoring.extractclass;

import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.AssignmentUtil;
import com.intellij.refactoring.psi.MethodInheritanceUtils;
import com.intellij.refactoring.psi.SearchUtils;
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

class ExtractClassProcessor extends FixableUsagesRefactoringProcessor {
    private static final Logger logger =
            Logger.getInstance("com.siyeh.rpp.extractclass.ExtractClassProcessor");

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

    ExtractClassProcessor(PsiClass sourceClass,
                          List<PsiField> fields,
                          List<PsiMethod> methods,
                          List<PsiClass> innerClasses,
                          String newPackageName,
                          String newClassName,
                          boolean previewUsages) {
        super(sourceClass.getProject());
        this.sourceClass = sourceClass;
        this.newPackageName = newPackageName;
        this.fields = new ArrayList<PsiField>(fields);
        this.methods = new ArrayList<PsiMethod>(methods);
        this.innerClasses = new ArrayList<PsiClass>(innerClasses);
        initializersToMove = calculateInitializersToMove();
        this.newClassName = newClassName;
        delegateFieldName = calculateDelegateFieldName();
        requiresBackpointer = backpointerRequired();
        if (requiresBackpointer) {
            for (PsiTypeParameter param : sourceClass.getTypeParameters()) {
                typeParams.add(param);
            }
        } else {
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
        final BackpointerUsageVisitor visitor = new BackpointerUsageVisitor();
        initializer.accept(visitor);
        return !visitor.isBackpointerRequired();
    }

    private String calculateDelegateFieldName() {
        final Project project = sourceClass.getProject();
        final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
        final CodeStyleSettings settings = settingsManager.getCurrentSettings();

        final String baseName = toLowerCase(newClassName);
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

    private static String toLowerCase(String name) {
        if (name.length() > 0) {
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        } else {
            return String.valueOf(Character.toLowerCase(name.charAt(0)));
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
        return new ExtractClassUsageViewDescriptor(sourceClass, usageInfos);
    }

    protected void performRefactoring(UsageInfo[] usageInfos) {
        buildClass();
        if (isDelegationRequired()) {
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

                final PsiMethod[] superMethods =
                        method.findSuperMethods();
                for (PsiMethod superMethod : superMethods) {
                    final PsiClass containingSuperClass = superMethod.getContainingClass();
                    if (!containingSuperClass.isInterface() &&
                            !superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
                        superMethod.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
                    }
                }
            }
        } catch (IncorrectOperationException e) {
            logger.error(e);
        }
    }

    private void createGetterIfNecessary(PsiField field) {
        @NonNls final StringBuilder out = new StringBuilder();
        final PsiType type = field.getType();
        final String typeText = type.getCanonicalText();
        final String name = calculateStrippedName(field);
        final String capitalizedName = StringUtil.capitalize(name);
        @NonNls final String getterName;
        if (PsiType.BOOLEAN.equals(type)) {
            getterName = "is" + capitalizedName;
        } else {
            getterName = "get" + capitalizedName;
        }
        final PsiMethod[] methods = sourceClass.getMethods();
        for (PsiMethod method : methods) {
            final String methodName = method.getName();
            if (getterName.equals(methodName) &&
                    method.getParameterList().getParameters().length == 0) {
                return;
            }
        }
        out.append("\tpublic ");
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            out.append("\tstatic ");
        }
        out.append(typeText);
        out.append(' ');
        out.append(getterName);
        out.append("()\n");
        out.append("\t{\n");
        final String fieldName = field.getName();
        out.append("\t\treturn " + fieldName + ";\n");
        out.append("\t}\n");
        out.append('\n');
        final PsiManager manager = sourceClass.getManager();
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
        try {
            final String fieldString = out.toString();
            final PsiMethod method = elementFactory.createMethodFromText(fieldString, sourceClass);
            final PsiElement newMethod = sourceClass.add(method);
            codeStyleManager.reformat(newMethod);
        } catch (IncorrectOperationException e) {
            logger.error(e);
        }
    }

    private void createSetterIfNecessary(PsiField field) {
        @NonNls final StringBuilder out = new StringBuilder();
        final PsiType type = field.getType();
        final String typeText = type.getCanonicalText();
        final String name = calculateStrippedName(field);
        final String capitalizedName = StringUtil.capitalize(name);
        @NonNls final String setterName = "set" + capitalizedName;
        final PsiMethod[] methods = sourceClass.getMethods();
        for (PsiMethod method : methods) {
            final String methodName = method.getName();
            if (methodName.equals(setterName) &&
                    method.getParameterList().getParameters().length == 1) {
                return;
            }
        }
        final Project project = field.getProject();
        final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
        final CodeStyleSettings settings = settingsManager.getCurrentSettings();
        final String parameterName =
                settings.PARAMETER_NAME_PREFIX + name + settings.PARAMETER_NAME_SUFFIX;
        out.append("\tpublic ");
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            out.append("static ");
        }
        out.append("void ");
        out.append(setterName);
        out.append('(');
        out.append(settings.GENERATE_FINAL_PARAMETERS ? "final " : "");
        out.append(typeText);
        out.append(' ');
        out.append(parameterName);
        out.append(")\n");
        out.append("\t{\n");

        final String fieldName = field.getName();
        assert fieldName != null;
        if (fieldName.equals(parameterName)) {
            out.append("\t\tthis." + fieldName + " = " + parameterName + ";\n");
        } else {
            out.append("\t\t" + fieldName + " = " + parameterName + ";\n");
        }
        out.append("\t}\n");
        final PsiManager manager = sourceClass.getManager();
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
        try {
            final String fieldString = out.toString();
            final PsiMethod method = elementFactory.createMethodFromText(fieldString, sourceClass);
            final PsiElement newMethod = sourceClass.add(method);
            codeStyleManager.reformat(newMethod);
        } catch (IncorrectOperationException e) {
            logger.error(e);
        }
    }

    private static String calculateStrippedName(PsiVariable variable) {
        final Project project = variable.getProject();
        final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
        final CodeStyleSettings settings = settingsManager.getCurrentSettings();
        String name = variable.getName();
        assert name != null;
        if (variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.STATIC)) {
            if (name.startsWith(settings.STATIC_FIELD_NAME_PREFIX)) {
                name = name.substring(settings.STATIC_FIELD_NAME_PREFIX.length());
            }
            if (name.endsWith(settings.STATIC_FIELD_NAME_SUFFIX)) {
                name = name.substring(0, name.length() - settings.STATIC_FIELD_NAME_SUFFIX.length());
            }
        } else if (variable instanceof PsiField && !variable.hasModifierProperty(PsiModifier.STATIC)) {
            if (name.startsWith(settings.FIELD_NAME_PREFIX)) {
                name = name.substring(settings.FIELD_NAME_PREFIX.length());
            }
            if (name.endsWith(settings.FIELD_NAME_SUFFIX)) {
                name = name.substring(0, name.length() - settings.FIELD_NAME_SUFFIX.length());
            }
        }
        return name;
    }

    private void buildDelegate() {
        final PsiManager manager = sourceClass.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
        @NonNls final StringBuilder fieldBuffer = new StringBuilder();
        final String delegateVisibility = calculateDelegateVisibility();
        fieldBuffer.append(delegateVisibility + ' ');
        fieldBuffer.append("final ");
        final String fullyQualifiedName =
                StringUtil.getQualifiedName(newPackageName, newClassName);
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
        } catch (IncorrectOperationException e) {
            logger.error(e);
        }
    }

    @NonNls
    private String calculateDelegateVisibility() {
        for (PsiField field : fields) {
            if (field.hasModifierProperty(PsiModifier.PUBLIC) &&
                    !field.hasModifierProperty(PsiModifier.STATIC)) {
                return "public";
            }
        }
        for (PsiField field : fields) {
            if (field.hasModifierProperty(PsiModifier.PROTECTED) &&
                    !field.hasModifierProperty(PsiModifier.STATIC)) {
                return "protected";
            }
        }
        for (PsiField field : fields) {
            if (field.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
                    !field.hasModifierProperty(PsiModifier.STATIC)) {
                return "";
            }
        }
        return "private";
    }

    private boolean isDelegationRequired() {
        for (PsiField field : fields) {
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
        }
        for (PsiMethod method : methods) {
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
        }
        return false;
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
            } else {
                findUsagesForMethod(method, usages);
            }
        }
    }

    private void findUsagesForInnerClass(PsiClass innerClass, List<FixableUsageInfo> usages) {
        final PsiManager psiManager = innerClass.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final Iterable<PsiReference> calls = SearchUtils.findAllReferences(innerClass, scope);
        final String innerName = innerClass.getQualifiedName();
        final String newInnerClassName = newPackageName + '.' + newClassName +
                innerName.substring(sourceClass.getQualifiedName().length());
        boolean hasExternalReference = false;
        for (PsiReference reference : calls) {
            final PsiElement referenceElement = reference.getElement();
            if (referenceElement instanceof PsiJavaCodeReferenceElement) {
                if (!isInMovedElement(referenceElement)) {

                    usages.add(new ReplaceClassReference((PsiJavaCodeReferenceElement) referenceElement,
                            newInnerClassName));
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
        final Iterable<PsiReference> calls = SearchUtils.findAllReferences(method, scope);
        for (PsiReference reference : calls) {
            final PsiElement referenceElement = reference.getElement();
            final PsiElement parent = referenceElement.getParent();
            if (parent instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression call = (PsiMethodCallExpression) parent;
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

        if (methodsRequiringDelegation.contains(method) ||
                MethodInheritanceUtils.hasSiblingMethods(method)) {
            usages.add(new MakeMethodDelegate(method, delegateFieldName));
        } else {
            usages.add(new RemoveMethod(method));
        }
    }

    private void findUsagesForStaticMethod(PsiMethod method, List<FixableUsageInfo> usages) {
        final PsiManager psiManager = method.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final Iterable<PsiReference> calls = SearchUtils.findAllReferences(method, scope);
        for (PsiReference reference : calls) {
            final PsiElement referenceElement = reference.getElement();

            final PsiElement parent = referenceElement.getParent();
            if (parent instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression call =
                        (PsiMethodCallExpression) parent;
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

    private boolean backpointerRequired() {
        final BackpointerUsageVisitor visitor = new BackpointerUsageVisitor();
        for (PsiMethod method : methods) {
            method.accept(visitor);
            if (visitor.isBackpointerRequired()) {
                return true;
            }
        }
        for (PsiField field : fields) {
            field.accept(visitor);
            if (visitor.isBackpointerRequired()) {
                return true;
            }
        }
        for (PsiClass innerClass : innerClasses) {
            innerClass.accept(visitor);
            if (visitor.isBackpointerRequired()) {
                return true;
            }
        }
        return false;
    }

    private void findUsagesForField(PsiField field, List<FixableUsageInfo> usages) {
        final PsiManager psiManager = field.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final Iterable<PsiReference> references = SearchUtils.findAllReferences(field, scope);

        for (PsiReference reference : references) {
            final PsiReferenceExpression exp = (PsiReferenceExpression) reference.getElement();
            if (isInMovedElement(exp)) {
                continue;
            }
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                final String qualifiedName = StringUtil.getQualifiedName(newPackageName, newClassName);
                final String capitalizedName = StringUtil.capitalize(calculateStrippedName(field));
                final PsiType fieldType = field.getType();
                @NonNls final String getter;
                if (PsiType.BOOLEAN.equals(fieldType)) {
                    getter = "is" + capitalizedName;
                } else {
                    getter = "get" + capitalizedName;
                }
                @NonNls final String setter = "set" + capitalizedName;
                final boolean isPublic = field.hasModifierProperty(PsiModifier.PUBLIC);
                if (AssignmentUtil.isPreIncrementedOrDecremented(exp)) {
                    usages.add(new ReplaceStaticVariablePreIncrementDecrement(exp, qualifiedName, setter, getter,
                            isPublic));
                    if (!isPublic) {
                        fieldsRequiringSetters.add(field);
                    }
                } else if (AssignmentUtil.isPostIncrementedOrDecremented(exp)) {
                    usages.add(new ReplaceStaticVariablePostIncrementDecrement(exp, qualifiedName, setter, getter,
                            isPublic));
                    if (!isPublic) {
                        fieldsRequiringSetters.add(field);
                    }
                } else if (RefactoringUtil.isAssignmentLHS(exp)) {
                    usages.add(new ReplaceStaticVariableAssignment(exp, qualifiedName, setter, getter, isPublic));
                    if (!isPublic) {
                        fieldsRequiringSetters.add(field);
                    }
                } else {
                    usages.add(new ReplaceStaticVariableAccess(exp, qualifiedName, getter, isPublic));
                }
                if (!isPublic) {
                    fieldsRequiringGetters.add(field);
                }
            } else {

                final String capitalizedName = StringUtil.capitalize(calculateStrippedName(field));
                final PsiType fieldType = field.getType();
                @NonNls final String getter;
                if (PsiType.BOOLEAN.equals(fieldType)) {
                    getter = "is" + capitalizedName;
                } else {
                    getter = "get" + capitalizedName;
                }
                @NonNls final String setter = "set" + capitalizedName;
                if (AssignmentUtil.isPreIncrementedOrDecremented(exp)) {
                    usages.add(
                            new ReplaceInstanceVariablePreIncrementDecrement(exp, delegateFieldName, setter, getter));
                    fieldsRequiringSetters.add(field);
                } else if (AssignmentUtil.isPostIncrementedOrDecremented(exp)) {
                    usages.add(
                            new ReplaceInstanceVariablePostIncrementDecrement(exp, delegateFieldName, setter, getter));
                    fieldsRequiringSetters.add(field);
                } else if (RefactoringUtil.isAssignmentLHS(exp)) {
                    final PsiAssignmentExpression assignment =
                            PsiTreeUtil.getParentOfType(exp, PsiAssignmentExpression.class);
                    usages.add(new ReplaceInstanceVariableAssignment(assignment, delegateFieldName, setter, getter));
                    fieldsRequiringSetters.add(field);
                } else {
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
            final boolean getterRequired = fieldsRequiringGetters.contains(field);
            final boolean setterRequired = fieldsRequiringSetters.contains(field);
            extractedClassBuilder.addField(field, getterRequired, setterRequired);
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
        } catch (IOException e) {
            logger.error(e);
            return;
        }

        try {
          final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
            final PsiFile containingFile = sourceClass.getContainingFile();

            final PsiDirectory containingDirectory =
                    containingFile.getContainingDirectory();
            final Module module =
                    ModuleUtil.findModuleForPsiElement(containingFile);
            final PsiDirectory directory =
                    PackageUtil.findOrCreateDirectoryForPackage(module, newPackageName, containingDirectory, true);
            if (directory != null) {
                final PsiFile newFile =
                        PsiFileFactory.getInstance(project).createFileFromText(newClassName + ".java", classString);
                final PsiElement addedFile = directory.add(newFile);
                final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
                final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedFile);
                codeStyleManager.reformat(shortenedFile);
            }
        } catch (IncorrectOperationException e) {
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

    private class BackpointerUsageVisitor extends JavaRecursiveElementVisitor {
        private boolean backpointerRequired = false;

        public void visitElement(PsiElement element) {
            if (backpointerRequired) {
                return;
            }
            super.visitElement(element);
        }

        public void visitReferenceExpression(PsiReferenceExpression expression) {
            if (backpointerRequired) {
                return;
            }
            super.visitReferenceExpression(expression);
            final PsiExpression qualifier = expression.getQualifierExpression();

            final PsiElement referent = expression.resolve();
            if (!(referent instanceof PsiField)) {
                return;
            }
            final PsiField field = (PsiField) referent;
            if (fields.contains(field) || innerClasses.contains(field.getContainingClass())) {
                return;
            }
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
                backpointerRequired = true;
            }
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            if (backpointerRequired) {
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (methods.contains(method) || innerClasses.contains(containingClass)) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (!containingClass.equals(sourceClass)) {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
                backpointerRequired = true;
            }
        }

        public boolean isBackpointerRequired() {
            return backpointerRequired;
        }
    }

    private class NecessaryAccessorsVisitor extends JavaRecursiveElementVisitor {
        private final Set<PsiMethod> methodsNeedingPublic = new HashSet<PsiMethod>();
        private final Set<PsiField> fieldsNeedingGetter = new HashSet<PsiField>();
        private final Set<PsiField> fieldsNeedingSetter = new HashSet<PsiField>();

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiMethod method = expression.resolveMethod();
            if (method != null && method.getContainingClass().equals(sourceClass) &&
                    !method.hasModifierProperty(PsiModifier.PUBLIC)) {
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
            final PsiExpression operand = expression.getOperand();
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            if (isBackpointerReference(operand)) {
                fieldsNeedingSetter.add(getReferencedField(operand));
            }
        }

        public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            final PsiExpression operand = expression.getOperand();
            final PsiJavaToken sign = expression.getOperationSign();
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
            if (expression == null) {
                return false;
            }
            if (expression instanceof PsiParenthesizedExpression) {
                final PsiExpression contents = ((PsiParenthesizedExpression) expression).getExpression();
                return isBackpointerReference(contents);
            }
            if (!(expression instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression reference = (PsiReferenceExpression) expression;
            final PsiElement qualifier = reference.getQualifier();
            if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
                return false;
            }
            final PsiElement referent = reference.resolve();
            if (!(referent instanceof PsiField)) {
                return false;
            }
            final PsiField referentField = (PsiField) referent;
            if (fields.contains(referentField)) {
                return false;
            }
            if (innerClasses.contains(referentField.getContainingClass())) {
                return false;
            }
            return true;
        }

        private PsiField getReferencedField(PsiExpression expression) {
            if (expression instanceof PsiParenthesizedExpression) {
                final PsiExpression contents = ((PsiParenthesizedExpression) expression).getExpression();
                return getReferencedField(contents);
            }
            final PsiReferenceExpression reference = (PsiReferenceExpression) expression;
            return (PsiField) reference.resolve();
        }
    }
}
