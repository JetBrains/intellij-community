
package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.search.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class FindUsagesUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findUsages.FindUsagesUtil");

  public static void processUsages(PsiElement element, final Processor<UsageInfo> processor, final FindUsagesOptions options) {
    if (LOG.isDebugEnabled()){
      LOG.debug("findUsages: element = " + element + ", options = " + options);
    }

    if (element instanceof PsiVariable){
      if (options.isReadAccess || options.isWriteAccess){
        if (options.isReadAccess && options.isWriteAccess){
          //todo[myakovlev] this also shows param in javadoc (PsiDocParamRef), but should not
          addElementUsages(element, processor, options);
        }
        else{
          addElementUsages(element, new Processor<UsageInfo>() {
            public boolean process(UsageInfo info) {
              final PsiElement element = info.getElement();
              boolean isWrite = (element instanceof PsiExpression)? PsiUtil.isAccessedForWriting(((PsiExpression)element)) : false;
              if (isWrite == options.isWriteAccess) {
                if (!processor.process(info)) return false;
              }
              return true;
            }
          }, options);
        }
      }
    }
    else{
      if (options.isUsages){
        addElementUsages(element, processor, options);
      }
    }

    if (ThrowSearchUtil.isSearchable (element) && options.isThrowUsages) {
      ThrowSearchUtil.addThrowUsages(processor, options.myThrowRoot, options);
    }

    if (element instanceof PsiPackage && options.isClassesUsages){
      addClassesUsages((PsiPackage)element, processor, options);
    }

    if (element instanceof PsiClass && options.isMethodsUsages){
      addMethodsUsages((PsiClass)element, processor, options);
    }

    if (element instanceof PsiClass && options.isFieldsUsages){
      addFieldsUsages((PsiClass)element, processor, options);
    }

    if (element instanceof PsiClass){
      if (!((PsiClass)element).isInterface()){
        if (options.isDerivedClasses){
          addInheritors((PsiClass)element, processor, options);
        }
      }
      else{
        if (options.isDerivedInterfaces){
          if (options.isImplementingClasses){
            addInheritors((PsiClass)element, processor, options);
          }
          else{
            addDerivedInterfaces((PsiClass)element, processor, options);
          }
        }
        else if (options.isImplementingClasses){
          addImplementingClasses((PsiClass)element, processor, options);
        }
      }
    }

    if (element instanceof PsiMethod){
      PsiSearchHelper searchHelper = element.getManager().getSearchHelper();
      final PsiMethod psiMethod = (PsiMethod)element;
      if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)){
        if (options.isOverridingMethods){
          processOverridingMethods(psiMethod, processor, searchHelper, options);
        }
      }
      else{
        if (options.isImplementingMethods){
          processOverridingMethods(psiMethod, processor, searchHelper, options);
        }
      }
    }

    if (options.isSearchForTextOccurences && options.searchScope instanceof GlobalSearchScope) {
      String stringToSearch = getStringToSearch(element);
      RefactoringUtil.UsageInfoFactory factory = new RefactoringUtil.UsageInfoFactory() {
        public UsageInfo createUsageInfo(PsiElement usage, int startOffset, int endOffset) {
          return new UsageInfo(usage, startOffset, endOffset, true);
        }
      };
      RefactoringUtil.processTextOccurences(element, stringToSearch, (GlobalSearchScope)options.searchScope, processor, factory);
    }
  }

  private static void processOverridingMethods(final PsiMethod psiMethod,
                                               final Processor<UsageInfo> processor,
                                               PsiSearchHelper searchHelper, final FindUsagesOptions options) {
    searchHelper.processOverridingMethods(new PsiElementProcessor<PsiMethod>() {
      public boolean execute(PsiMethod element) {
        addResult(processor, element.getNavigationElement(), options, null);
        return true;
      }

    }, psiMethod, options.searchScope, options.isCheckDeepInheritance);
  }

  /**
   * @deprecated use processUsages instead. 
   * @param element
   * @param options
   * @return
   */
  public static UsageInfo[] findUsages(PsiElement element, final FindUsagesOptions options) {
    final List<UsageInfo> results = new ArrayList<UsageInfo>();
    processUsages(element, new Processor<UsageInfo>() {
      public boolean process(UsageInfo t) {
        results.add(t);
        return true;
      }
    }, options);
    return results.toArray(new UsageInfo[results.size()]);
  }

  private static String getStringToSearch(PsiElement element) {
    if (element instanceof PsiDirectory) {  // normalize a directory to a corresponding package
      element = ((PsiDirectory)element).getPackage();
    }
    if (element instanceof PsiPackage){
      return ((PsiPackage)element).getQualifiedName();
    }
    if (element instanceof PsiClass){
      return ((PsiClass)element).getQualifiedName();
    }
    if (element instanceof PsiMethod){
      return ((PsiMethod)element).getName();
    }
    if (element instanceof PsiVariable){
      return ((PsiVariable)element).getName();
    }
    if (element instanceof PsiMetaOwner){
      final PsiMetaData metaData = ((PsiMetaOwner)element).getMetaData();
      if (metaData != null) {
        return metaData.getName();
      }
    }

    LOG.error("Unknown element type: "+element);
    return null;
  }

  private static void addElementUsages(final PsiElement element, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    if (element instanceof PsiMethod){
      addMethodUsages((PsiMethod)element, results, options, options.searchScope);
    }
    else{
      PsiSearchHelper helper = element.getManager().getSearchHelper();
      helper.processReferences(new PsiReferenceProcessor() {
        public boolean execute(PsiReference ref) {
          return addResult(results, ref, options, element);
        }
      }, element, options.searchScope, false);
    }
  }

  private static void addMethodUsages(final PsiMethod method, final Processor<UsageInfo> result, final FindUsagesOptions options, SearchScope searchScope) {
    PsiSearchHelper helper = method.getManager().getSearchHelper();
    if (method.isConstructor()) {
      if (!options.isIncludeOverloadUsages) {
        addConstructorUsages(method, helper, searchScope, result, options);
      } else {
        for (PsiMethod constructor : method.getContainingClass().getConstructors()) {
          addConstructorUsages(constructor, helper, searchScope, result, options);
        }
      }
    }
    else {
      helper.processReferencesIncludingOverriding(new PsiReferenceProcessor() {
        public boolean execute(PsiReference ref) {
          return addResult(result, ref, options, method);
        }
      }, method, searchScope, !options.isIncludeOverloadUsages);
    }
  }

  private static void addConstructorUsages(PsiMethod method, PsiSearchHelper helper, SearchScope searchScope, final Processor<UsageInfo> result, final FindUsagesOptions options) {
    final PsiClass parentClass = method.getContainingClass();
    if (parentClass == null) return;

    helper.processReferences(new PsiReferenceProcessor() {
      public boolean execute(PsiReference ref) {
        return addResult(result, ref, options, parentClass);
      }
    }, method, searchScope, false);

    addImplicitConstructorCalls(method, result, searchScope);
  }

  private static void addImplicitConstructorCalls(final PsiMethod constructor, final Processor<UsageInfo> result, SearchScope searchScope){
    if (constructor.getParameterList().getParameters().length > 0) return;
    if (constructor.hasModifierProperty(PsiModifier.PRIVATE)) return;

    PsiClass parentClass = constructor.getContainingClass();

    final PsiManager manager = constructor.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();
    helper.processInheritors(new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        PsiClass inheritor = element;
        if (inheritor instanceof PsiAnonymousClass){
          PsiMethod constructor1 = null;
          PsiElement parent = inheritor.getParent();
          if (parent instanceof PsiConstructorCall) {
            constructor1 = ((PsiConstructorCall)parent).resolveConstructor();
          }

          if (constructor1 != null && manager.areElementsEquivalent(constructor, constructor1)){
            result.process(new UsageInfo(inheritor));
          }
        }
        else{
          PsiMethod[] constructors = inheritor.getConstructors();
          if (constructors.length == 0){
            result.process(new UsageInfo(inheritor)); // implicit default constructor
          }
          else{
            for(int j = 0; j < constructors.length; j++){
              PsiMethod superConstructor = constructors[j];
              PsiCodeBlock body = superConstructor.getBody();
              if (body == null) continue;
              PsiStatement[] statements = body.getStatements();
              if (statements.length > 0){
                PsiStatement firstStatement = statements[0];
                if (firstStatement instanceof PsiExpressionStatement){
                  PsiExpression expr = ((PsiExpressionStatement)firstStatement).getExpression();
                  if (expr instanceof PsiMethodCallExpression){
                    PsiReferenceExpression methodExpr = ((PsiMethodCallExpression)expr).getMethodExpression();
                    if (methodExpr.getText().equals(PsiKeyword.SUPER) ||
                        methodExpr.getText().equals(PsiKeyword.THIS)) continue;
                  }
                }
              }
              result.process(new UsageInfo(superConstructor));
            }
          }
        }
        return true;
      }

    }, parentClass, searchScope, false);
  }

  private static void addClassesUsages(PsiPackage aPackage, Processor<UsageInfo> results, FindUsagesOptions options) {
    PsiSearchHelper helper = aPackage.getManager().getSearchHelper();

    PsiReference[] refs = helper.findReferences(aPackage, options.searchScope, false);
    HashSet<PsiFile> filesSet = new HashSet<PsiFile>();
    ArrayList<PsiFile> files = new ArrayList<PsiFile>();
    for (PsiReference psiReference : refs) {
      PsiElement ref = psiReference.getElement();
      PsiFile file = ref.getContainingFile();
      if (filesSet.add(file)) {
        files.add(file);
      }
    }

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null){
      progress.pushState();
    }

    ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
    addClassesInPackage(aPackage, options.isIncludeSubpackages, classes);
    for(int i = 0; i < classes.size(); i++){
      PsiClass aClass = classes.get(i);
      if (progress != null){
        progress.setText(FindBundle.message("find.searching.for.references.to.class.progress", aClass.getName()));
      }
      for(int j = 0; j < files.size(); j++){
        ProgressManager.getInstance().checkCanceled();
        PsiFile file = files.get(j);
        refs = helper.findReferences(aClass, new LocalSearchScope(file), false);
        addResults(results, refs, options, aClass);
      }
    }

    if (progress != null){
      progress.popState();
    }
  }

  private static void addResults(Processor<UsageInfo> results, PsiReference[] refs, FindUsagesOptions options, PsiElement element) {
    for (PsiReference psiReference : refs) {
      final boolean shouldContinue = addResult(results, psiReference, options, element);
      if (!shouldContinue) {
        break;
      }
    }
  }

  private static void addClassesInPackage(PsiPackage aPackage, boolean includeSubpackages, ArrayList<PsiClass> array) {
    PsiDirectory[] dirs = aPackage.getDirectories();
    for (PsiDirectory dir : dirs) {
      addClassesInDirectory(dir, includeSubpackages, array);
    }
  }

  private static void addClassesInDirectory(PsiDirectory dir, boolean includeSubdirs, ArrayList<PsiClass> array) {
    PsiClass[] classes = dir.getClasses();
    for (PsiClass aClass : classes) {
      array.add(aClass);
    }
    if (includeSubdirs){
      PsiDirectory[] dirs = dir.getSubdirectories();
      for (PsiDirectory directory : dirs) {
        addClassesInDirectory(directory, includeSubdirs, array);
      }
    }
  }

  public static void addMethodsUsages(final PsiClass aClass, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    if (!options.isIncludeInherited){
      PsiMethod[] methods = aClass.getMethods();
      for (PsiMethod method : methods) {
        addMethodUsages(method, results, options, options.searchScope);
      }
    }
    else{
      final PsiManager manager = aClass.getManager();
      PsiSearchHelper helper = manager.getSearchHelper();
      PsiMethod[] methods = aClass.getAllMethods();
      MethodsLoop:
        for(int i = 0; i < methods.length; i++){
          final PsiMethod method = methods[i];
          // filter overriden methods
          for(int j = 0; j < i; j++){
            if (MethodSignatureUtil.areSignaturesEqual(method, methods[j])) continue MethodsLoop;
          }
          final PsiClass methodClass = method.getContainingClass();
          if (methodClass != null && manager.areElementsEquivalent(methodClass, aClass)){
            addMethodUsages(methods[i], results, options, options.searchScope);
          }
          else{
            helper.processReferencesIncludingOverriding(new PsiReferenceProcessor() {
              public boolean execute(PsiReference reference) {
                PsiElement refElement = reference.getElement();
                if (refElement instanceof PsiReferenceExpression) {
                  PsiClass usedClass = getFieldOrMethodAccessedClass((PsiReferenceExpression)refElement, methodClass);
                  if (usedClass != null) {
                    if (manager.areElementsEquivalent(usedClass, aClass) || usedClass.isInheritor(aClass, true)) {
                      addResult(results, refElement, options, method);
                    }
                  }
                }
                return true;
              }
            }, method, options.searchScope, !options.isIncludeOverloadUsages);
          }
        }
    }
  }

  private static void addFieldsUsages(final PsiClass aClass, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    if (!options.isIncludeInherited){
      PsiField[] fields = aClass.getFields();
      for (PsiField field : fields) {
        addElementUsages(field, results, options);
      }
    }
    else{
      final PsiManager manager = aClass.getManager();
      PsiSearchHelper helper = manager.getSearchHelper();
      PsiField[] fields = aClass.getAllFields();
      FieldsLoop:
        for(int i = 0; i < fields.length; i++){
          final PsiField field = fields[i];
          // filter hidden fields
          for(int j = 0; j < i; j++){
            if (field.getName().equals(fields[j].getName())) continue FieldsLoop;
          }
          final PsiClass fieldClass = field.getContainingClass();
          if (fieldClass != null && manager.areElementsEquivalent(fieldClass, aClass)){
            addElementUsages(fields[i], results, options);
          }
          else{
            helper.processReferences(new PsiReferenceProcessor() {
              public boolean execute(PsiReference reference) {
                PsiElement refElement = reference.getElement();
                if (refElement instanceof PsiReferenceExpression) {
                  PsiClass usedClass = getFieldOrMethodAccessedClass((PsiReferenceExpression)refElement, fieldClass);
                  if (usedClass != null) {
                    if (manager.areElementsEquivalent(usedClass, aClass) || usedClass.isInheritor(aClass, true)) {
                      addResult(results, refElement, options, field);
                    }
                  }
                }
                return true;
              }
            }, field, options.searchScope, false);
          }
        }
    }
  }

  private static PsiClass getFieldOrMethodAccessedClass(PsiReferenceExpression ref, PsiClass fieldOrMethodClass) {
    PsiElement[] children = ref.getChildren();
    if (children.length > 1 && children[0] instanceof PsiExpression){
      PsiExpression expr = (PsiExpression)children[0];
      PsiType type = expr.getType();
      if (type != null){
        if (!(type instanceof PsiClassType)) return null;
        return PsiUtil.resolveClassInType(type);
      }
      else{
        if (expr instanceof PsiReferenceExpression){
          PsiElement refElement = ((PsiReferenceExpression)expr).resolve();
          if (refElement instanceof PsiClass) return (PsiClass)refElement;
        }
        return null;
      }
    }
    else{
      PsiManager manager = ref.getManager();
      for(PsiElement parent = ref; parent != null; parent = parent.getParent()){
        if (parent instanceof PsiClass
          && (manager.areElementsEquivalent(parent, fieldOrMethodClass) || ((PsiClass)parent).isInheritor(fieldOrMethodClass, true))){
          return (PsiClass)parent;
        }
      }
    }
    return null;
  }

  private static void addInheritors(final PsiClass aClass, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    PsiSearchHelper helper = aClass.getManager().getSearchHelper();
    helper.processInheritors(new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        addResult(results, element, options, null);
        return true;
      }

    }, aClass, options.searchScope, options.isCheckDeepInheritance);
  }

  private static void addDerivedInterfaces(PsiClass anInterface, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    PsiSearchHelper helper = anInterface.getManager().getSearchHelper();
    helper.processInheritors(new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass inheritor) {
        if (inheritor.isInterface()) {
          addResult(results, inheritor, options, null);
        }
        return true;
      }

    }, anInterface, options.searchScope, options.isCheckDeepInheritance);
  }

  private static void addImplementingClasses(PsiClass anInterface, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    PsiSearchHelper helper = anInterface.getManager().getSearchHelper();
    helper.processInheritors(new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass inheritor) {
        if (!inheritor.isInterface()) {
          addResult(results, inheritor, options, null);
        }
        return true;
      }

    }, anInterface, options.searchScope, options.isCheckDeepInheritance);
  }

  private static void addResults(Processor<UsageInfo> total, PsiElement[] part, FindUsagesOptions options, PsiElement refElement) {
    for (PsiElement element : part) {
      addResult(total, element, options, refElement);
    }
  }

  private static void addResult(Processor<UsageInfo> total, PsiElement element, FindUsagesOptions options, PsiElement refElement) {
    if (filterUsage(element, options, refElement)){
      total.process(new UsageInfo(element));
    }
  }

  private static boolean addResult(Processor<UsageInfo> results, PsiReference ref, FindUsagesOptions options, PsiElement refElement) {
    if (filterUsage(ref.getElement(), options, refElement)){
      TextRange rangeInElement = ref.getRangeInElement();
      return results.process(new UsageInfo(ref.getElement(), rangeInElement.getStartOffset(), rangeInElement.getEndOffset(), false));
    }
    return true;
  }

  private static boolean filterUsage(PsiElement usage, FindUsagesOptions options, PsiElement refElement) {
    if (usage instanceof PsiJavaCodeReferenceElement){
      if (refElement instanceof PsiPackage && !options.isIncludeSubpackages){
        if (((PsiJavaCodeReferenceElement)usage).resolve() instanceof PsiPackage){
          PsiElement parent = usage.getParent();
          if (parent instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)parent).resolve() instanceof PsiPackage){
            return false;
          }
        }
      }

      if (!(usage instanceof PsiReferenceExpression)){
        if (options.isSkipImportStatements){
          PsiElement parent = usage.getParent();
          while(parent instanceof PsiJavaCodeReferenceElement){
            parent = parent.getParent();
          }
          if (parent instanceof PsiImportStatement){
            return false;
          }
        }

        if (options.isSkipPackageStatements){
          PsiElement parent = usage.getParent();
          while(parent instanceof PsiJavaCodeReferenceElement){
            parent = parent.getParent();
          }
          if (parent instanceof PsiPackageStatement){
            return false;
          }
        }
      }
    }
    return true;
  }
}
