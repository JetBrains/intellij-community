/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.completion.scope.CompletionProcessor;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
*/
public class JavaClassReference extends GenericReference implements PsiJavaReference, QuickFixProvider, LocalQuickFixProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference");
  private final int myIndex;
  private TextRange myRange;
  private final String myText;
  private final boolean myInStaticImport;
  private final JavaClassReferenceSet myJavaClassReferenceSet;
  private final Map<CustomizableReferenceProvider.CustomizationKey,Object> myOptions;

  public JavaClassReference(final JavaClassReferenceSet referenceSet, TextRange range, int index, String text, final boolean staticImport) {
    super(referenceSet.getProvider());
    myInStaticImport = staticImport;
    LOG.assertTrue(range.getEndOffset() <= referenceSet.getElement().getTextLength());
    myIndex = index;
    myRange = range;
    myText = text;
    myJavaClassReferenceSet = referenceSet;
    myOptions = myJavaClassReferenceSet.getOptions();
  }

  @Nullable
  public PsiElement getContext() {
    final PsiReference contextRef = getContextReference();
    return contextRef != null ? contextRef.resolve() : null;
  }

  public void processVariants(final PsiScopeProcessor processor) {
    if (processor instanceof CompletionProcessor && JavaClassReferenceProvider.EXTEND_CLASS_NAMES.getValue(getOptions()) != null) {
      ((CompletionProcessor)processor).setCompletionElements(getVariants());
      return;
    }

    PsiScopeProcessor processorToUse = processor;
    if (myInStaticImport) {
      // allows to complete members
      processor.handleEvent(PsiScopeProcessor.Event.CHANGE_LEVEL, null);
    }
    else {
      if (isStaticClassReference()) {
        processor.handleEvent(PsiScopeProcessor.Event.START_STATIC, null);
      }
      processorToUse = new PsiScopeProcessor() {
        public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
          return !(element instanceof PsiClass || element instanceof PsiPackage) || processor.execute(element, substitutor);
        }

        public <V> V getHint(Class<V> hintClass) {
          return processor.getHint(hintClass);
        }

        public void handleEvent(Event event, Object associated) {
          processor.handleEvent(event, associated);
        }
      };
    }
    super.processVariants(processorToUse);
  }

  private boolean isStaticClassReference() {
    final String s = getElement().getText();
    return isStaticClassReference(s);
  }

  private boolean isStaticClassReference(final String s) {
    return myIndex > 0 && s.charAt(getRangeInElement().getStartOffset() - 1) == JavaClassReferenceSet.SEPARATOR2;
  }

  @Nullable
  public PsiReference getContextReference() {
    return myIndex > 0 ? myJavaClassReferenceSet.getReference(myIndex - 1) : null;
  }

  public ReferenceType getType() {
    return myJavaClassReferenceSet.getType(myIndex);
  }

  public ReferenceType getSoftenType() {
    return new ReferenceType(ReferenceType.JAVA_CLASS, ReferenceType.JAVA_PACKAGE);
  }

  public boolean needToCheckAccessibility() {
    return false;
  }

  public PsiElement getElement() {
    return myJavaClassReferenceSet.getElement();
  }

  public boolean isReferenceTo(PsiElement element) {
    return (element instanceof PsiClass || element instanceof PsiPackage) && super.isReferenceTo(element);
  }

  public TextRange getRangeInElement() {
    return myRange;
  }

  public String getCanonicalText() {
    return myText;
  }

  public boolean isSoft() {
    return myJavaClassReferenceSet.isSoft();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    if (manipulator != null) {
      final PsiElement element = manipulator.handleContentChange(getElement(), getRangeInElement(), newElementName);
      myRange = new TextRange(getRangeInElement().getStartOffset(), getRangeInElement().getStartOffset() + newElementName.length());
      return element;
    }
    throw new IncorrectOperationException("Manipulator for this element is not defined");
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (isReferenceTo(element)) return getElement();

    final String newName;
    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)element;
      newName = psiClass.getQualifiedName();
    }
    else if (element instanceof PsiPackage) {
      PsiPackage psiPackage = (PsiPackage)element;
      newName = psiPackage.getQualifiedName();
    }
    else {
      throw new IncorrectOperationException("Cannot bind to " + element);
    }
    assert newName != null;

    TextRange range = new TextRange(myJavaClassReferenceSet.getReference(0).getRangeInElement().getStartOffset(), getRangeInElement().getEndOffset());
    final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    assert manipulator != null;
    final PsiElement finalElement = manipulator.handleContentChange(getElement(), range, newName);
    range = new TextRange(range.getStartOffset(), range.getStartOffset() + newName.length());
    myJavaClassReferenceSet.reparse(finalElement, range);
    return finalElement;
  }

  public PsiElement resolveInner() {
    return advancedResolve(true).getElement();
  }

  public Object[] getVariants() {
    PsiElement context = getContext();
    if (context == null) {
      context = getElement().getManager().findPackage("");
    }
    if (context instanceof PsiPackage) {
      final String[] extendClasses = JavaClassReferenceProvider.EXTEND_CLASS_NAMES.getValue(getOptions());
      if (extendClasses != null) {
        return getSubclassVariants((PsiPackage)context, extendClasses);
      }
      return processPackage((PsiPackage)context);
    }
    if (context instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)context;

      if (myInStaticImport) {
        return ArrayUtil.mergeArrays(aClass.getInnerClasses(), aClass.getFields(), Object.class);
      }
      else if (isStaticClassReference()) {
        final PsiClass[] psiClasses = aClass.getInnerClasses();
        final List<PsiClass> staticClasses = new ArrayList<PsiClass>(psiClasses.length);

        for (PsiClass c : psiClasses) {
          if (c.hasModifierProperty(PsiModifier.STATIC)) {
            staticClasses.add(c);
          }
        }
        return staticClasses.size() > 0 ? staticClasses.toArray(new PsiClass[staticClasses.size()]) : PsiClass.EMPTY_ARRAY;
      }
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private static Object[] processPackage(final PsiPackage aPackage) {
    final PsiPackage[] subPackages = aPackage.getSubPackages();
    final PsiClass[] classes = aPackage.getClasses();
    return ArrayUtil.mergeArrays(subPackages, classes, Object.class);
  }

  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {

    final PsiElement psiElement = getElement();

    if (!psiElement.isValid()) return JavaResolveResult.EMPTY;

    final String elementText = psiElement.getText();

    final PsiElement context = getContext();
    if (context instanceof PsiClass) {
      if (isStaticClassReference(elementText)) {
          final PsiClass psiClass = ((PsiClass)context).findInnerClassByName(getCanonicalText(), false);
          if (psiClass != null) return new ClassCandidateInfo(psiClass, PsiSubstitutor.EMPTY, false, psiElement);
          return JavaResolveResult.EMPTY;
      } else if (!myInStaticImport && myJavaClassReferenceSet.isAllowDollarInNames() ) {
        return JavaResolveResult.EMPTY;        
      }
    }

    String qName = elementText.substring(myJavaClassReferenceSet.getReference(0).getRangeInElement().getStartOffset(), getRangeInElement().getEndOffset());
    if (qName.indexOf(".") == -1) {
      final String defaultPackage = JavaClassReferenceProvider.DEFAULT_PACKAGE.getValue(myOptions);
      if (StringUtil.isNotEmpty(defaultPackage)) {
        final JavaResolveResult resolveResult = advancedResolveInner(psiElement, defaultPackage + "." + qName);
        if (resolveResult != JavaResolveResult.EMPTY) {
          return resolveResult;
        }
      }
    }
    return advancedResolveInner(psiElement, qName);
  }

  private JavaResolveResult advancedResolveInner(final PsiElement psiElement, final String qName) {
    PsiManager manager = psiElement.getManager();
    GlobalSearchScope scope = getScope();
    if (myIndex == myJavaClassReferenceSet.getReferences().length - 1) {
      final PsiClass aClass = manager.findClass(qName, scope);
      if (aClass != null) {
        return new ClassCandidateInfo(aClass, PsiSubstitutor.EMPTY, false, psiElement);
      } else {
        final Boolean value = JavaClassReferenceProvider.RESOLVE_ONLY_CLASSES.getValue(getOptions());
        if (value != null && value.booleanValue()) {
          return JavaResolveResult.EMPTY;
        }
      }
    }
    PsiElement resolveResult = manager.findPackage(qName);
    if (resolveResult == null) {
      resolveResult = manager.findClass(qName, scope);
    }
    if (myInStaticImport && resolveResult == null) {
      resolveResult = resolveMember(qName, manager, getElement().getResolveScope());
    }
    if (resolveResult == null) {
      PsiFile containingFile = psiElement.getContainingFile();

      if (containingFile instanceof PsiJavaFile) {
        if (containingFile instanceof JspFile) {
          containingFile = containingFile.getViewProvider().getPsi(StdLanguages.JAVA);
          if (containingFile == null) return JavaResolveResult.EMPTY;
        }

        final ClassResolverProcessor processor = new ClassResolverProcessor(getCanonicalText(), psiElement);
        containingFile.processDeclarations(processor, PsiSubstitutor.EMPTY, null, psiElement);

        if (processor.getResult().length == 1) {
          final JavaResolveResult javaResolveResult = processor.getResult()[0];

          if (javaResolveResult != JavaResolveResult.EMPTY && getOptions() != null) {
            final Boolean value = JavaClassReferenceProvider.RESOLVE_QUALIFIED_CLASS_NAME.getValue(getOptions());
            final PsiClass psiClass = (PsiClass)javaResolveResult.getElement();
            if (value != null && value.booleanValue() && psiClass != null) {
              final String qualifiedName = psiClass.getQualifiedName();

              if (!qName.equals(qualifiedName)) {
                return JavaResolveResult.EMPTY;
              }
            }
          }

          return javaResolveResult;
        }
      }
    }
    return resolveResult != null
           ? new CandidateInfo(resolveResult, PsiSubstitutor.EMPTY, false, false, psiElement)
           : JavaResolveResult.EMPTY;
  }

  private GlobalSearchScope getScope() {
    GlobalSearchScope scope = myJavaClassReferenceSet.getProvider().getScope();
    if (scope == null) {
      scope = GlobalSearchScope.allScope(getElement().getProject());
    }
    return scope;
  }

  private Map<CustomizableReferenceProvider.CustomizationKey, Object> getOptions() {
    return myOptions;
  }

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    final JavaResolveResult javaResolveResult = advancedResolve(incompleteCode);
    if (javaResolveResult.getElement() == null) return JavaResolveResult.EMPTY_ARRAY;
    return new JavaResolveResult[]{javaResolveResult};
  }

  public void registerQuickfix(HighlightInfo info, PsiReference reference) {
    registerFixes(info);
  }

  private List<? extends LocalQuickFix> registerFixes(final HighlightInfo info) {
    final String[] extendClasses = JavaClassReferenceProvider.EXTEND_CLASS_NAMES.getValue(getOptions());
    final String extendClass = extendClasses != null && extendClasses.length > 0 ? extendClasses[0] : null;
    final PsiReference contextReference = getContextReference();

    PsiElement context = contextReference != null ? contextReference.resolve() : null;

    if (context != null || contextReference == null) {
      final int[] primitives = getType().getPrimitives();
      boolean createJavaClass = JavaClassReferenceProvider.CLASS_REFERENCE_TYPE.getPrimitives().length == primitives.length &&
                                JavaClassReferenceProvider.CLASS_REFERENCE_TYPE.isAssignableTo(primitives[0]);
      final List<PsiDirectory> writableDirectoryList = getWritableDirectoryList(context);
      if (writableDirectoryList.size() != 0 && checkCreateClassOrPackage(writableDirectoryList, createJavaClass)) {
        return Arrays.asList(doRegisterQuickFix(info, writableDirectoryList, createJavaClass, extendClass));
      }
    }
    return Collections.emptyList();
  }

  protected CreateClassOrPackageFix doRegisterQuickFix(final HighlightInfo info, final List<PsiDirectory> writableDirectoryList,
                                                                 final boolean createJavaClass,
                                                                 final String extendClass) {
    final CreateClassOrPackageFix action =
      new CreateClassOrPackageFix(writableDirectoryList, this, createJavaClass, extendClass);
    QuickFixAction.registerQuickFixAction(info, action);
    return action;
  }

  @NotNull
  private Object[] getSubclassVariants(@NotNull PsiPackage context, @NotNull String[] extendClasses) {
    HashSet<Object> lookups = new HashSet<Object>();
    GlobalSearchScope packageScope = GlobalSearchScope.packageScope(context, true);
    GlobalSearchScope scope = myJavaClassReferenceSet.getProvider().getScope();
    if (scope != null) {
      packageScope = packageScope.intersectWith(scope);
    }
    final GlobalSearchScope allScope = context.getProject().getAllScope();
    Boolean inst = JavaClassReferenceProvider.INSTANTIATABLE.getValue(getOptions());

    boolean instantiatable = inst == null || inst.booleanValue();

    for (String extendClassName : extendClasses) {
      PsiClass extendClass = context.getManager().findClass(extendClassName, allScope);
      if (extendClass != null) {
        PsiClass[] result = context.getManager().getSearchHelper().findInheritors(extendClass, packageScope, true);
        for (final PsiClass clazz : result) {
          Object value = createSubclassLookupValue(context, clazz, instantiatable);
          if (value != null) {
            lookups.add(value);
          }
        }
        // add itself
        Object value = createSubclassLookupValue(context, extendClass, instantiatable);
        if (value != null) {
          lookups.add(value);
        }
      }
    }
    return lookups.toArray();
  }

  @Nullable
  private static Object createSubclassLookupValue(@NotNull final PsiPackage context, @NotNull final PsiClass clazz, boolean instantiatable) {
    if (instantiatable && !PsiUtil.isInstantiatable(clazz)) {
      return null;
    }
    String name = clazz.getQualifiedName();
    if (name == null) return null;
    final String pack = context.getQualifiedName();
    if (pack.length() > 0) {
      // paranoic check for IDEADEV-13982
      if (pack.length() + 1 > name.length()) {
        return null;
      }
      name = name.substring(pack.length() + 1);
    }
    return LookupValueFactory.createLookupValue(name, clazz.getIcon(Iconable.ICON_FLAG_READ_STATUS));
   }

  public LocalQuickFix[] getQuickFixes() {
    final List<? extends LocalQuickFix> list = registerFixes(null);
    return list.toArray(new LocalQuickFix[list.size()]);
  }

  private boolean checkCreateClassOrPackage(final List<PsiDirectory> writableDirectoryList, final boolean createJavaClass) {
    final PsiDirectory directory = writableDirectoryList.get(0);
    final String canonicalText = getCanonicalText();

    if (createJavaClass) {
      try {
        directory.checkCreateClass(canonicalText);
      } catch(IncorrectOperationException ex) {
        return false;
      }
    } else {
      try {
        directory.checkCreateSubdirectory(canonicalText);
      } catch(IncorrectOperationException ex) {
        return false;
      }
    }
    return true;
  }

  protected List<PsiDirectory> getWritableDirectoryList(final PsiElement context) {
    return CreateClassOrPackageFix.getWritableDirectoryListDefault(context, getElement().getManager());
  }

  @Nullable
  public static PsiElement resolveMember(String fqn, PsiManager manager, GlobalSearchScope resolveScope) {
    PsiClass aClass = manager.findClass(fqn, resolveScope);
    if (aClass != null) return aClass;
    int i = fqn.lastIndexOf('.');
    if (i==-1) return null;
    String memberName = fqn.substring(i+1);
    fqn = fqn.substring(0, i);
    aClass = manager.findClass(fqn, resolveScope);
    if (aClass == null) return null;
    PsiMember member = aClass.findFieldByName(memberName, true);
    if (member != null) return member;

    PsiMethod[] methods = aClass.findMethodsByName(memberName, true);
    return methods.length == 0 ? null : methods[0];
  }
}
