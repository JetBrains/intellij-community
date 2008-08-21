package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactorJHelpID;
import com.intellij.refactoring.base.BaseRefactoringHandler;
import com.intellij.refactoring.psi.MethodInheritanceUtils;
import com.intellij.refactoring.psi.PropertyUtils;
import com.intellij.refactoring.ui.ClassUtil;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class WrapReturnValueHandler extends BaseRefactoringHandler{
    private static final String REFACTORING_NAME = RefactorJBundle.message("wrap.return.value");

    public void invoke(Project project,
                       Editor editor,
                       PsiFile file,
                       DataContext dataContext){
        final ScrollingModel scrollingModel = editor.getScrollingModel();
        scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
        final PsiElement element = (PsiElement) dataContext.getData(DataConstants.PSI_ELEMENT);
        PsiMethod selectedMethod = null;
        if(element instanceof PsiMethod){
            selectedMethod = (PsiMethod) element;
        } else{
            final CaretModel caretModel = editor.getCaretModel();
            final int position = caretModel.getOffset();
            PsiElement selectedElement = file.findElementAt(position);
            while(selectedElement != null){
                if(selectedElement instanceof PsiMethod){
                    selectedMethod = (PsiMethod) selectedElement;
                    break;
                }
                selectedElement = selectedElement.getParent();
            }
        }
        if(selectedMethod == null){
            showErrorMessage(RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle
                            .message("the.caret.should.be.positioned.at.the.name.of.the.method.to.be.refactored"),
                    project);
            return;
        }
        if(selectedMethod.isConstructor()){
            showErrorMessage(RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("constructor.returns.can.not.be.wrapped"), project);
            return;
        }
        final PsiType returnType = selectedMethod.getReturnType();
        if(PsiType.VOID.equals(returnType)){
            showErrorMessage(RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("method.selected.returns.void"), project);
            return;
        }
        final Set<PsiMethod> siblingMethods = MethodInheritanceUtils.calculateSiblingMethods(selectedMethod);
        boolean hasLibrarySibling = false;
        for(PsiMethod siblingMethod : siblingMethods){
            if(siblingMethod instanceof PsiCompiledElement){
                hasLibrarySibling = true;
                break;
            }
        }
        if(hasLibrarySibling){
            showErrorMessage(RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message(
                            "the.selected.method.cannot.be.wrapped.because.it.is.defined.in.a.non.project.class"),
                    project);
            return;
        }
        invoke(selectedMethod);
    }

    protected String getRefactoringName(){
        return REFACTORING_NAME;
    }

    protected String getHelpID(){
        return RefactorJHelpID.WrapReturnValue;
    }

    public void invoke(Project project,
                       PsiElement[] elements,
                       DataContext dataContext){
        if(elements.length != 1){
            return;
        }
        final PsiMethod method =
                PsiTreeUtil.getParentOfType(elements[0], PsiMethod.class, false);
        if(method == null){
            return;
        }
        if(method.isConstructor()){
            showErrorMessage(RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("constructor.returns.can.not.be.wrapped"), project);
            return;
        }
        final PsiType returnType = method.getReturnType();
        if(PsiType.VOID.equals(returnType)){
            showErrorMessage(RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("method.selected.returns.void"), project);
            return;
        }
        final Set<PsiMethod> siblingMethods = MethodInheritanceUtils.calculateSiblingMethods(method);
        boolean hasLibrarySibling = false;
        for(PsiMethod siblingMethod : siblingMethods){
            if(siblingMethod instanceof PsiCompiledElement){
                hasLibrarySibling = true;
                break;
            }
        }
        if(hasLibrarySibling){
            showErrorMessage(RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message(
                            "the.selected.method.cannot.be.wrapped.because.it.is.defined.in.a.non.project.class"),
                    project);
            return;
        }
        invoke(method);
    }

    private void invoke(final PsiMethod method){

        final WrapReturnValueDialog dialog = new WrapReturnValueDialog(method);
        dialog.show();
        if(!dialog.isOK()){
            return;
        }
        final Project project = method.getProject();
        final PsiManager manager = method.getManager();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final boolean useExistingClass = dialog.useExistingClass();
        final String existingClassName = dialog.getExistingClassName();
        final boolean previewUsages = dialog.isPreviewUsages();

        if(useExistingClass){
          final PsiClass existingClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(existingClassName, scope);
            if(existingClass == null){
                showErrorMessage(RefactorJBundle.message("cannot.perform.the.refactoring") +
                        RefactorJBundle.message("could.not.find.selected.wrapping.class"),
                        project);
                return;
            }
            if(!classMayWrapType(existingClass, method.getReturnType())){
                showErrorMessage(RefactorJBundle.message("cannot.perform.the.refactoring") +
                        RefactorJBundle.message("there.already.exists.an.incompatible.class.with.the.chosen.name"),
                        project);
                return;
            }
            final String className = existingClass.getName();
            final String qualifiedName = existingClass.getQualifiedName();
            final String packageName = qualifiedName.substring(0, qualifiedName.length() -(className.length()+1));

            perform(project, new Runnable(){
                public void run(){
                    final WrapReturnValueProcessor processor =
                            new WrapReturnValueProcessor(
                                    className,
                                    packageName,
                                    method,
                                    previewUsages, existingClass);
                    processor.run();
                }
            });
        } else{
            final String className = dialog.getClassName();
            final String packageName = dialog.getPackageName();
            final String qualifiedName = ClassUtil.createQualifiedName(packageName, className);
          final PsiClass existingClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(qualifiedName, scope);
            if(existingClass != null){
                showErrorMessage(RefactorJBundle.message("cannot.perform.the.refactoring") +
                        RefactorJBundle.message("there.already.exists.a.class.with.the.selected.name"),
                        project);
                return;
            }
            perform(project, new Runnable(){
                public void run(){
                    final WrapReturnValueProcessor processor =
                            new WrapReturnValueProcessor(
                                    className,
                                    packageName,
                                    method,
                                    previewUsages, existingClass);
                    processor.run();
                }
            });
        }
    }

    @NonNls
    private static final Map<PsiType, String> specialNames = new HashMap<PsiType, String>();

    static{
        specialNames.put(PsiType.INT, "java.lang.Integer");
        specialNames.put(PsiType.DOUBLE, "java.lang.Double");
        specialNames.put(PsiType.CHAR, "java.lang.Character");
        specialNames.put(PsiType.FLOAT, "java.lang.Float");
        specialNames.put(PsiType.LONG, "java.lang.Long");
        specialNames.put(PsiType.BOOLEAN, "java.lang.Boolean");
        specialNames.put(PsiType.BYTE, "java.lang.Byte");
        specialNames.put(PsiType.SHORT, "java.lang.Short");
    }

    private static boolean classMayWrapType(PsiClass existingClass, PsiType returnType){
        final String existingClassName = existingClass.getQualifiedName();
        if(specialNames.containsKey(returnType)){
            if(specialNames.get(returnType).equals(existingClassName)){
                return true;
            }
        }

        PsiField instanceField = null;
        final PsiField[] fields = existingClass.getFields();
        int numInstanceFields = 0;
        for(PsiField field : fields){
            if(!field.hasModifierProperty(PsiModifier.STATIC)){
                numInstanceFields++;
                instanceField = field;
            }
        }
        if(numInstanceFields != 1){
            return false;
        }
        final PsiMethod[] constructors = existingClass.getConstructors();
        boolean foundConstructor = false;
        for(PsiMethod constructor : constructors){
            final PsiParameter[] parameters = constructor.getParameterList().getParameters();
            if(parameters.length == 1){
                final PsiParameter parameter = parameters[0];
                final PsiType parameterType = parameter.getType();
                if(parameterType.equals(returnType)){
                    foundConstructor = true;
                    break;
                }
            }
        }
        if(!foundConstructor){
            return false;
        }
        final PsiMethod getter = PropertyUtils.findGetterForField(instanceField);
        return getter != null;
    }
}
