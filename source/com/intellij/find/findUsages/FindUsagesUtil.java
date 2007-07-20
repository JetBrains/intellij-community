
package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.meta.PsiMetaBaseOwner;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class FindUsagesUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findUsages.FindUsagesUtil");

  public static void processUsages(final PsiElement element, final Processor<UsageInfo> processor, final FindUsagesOptions options) {
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
              boolean isWrite = element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element);
              if (isWrite == options.isWriteAccess) {
                if (!processor.process(info)) return false;
              }
              return true;
            }
          }, options);
        }
      }
    }
    else if (options.isUsages) {
      addElementUsages(element, processor, options);
    }

    if (ThrowSearchUtil.isSearchable (element) && options.isThrowUsages) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          ThrowSearchUtil.addThrowUsages(processor, options.myThrowRoot, options);
        }
      });
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
      if (((PsiClass)element).isInterface()) {
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
      else if (options.isDerivedClasses) {
        addInheritors((PsiClass)element, processor, options);
      }
    }

    if (element instanceof PsiMethod){
      PsiSearchHelper searchHelper = element.getManager().getSearchHelper();
      final PsiMethod psiMethod = (PsiMethod)element;
      if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        if (options.isImplementingMethods){
          processOverridingMethods(psiMethod, processor, searchHelper, options);
        }
      }
      else if (options.isOverridingMethods){
          processOverridingMethods(psiMethod, processor, searchHelper, options);
        }
    }

    if (options.isSearchForTextOccurences && options.searchScope instanceof GlobalSearchScope) {
      String stringToSearch = getStringToSearch(element);
      if (stringToSearch != null) {
        final TextRange elementTextRange = ApplicationManager.getApplication().runReadAction(new Computable<TextRange>() {
          public TextRange compute() {
            return element.getTextRange();
          }
        });
        RefactoringUtil.UsageInfoFactory factory = new RefactoringUtil.UsageInfoFactory() {
          public UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
            if (elementTextRange != null
                && usage.getContainingFile() == element.getContainingFile()
                && elementTextRange.contains(startOffset)
                && elementTextRange.contains(endOffset)) {
              return null;
            }
            return new UsageInfo(usage, startOffset, endOffset, true);
          }
        };
        RefactoringUtil.processTextOccurences(element, stringToSearch, (GlobalSearchScope)options.searchScope, processor, factory);
      }
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

  private static String getStringToSearch(final PsiElement element) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        PsiElement norm = element;
        if (element instanceof PsiDirectory) {  // normalize a directory to a corresponding package
          norm = ((PsiDirectory)element).getPackage();
        }
        if (norm instanceof PsiPackage) {
          return ((PsiPackage)norm).getQualifiedName();
        }
        if (norm instanceof PsiClass) {
          return ((PsiClass)norm).getQualifiedName();
        }
        if (norm instanceof PsiMethod) {
          return ((PsiMethod)norm).getName();
        }
        if (norm instanceof PsiVariable) {
          return ((PsiVariable)norm).getName();
        }
        if (norm instanceof PsiMetaBaseOwner) {
          final PsiMetaDataBase metaData = ((PsiMetaBaseOwner)norm).getMetaData();
          if (metaData != null) {
            return metaData.getName();
          }
        }
        if (norm instanceof PsiNamedElement) {
          return ((PsiNamedElement)norm).getName();
        }
        if (norm instanceof XmlAttributeValue) {
          return ((XmlAttributeValue)norm).getValue();
        }

        LOG.error("Unknown element type: " + element);
        return null;
      }
    });
  }

  private static void addElementUsages(final PsiElement element, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    if (element instanceof PsiMethod){
      addMethodUsages((PsiMethod)element, results, options, options.searchScope);
    }
    else {
      ReferencesSearch.search(element, options.searchScope, false).forEach(new ReadActionProcessor<PsiReference>() {
        public boolean processInReadAction(final PsiReference ref) {
          return addResult(results, ref, options, element);
        }
      });
    }
  }

  private static void addMethodUsages(final PsiMethod method, final Processor<UsageInfo> result, final FindUsagesOptions options, SearchScope searchScope) {
    PsiSearchHelper helper = method.getManager().getSearchHelper();
    if (method.isConstructor()) {
      if (options.isIncludeOverloadUsages) {
        for (PsiMethod constructor : method.getContainingClass().getConstructors()) {
          addConstructorUsages(constructor, searchScope, result, options);
        }
      }
      else {
        addConstructorUsages(method, searchScope, result, options);
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

  private static void addConstructorUsages(PsiMethod method, SearchScope searchScope, final Processor<UsageInfo> result, final FindUsagesOptions options) {
    final PsiClass parentClass = method.getContainingClass();
    if (parentClass == null) return;

    ReferencesSearch.search(method, searchScope, false).forEach(new ReadActionProcessor<PsiReference>() {
      public boolean processInReadAction(final PsiReference ref) {
        return addResult(result, ref, options, parentClass);
      }
    });

    //addImplicitConstructorCalls(method, result, searchScope);
  }

  private static void addClassesUsages(PsiPackage aPackage, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    final HashSet<PsiFile> filesSet = new HashSet<PsiFile>();
    final ArrayList<PsiFile> files = new ArrayList<PsiFile>();
    ReferencesSearch.search(aPackage, options.searchScope, false).forEach(new ReadActionProcessor<PsiReference>() {
      public boolean processInReadAction(final PsiReference psiReference) {
        PsiElement ref = psiReference.getElement();
        PsiFile file = ref.getContainingFile();
        if (filesSet.add(file)) {
          files.add(file);
        }
        return true;
      }
    });

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null){
      progress.pushState();
    }

    ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
    addClassesInPackage(aPackage, options.isIncludeSubpackages, classes);
    for (final PsiClass aClass : classes) {
      if (progress != null) {
        progress.setText(FindBundle.message("find.searching.for.references.to.class.progress", aClass.getName()));
      }
      for (PsiFile file : files) {
        ProgressManager.getInstance().checkCanceled();
        ReferencesSearch.search(aClass, new LocalSearchScope(file), false).forEach(new ReadActionProcessor<PsiReference>() {
          public boolean processInReadAction(final PsiReference psiReference) {
            return addResult(results, psiReference, options, aClass);
          }
        });
      }
    }

    if (progress != null){
      progress.popState();
    }
  }

  private static void addClassesInPackage(PsiPackage aPackage, boolean includeSubpackages, ArrayList<PsiClass> array) {
    PsiDirectory[] dirs = aPackage.getDirectories();
    for (PsiDirectory dir : dirs) {
      addClassesInDirectory(dir, includeSubpackages, array);
    }
  }

  private static void addClassesInDirectory(final PsiDirectory dir, final boolean includeSubdirs, final ArrayList<PsiClass> array) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        PsiClass[] classes = dir.getClasses();
        array.addAll(Arrays.asList(classes));
        if (includeSubdirs) {
          PsiDirectory[] dirs = dir.getSubdirectories();
          for (PsiDirectory directory : dirs) {
            addClassesInDirectory(directory, includeSubdirs, array);
          }
        }
      }
    });
  }

  private static void addMethodsUsages(final PsiClass aClass, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    if (options.isIncludeInherited) {
      final PsiManager manager = aClass.getManager();
      PsiSearchHelper helper = manager.getSearchHelper();
      PsiMethod[] methods = aClass.getAllMethods();
      MethodsLoop:
        for(int i = 0; i < methods.length; i++){
          final PsiMethod method = methods[i];
          // filter overriden methods
          MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
          for(int j = 0; j < i; j++){
            if (methodSignature.equals(methods[j].getSignature(PsiSubstitutor.EMPTY))) continue MethodsLoop;
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
    else {
      PsiMethod[] methods = aClass.getMethods();
      for (PsiMethod method : methods) {
        addMethodUsages(method, results, options, options.searchScope);
      }
    }
  }

  private static void addFieldsUsages(final PsiClass aClass, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    if (options.isIncludeInherited) {
      final PsiManager manager = aClass.getManager();
      PsiField[] fields = aClass.getAllFields();
      FieldsLoop:
      for (int i = 0; i < fields.length; i++) {
        final PsiField field = fields[i];
        // filter hidden fields
        for (int j = 0; j < i; j++) {
          if (field.getName().equals(fields[j].getName())) continue FieldsLoop;
        }
        final PsiClass fieldClass = field.getContainingClass();
        if (manager.areElementsEquivalent(fieldClass, aClass)) {
          addElementUsages(fields[i], results, options);
        }
        else {
          ReferencesSearch.search(field, options.searchScope, false).forEach(new ReadActionProcessor<PsiReference>() {
            public boolean processInReadAction(final PsiReference reference) {
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
          });
        }
      }
    }
    else {
      PsiField[] fields = ApplicationManager.getApplication().runReadAction(new Computable<PsiField[]>() {
        public PsiField[] compute() {
          return aClass.getFields();
        }
      });
      for (PsiField field : fields) {
        addElementUsages(field, results, options);
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
    if (!(usage instanceof PsiJavaCodeReferenceElement)) {
      return true;
    }
    if (refElement instanceof PsiPackage && !options.isIncludeSubpackages &&
        ((PsiJavaCodeReferenceElement)usage).resolve() instanceof PsiPackage) {
      PsiElement parent = usage.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)parent).resolve() instanceof PsiPackage) {
        return false;
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
    return true;
  }

  static boolean isSearchForTextOccurencesAvailable(PsiElement psiElement, boolean isSingleFile) {
    if (isSingleFile) return false;
    if (psiElement instanceof PsiClass) {
      return ((PsiClass)psiElement).getQualifiedName() != null;
    }
    return psiElement instanceof PsiPackage;
  }
}
