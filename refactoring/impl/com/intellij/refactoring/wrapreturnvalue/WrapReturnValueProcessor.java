package com.intellij.refactoring.wrapreturnvalue;

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
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.MethodInheritanceUtils;
import com.intellij.refactoring.psi.PropertyUtils;
import com.intellij.refactoring.psi.SearchUtils;
import com.intellij.refactoring.psi.TypeParametersVisitor;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

class WrapReturnValueProcessor extends FixableUsagesRefactoringProcessor {
    @NonNls
    private static final Map<String, String> specialNames = new HashMap<String, String>();

    static
    {
       specialNames.put("java.lang.Integer", "intValue");
       specialNames.put("java.lang.Double", "doubleValue");
       specialNames.put("java.lang.Character", "charValue");
       specialNames.put("java.lang.Float", "floatValue");
       specialNames.put("java.lang.Long", "longValue");
       specialNames.put("java.lang.Boolean", "booleanValue");
       specialNames.put("java.lang.Byte", "byteValue");
       specialNames.put("java.lang.Short", "shortValue");
    }
    private static final Logger logger =
            Logger.getInstance("com.siyeh.rpp.wrapreturnvalue.WrapReturnValueProcessor");

    private final PsiMethod method;
    private final String className;
    private final String packageName;
    private final PsiClass existingClass;
    private final List<PsiTypeParameter> typeParams;
    @NonNls
    private final String unwrapMethodName;

    WrapReturnValueProcessor(String className,
                             String packageName,
                             PsiMethod method,
                             boolean previewUsages, PsiClass existingClass){
        super(method.getProject());
        this.method = method;
        this.className = className;
        this.packageName = packageName;
        this.existingClass = existingClass;

        final Set<PsiTypeParameter> typeParamSet = new HashSet<PsiTypeParameter>();
        final TypeParametersVisitor visitor = new TypeParametersVisitor(typeParamSet);
        final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        assert returnTypeElement != null;
        returnTypeElement.accept(visitor);
        typeParams = new ArrayList<PsiTypeParameter>(typeParamSet);
        if(existingClass != null){
            unwrapMethodName = calculateUnwrapMethodName();
        } else{
            unwrapMethodName = "getValue";
        }
    }

    private String calculateUnwrapMethodName(){
        final String existingClassName = existingClass.getQualifiedName();
        if(specialNames.containsKey(existingClassName))
        {
            return specialNames.get(existingClassName);
        }

        PsiField instanceField = null;
        final PsiField[] fields = existingClass.getFields();
        for(PsiField field : fields){
            if(!field.hasModifierProperty(PsiModifier.STATIC)){
                instanceField = field;
                break;
            }
        }
        final PsiMethod getter = PropertyUtils.findGetterForField(instanceField);
        return getter.getName();
    }

    protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usageInfos){
        return new WrapReturnValueUsageViewDescriptor(method, usageInfos);
    }

    public void findUsages(@NotNull List<FixableUsageInfo> usages){
        final Set<PsiMethod> siblingMethods = MethodInheritanceUtils.calculateSiblingMethods(method);
        for(PsiMethod siblingMethod : siblingMethods){
            findUsagesForMethod(siblingMethod, usages);
        }
    }

    private void findUsagesForMethod(PsiMethod siblingMethod, List<FixableUsageInfo> usages){
        final SearchScope scope = siblingMethod.getUseScope();
        final Iterable<PsiReference> calls = SearchUtils.findAllReferences(siblingMethod, scope);
        for(PsiReference reference : calls){
            final PsiElement referenceElement = reference.getElement();
            final PsiElement parent = referenceElement.getParent();
            if(parent instanceof PsiCallExpression){
                usages.add(new UnwrapCall((PsiCallExpression) parent, unwrapMethodName));
            }
        }
        final String returnType = calculateReturnTypeString();
        usages.add(new ChangeReturnType(siblingMethod, returnType));
        final ReturnSearchVisitor visitor = new ReturnSearchVisitor(usages, returnType);
        siblingMethod.accept(visitor);
    }

    private String calculateReturnTypeString(){
        final String qualifiedName = StringUtil.getQualifiedName(packageName, className);
        final StringBuilder returnTypeBuffer = new StringBuilder(qualifiedName);
        if(!typeParams.isEmpty()){
            returnTypeBuffer.append('<');
            boolean first = true;
            for(PsiTypeParameter typeParameter : typeParams){
                if(!first){
                    returnTypeBuffer.append(',');
                }
                first = false;
                final String typeParamName = typeParameter.getName();
                returnTypeBuffer.append(typeParamName);
            }
            returnTypeBuffer.append('>');
        }
        return returnTypeBuffer.toString();
    }

    protected void performRefactoring(UsageInfo[] usageInfos){
        if(existingClass == null){
            buildClass();
        }
        super.performRefactoring(usageInfos);
    }

    private void buildClass(){

        final PsiManager manager = method.getManager();
        final Project project = method.getProject();
        final ReturnValueBeanBuilder beanClassBuilder = new ReturnValueBeanBuilder();
        final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
        final CodeStyleSettings settings = settingsManager.getCurrentSettings();
        beanClassBuilder.setCodeStyleSettings(settings);
        beanClassBuilder.setTypeArguments(typeParams);
        beanClassBuilder.setClassName(className);
        beanClassBuilder.setPackageName(packageName);
        final PsiType returnType = method.getReturnType();
        beanClassBuilder.setValueType(returnType);

        final String classString;
        try{
            classString = beanClassBuilder.buildBeanClass();
        } catch(IOException e){
            logger.error(e);
            return;
        }

        try{
          final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
            final PsiFile containingFile = method.getContainingFile();

            final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
            final Module module = ModuleUtil.findModuleForPsiElement(containingFile);
            final PsiDirectory directory =
                    PackageUtil.findOrCreateDirectoryForPackage(module, packageName, containingDirectory, true);

            if(directory != null){
                final PsiFile newFile = PsiFileFactory.getInstance(project).createFileFromText(className + ".java", classString);

                final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
                final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(project).shortenClassReferences(newFile);
                final PsiElement reformattedFile = codeStyleManager.reformat(shortenedFile);
                directory.add(reformattedFile);
            }
        } catch(IncorrectOperationException e){
            logger.error(e);
        }
    }

    protected String getCommandName(){
        final PsiClass containingClass = method.getContainingClass();
        return RefactorJBundle
                .message("wrapped.return.command.name", className, containingClass.getName(), '.', method.getName());
    }

    @SuppressWarnings({"AssignmentToCollectionOrArrayFieldFromParameter"})
    private class ReturnSearchVisitor extends JavaRecursiveElementVisitor{
        private final List<FixableUsageInfo> usages;
        private final String type;

        ReturnSearchVisitor(List<FixableUsageInfo> usages, String type){
            super();
            this.usages = usages;
            this.type = type;
        }

        public void visitReturnStatement(PsiReturnStatement statement){
            super.visitReturnStatement(statement);
            final PsiExpression returnValue = statement.getReturnValue();
            if(existingClass != null && returnValue instanceof PsiMethodCallExpression){
                final PsiMethodCallExpression callExpression = (PsiMethodCallExpression) returnValue;
                if(callExpression.getArgumentList().getExpressions().length == 0){
                    final PsiReferenceExpression callMethodExpression = callExpression.getMethodExpression();
                    final String methodName = callMethodExpression.getReferenceName();
                    if(unwrapMethodName.equals(methodName)){
                        final PsiExpression qualifier = callMethodExpression.getQualifierExpression();
                        if(qualifier != null){
                            final PsiType qualifierType = qualifier.getType();
                            if(qualifierType!=null && qualifierType.getCanonicalText().equals(existingClass.getQualifiedName())){
                                usages.add(new ReturnWrappedValue(statement));
                                return;
                            }
                        }
                    }
                }
            }
            usages.add(new WrapReturnValue(statement, type));
        }
    }
}
