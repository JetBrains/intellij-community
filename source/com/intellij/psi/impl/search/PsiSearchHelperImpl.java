package com.intellij.psi.impl.search;

import com.intellij.ant.PsiAntElement;
import com.intellij.aspects.psi.PsiAspect;
import com.intellij.aspects.psi.PsiPointcut;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.j2ee.ejb.role.EjbDeclMethodRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.RepositoryElementsManager;
import com.intellij.psi.impl.cache.RepositoryIndex;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.cache.impl.idCache.WordInfo;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.jsp.*;
import com.intellij.psi.search.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.StringSearcher;
import gnu.trove.TIntArrayList;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PsiSearchHelperImpl implements PsiSearchHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.PsiSearchHelperImpl");

  private final PsiManagerImpl myManager;
  private final JoinPointSearchHelper myJoinPointSearchHelper;
  private static final TokenSet XML_ATTRIBUTE_VALUE_TOKEN_BIT_SET = TokenSet.create(new IElementType[]{ElementType.XML_ATTRIBUTE_VALUE_TOKEN});
  private static final TodoItem[] EMPTY_TODO_ITEMS = new TodoItem[0];

  public interface CustomSearchHelper {
    short getOccurenceMask();
    TokenSet getElementTokenSet();
    boolean caseInsensitive();
  }

  public static class XmlCustomSearchHelper implements CustomSearchHelper {
    private TokenSet myTokenSet = TokenSet.create(new IElementType[] { XmlTokenType.XML_NAME});
    private short myOccurenceMask = WordInfo.PLAIN_TEXT;

    public short getOccurenceMask() {
      return myOccurenceMask;
    }

    public TokenSet getElementTokenSet() {
      return myTokenSet;
    }

    public boolean caseInsensitive() {
      return false;
    }
  }

  public static class HtmlCustomSearchHelper implements CustomSearchHelper {
    private TokenSet myTokenSet = XML_ATTRIBUTE_VALUE_TOKEN_BIT_SET;
    private short myOccurenceMask;

    public final void registerStyleCustomSearchHelper(CustomSearchHelper helper) {
      myTokenSet = TokenSet.orSet(myTokenSet,helper.getElementTokenSet());
      myOccurenceMask |= helper.getOccurenceMask();
    }

    public short getOccurenceMask() {
      return myOccurenceMask;
    }

    public TokenSet getElementTokenSet() {
      return myTokenSet;
    }

    public boolean caseInsensitive() {
      return true;
    }
  }

  static class XHtmlCustomSearchHelper extends HtmlCustomSearchHelper {
    public boolean caseInsensitive() {
      return false;
    }
  }

  private static final HashMap<FileType,CustomSearchHelper> CUSTOM_SEARCH_HELPERS = new HashMap<FileType, CustomSearchHelper>();

  static {
    registerCustomSearchHelper(StdFileTypes.HTML,new HtmlCustomSearchHelper());
    registerCustomSearchHelper(StdFileTypes.XHTML,new XHtmlCustomSearchHelper());

    XmlCustomSearchHelper searchHelper = new XmlCustomSearchHelper();
    registerCustomSearchHelper(StdFileTypes.XML,searchHelper);
    registerCustomSearchHelper(StdFileTypes.DTD,searchHelper);
  }

  public static void registerCustomSearchHelper(FileType fileType,CustomSearchHelper searchHelper) {
    CUSTOM_SEARCH_HELPERS.put(fileType, searchHelper);
  }

  public static final CustomSearchHelper getCustomSearchHelper(FileType fileType) {
    return CUSTOM_SEARCH_HELPERS.get(fileType);
  }

  public PsiSearchHelperImpl(PsiManagerImpl manager) {
    myManager = manager;
    myJoinPointSearchHelper = new JoinPointSearchHelper(manager, this);
  }

  public PsiReference[] findReferences(PsiElement element, SearchScope searchScope, boolean ignoreAccessScope) {
    LOG.assertTrue(searchScope != null);

    PsiReferenceProcessor.CollectElements processor = new PsiReferenceProcessor.CollectElements();
    processReferences(processor, element, searchScope, ignoreAccessScope);
    return processor.toArray(PsiReference.EMPTY_ARRAY);
  }

  public boolean processReferences(final PsiReferenceProcessor processor,
                                   final PsiElement refElement,
                                   SearchScope searchScope,
                                   boolean ignoreAccessScope) {
    return processReferences(processor, refElement, searchScope, ignoreAccessScope, true);
  }

  private boolean processReferences(final PsiReferenceProcessor processor,
                                    final PsiElement refElement,
                                    SearchScope originalScope,
                                    boolean ignoreAccessScope,
                                    boolean isStrictSignatureSearch) {
    LOG.assertTrue(originalScope != null);

    if (refElement instanceof PsiAnnotationMethod) {
      PsiMethod method = (PsiMethod)refElement;
      if (PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(method.getName()) && method.getParameterList().getParameters().length == 0) {
        PsiReference[] references = findReferences(method.getContainingClass(), originalScope, ignoreAccessScope);
        for (int i = 0; i < references.length; i++) {
          PsiReference reference = references[i];
          if (reference instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement javaReference = (PsiJavaCodeReferenceElement)reference;
            if (javaReference.getParent() instanceof PsiAnnotation) {
              PsiNameValuePair[] members = ((PsiAnnotation)javaReference.getParent()).getParameterList().getAttributes();
              if (members.length == 1 && members[0].getNameIdentifier() == null) {
                processor.execute(members[0].getReference());
              }
            }
          }
        }
      }
    }


    String text;
//    final boolean[] refTypes = new boolean[ElementType.LAST + 1];
    if (refElement instanceof PsiPackage) {
      text = ((PsiPackage)refElement).getName();
      if (text == null) return true;
    }
    else if (refElement instanceof PsiClass) {
      if (refElement instanceof PsiAnonymousClass) return true;
      text = ((PsiClass)refElement).getName();
    }
    else if (refElement instanceof PsiVariable) {
      text = ((PsiVariable)refElement).getName();
      if (text == null) return true;
    }
    else if (refElement instanceof PsiMethod) {
      if (((PsiMethod)refElement).isConstructor()) {
        return processConstructorReferences(processor, (PsiMethod)refElement, originalScope, ignoreAccessScope,
                                            isStrictSignatureSearch);
      }

      text = ((PsiMethod)refElement).getName();
    }
    else if (refElement instanceof PsiAntElement) {
      final PsiAntElement antElement = (PsiAntElement)refElement;
      final SearchScope searchScope = antElement.getSearchScope().intersectWith(originalScope);
      if (searchScope instanceof LocalSearchScope) {
        final PsiElement[] scopes = ((LocalSearchScope)searchScope).getScope();
        for (int i = 0; i < scopes.length; i++) {
          final PsiElement scope = scopes[i];
          if (!processAntElementScopeRoot(scope, refElement, antElement, processor)) return false;
        }
        return true;
      }
      else {
        return true;
      }
    }
    else if (refElement instanceof XmlAttributeValue) {
      /*
      if (((XmlAttributeValue)refElement).isAntPropertyDefinition() ||
          ((XmlAttributeValue)refElement).isAntTargetDefinition()) {
        PsiMetaData metaData = PsiTreeUtil.getParentOfType(refElement, XmlTag.class).getMetaData();
        if (metaData instanceof AntPropertyDeclaration) {
          Set<String> properties = ((AntPropertyDeclaration)metaData).getProperties();
          for (Iterator<String> iterator = properties.iterator(); iterator.hasNext();) {
            String propertyName = iterator.next();
            PsiReference[] refs = findReferencesInNonJavaFile(refElement.getContainingFile(), refElement, propertyName);
            for (int i = 0; i < refs.length; i++) {
              if (!processor.execute(refs[i])) return false;
            }
          }

          return true;
        }
      }
      */

      text = ((XmlAttributeValue)refElement).getValue();
    }
    else if (refElement instanceof PsiPointcutDef) {
      text = ((PsiPointcutDef)refElement).getName();
    }
    else if (refElement instanceof PsiNamedElement) {
      text = ((PsiNamedElement)refElement).getName();
    }
    else {
      return true;
    }

    SearchScope searchScope;
    if (!ignoreAccessScope) {
      SearchScope accessScope = getAccessScope(refElement);
      searchScope = originalScope.intersectWith(accessScope);
      if (searchScope == null) return true;
    }
    else {
      searchScope = originalScope;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("visitReferences() search scope: " + searchScope);
    }

    final PsiElementProcessorEx processor1 = new PsiElementProcessorEx() {
      public boolean execute(PsiElement element, int offsetInElement) {
        final PsiReference reference = element.findReferenceAt(offsetInElement);
        if (reference == null) return true;
        if (reference.isReferenceTo(refElement)) {
          return processor.execute(reference);
        }
        else {
          return true;
        }
      }
    };

    final CustomSearchHelper customSearchHelper = getCustomSearchHelper(refElement);

    final short occurrenceMask;

    if (customSearchHelper!=null) {
      occurrenceMask = customSearchHelper.getOccurenceMask();
      if (customSearchHelper.caseInsensitive()) text = text.toLowerCase();
    } else if (refElement instanceof XmlAttributeValue) {
      occurrenceMask = WordInfo.PLAIN_TEXT;
    }
    else {
      occurrenceMask = WordInfo.IN_CODE |
                       WordInfo.JSP_ATTRIBUTE_VALUE |
                       WordInfo.IN_COMMENTS;
    }

    final TokenSet elementTypes;
    if (customSearchHelper!=null) {
      elementTypes = customSearchHelper.getElementTokenSet();
    } else if (refElement instanceof XmlAttributeValue) {
      elementTypes = XML_ATTRIBUTE_VALUE_TOKEN_BIT_SET;
    }
    else {
      elementTypes = IDENTIFIER_OR_DOC_VALUE_OR_JSP_ATTRIBUTE_VALUE_BIT_SET;
    }

    if (!processElementsWithWord(
          processor1,
          searchScope,
          text,
          elementTypes,
          occurrenceMask,
          customSearchHelper!=null && customSearchHelper.caseInsensitive()
       )) {
      return false;
    }

    if (refElement instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)refElement;
      if (PropertyUtil.isSimplePropertyAccessor(method)) {
        final String propertyName = PropertyUtil.getPropertyName(method);
        if (myManager.getNameHelper().isIdentifier(propertyName)) {
          if (!processElementsWithWord(processor1,
                                       searchScope,
                                       propertyName,
                                       IDENTIFIER_OR_ATTRIBUTE_VALUE_BIT_SET,
                                       WordInfo.JSP_ATTRIBUTE_VALUE,
                                       false)) {
            return false;
          }
        }
      }
    }

    if (refElement.getContainingFile() instanceof JspFile) {
      boolean canBeAccessedByIncludes;
      if (refElement instanceof PsiField || refElement instanceof PsiMethod) {
        canBeAccessedByIncludes = refElement.getParent() instanceof JspDeclaration;
      }
      else if (refElement instanceof JspImplicitVariable) {
        canBeAccessedByIncludes = true;
      }
      else if (refElement instanceof PsiLocalVariable) {
        canBeAccessedByIncludes = refElement.getParent().getParent() instanceof JspFile;
      }
      else {
        canBeAccessedByIncludes = false;
      }
      if (canBeAccessedByIncludes) {
        PsiElementProcessor processor2 = new PsiBaseElementProcessor() {
          public boolean execute(PsiElement element) {
            return processor1.execute(element, 0);
          }
        };

        PsiFile[] files = JspUtil.findIncludingFiles(refElement.getContainingFile(), searchScope);
        for (int i = 0; i < files.length; i++) {
          if (!processIdentifiers(processor2, text, new LocalSearchScope(files[i]), IdentifierPosition.IN_CODE)) return false;
        }
      }
    }

    if (refElement instanceof PsiPackage && originalScope instanceof GlobalSearchScope) {
      if (!UIFormUtil.processReferencesInUIForms(processor, (PsiPackage)refElement, (GlobalSearchScope)originalScope)) return false;
    }
    else if (refElement instanceof PsiClass && originalScope instanceof GlobalSearchScope) {
      if (!UIFormUtil.processReferencesInUIForms(processor, (PsiClass)refElement, (GlobalSearchScope)originalScope)) return false;
    }
    else if (refElement instanceof PsiField && originalScope instanceof GlobalSearchScope) {
      if (!UIFormUtil.processReferencesInUIForms(processor, (PsiField)refElement, (GlobalSearchScope)originalScope)) return false;
    }

    return true;
  }

  private static CustomSearchHelper getCustomSearchHelper(final PsiElement refElement) {
    PsiFile containingFile = refElement.getContainingFile();
    final CustomSearchHelper customSearchHelper = containingFile != null ? CUSTOM_SEARCH_HELPERS.get(containingFile.getFileType()) : null;
    return customSearchHelper;
  }

  private boolean processAntElementScopeRoot(final PsiElement scope,
                                             final PsiElement refElement,
                                             final PsiAntElement antElement,
                                             final PsiReferenceProcessor processor) {
    final PsiReference[] references = findReferencesInNonJavaFile(scope.getContainingFile(), refElement, antElement.getName());
    for (int i = 0; i < references.length; i++) {
      PsiReference reference = references[i];
      if (!processor.execute(reference)) return false;
    }
    return true;
  }

  private boolean processConstructorReferences(final PsiReferenceProcessor processor,
                                               final PsiMethod constructor,
                                               final SearchScope searchScope,
                                               boolean ignoreAccessScope,
                                               final boolean isStrictSignatureSearch) {
    LOG.assertTrue(searchScope != null);

    PsiClass aClass = constructor.getContainingClass();
    if (aClass == null) return true;

    if (aClass.isEnum()) {
      PsiField[] fields = aClass.getFields();
      for (int i = 0; i < fields.length; i++) {
        PsiField field = fields[i];
        if (field instanceof PsiEnumConstant) {
          PsiReference reference = field.getReference();
          if (reference != null && reference.isReferenceTo(constructor)) {
            if (!processor.execute(reference)) return false;
          }
        }
      }
    }

    // search usages like "new XXX(..)"
    PsiReferenceProcessor processor1 = new PsiReferenceProcessor() {
      public boolean execute(PsiReference reference) {
        PsiElement parent = reference.getElement().getParent();
        if (parent instanceof PsiAnonymousClass) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiNewExpression) {
          PsiMethod constructor1 = ((PsiNewExpression)parent).resolveConstructor();
          if (constructor1 != null) {
            if (isStrictSignatureSearch) {
              if (myManager.areElementsEquivalent(constructor, constructor1)) {
                return processor.execute(reference);
              }
            }
            else {
              if (myManager.areElementsEquivalent(constructor.getContainingClass(), constructor1.getContainingClass())) {
                return processor.execute(reference);
              }
            }
          }
        }
        return true;
      }
    };
    if (!processReferences(processor1, aClass, searchScope, ignoreAccessScope)) return false;

    // search usages like "this(..)"
    PsiMethod[] methods = aClass.getMethods();
    for (int i = 0; i < methods.length; i++) {
      PsiMethod method = methods[i];
      if (method.isConstructor()) {
        PsiCodeBlock body = method.getBody();
        if (body != null) {
          PsiStatement[] statements = body.getStatements();
          if (statements.length > 0) {
            PsiStatement statement = statements[0];
            if (statement instanceof PsiExpressionStatement) {
              PsiExpression expr = ((PsiExpressionStatement)statement).getExpression();
              if (expr instanceof PsiMethodCallExpression) {
                PsiReferenceExpression refExpr = ((PsiMethodCallExpression)expr).getMethodExpression();
                if (PsiSearchScopeUtil.isInScope(searchScope, refExpr)) {
                  if (refExpr.getText().equals("this")) {
                    PsiElement referencedElement = refExpr.resolve();
                    if (referencedElement instanceof PsiMethod) {
                      PsiMethod constructor1 = (PsiMethod)referencedElement;
                      if (isStrictSignatureSearch) {
                        if (myManager.areElementsEquivalent(constructor1, constructor)) {
                          if (!processor.execute(refExpr)) return false;
                        }
                      }
                      else {
                        if (myManager.areElementsEquivalent(constructor.getContainingClass(),
                                                            constructor1.getContainingClass())) {
                          if (!processor.execute(refExpr)) return false;
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // search usages like "super(..)"
    PsiElementProcessor<PsiClass> processor2 = new PsiBaseElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        PsiClass inheritor = element;
        PsiMethod[] methods = inheritor.getMethods();
        for (int j = 0; j < methods.length; j++) {
          PsiMethod method = methods[j];
          if (method.isConstructor()) {
            PsiCodeBlock body = method.getBody();
            if (body != null) {
              PsiStatement[] statements = body.getStatements();
              if (statements.length > 0) {
                PsiStatement statement = statements[0];
                if (statement instanceof PsiExpressionStatement) {
                  PsiExpression expr = ((PsiExpressionStatement)statement).getExpression();
                  if (expr instanceof PsiMethodCallExpression) {
                    PsiReferenceExpression refExpr = ((PsiMethodCallExpression)expr).getMethodExpression();
                    if (PsiSearchScopeUtil.isInScope(searchScope, refExpr)) {
                      if (refExpr.getText().equals("super")) {
                        PsiElement referencedElement = refExpr.resolve();
                        if (referencedElement instanceof PsiMethod) {
                          PsiMethod constructor1 = (PsiMethod)referencedElement;
                          if (isStrictSignatureSearch) {
                            if (myManager.areElementsEquivalent(constructor1, constructor)) {
                              if (!processor.execute(refExpr)) return false;
                            }
                          }
                          else {
                            if (myManager.areElementsEquivalent(constructor.getContainingClass(),
                                                                constructor1.getContainingClass())) {
                              if (!processor.execute(refExpr)) return false;
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        return true;
      }
    };
    return processInheritors(processor2, aClass, searchScope, false);
  }

  public PsiMethod[] findOverridingMethods(PsiMethod method, SearchScope searchScope, boolean checkDeep) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiMethod> processor = new PsiElementProcessor.CollectElements<PsiMethod>();
    processOverridingMethods(processor, method, searchScope, checkDeep);

    return processor.toArray(PsiMethod.EMPTY_ARRAY);
  }

  public boolean processOverridingMethods(final PsiElementProcessor<PsiMethod> processor,
                                          final PsiMethod method,
                                          SearchScope searchScope,
                                          final boolean checkDeep) {
    LOG.assertTrue(searchScope != null);

    final PsiClass parentClass = method.getContainingClass();
    if (parentClass == null
        || method.isConstructor()
        || method.hasModifierProperty(PsiModifier.STATIC)
        || method.hasModifierProperty(PsiModifier.FINAL)
        || method.hasModifierProperty(PsiModifier.PRIVATE)
        || parentClass instanceof PsiAnonymousClass
        || parentClass.hasModifierProperty(PsiModifier.FINAL)) {
      return true;
    }

    PsiElementProcessor<PsiClass> inheritorsProcessor = new PsiBaseElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        PsiClass inheritor = element;
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(parentClass, inheritor,
                                                                                 PsiSubstitutor.EMPTY);
        MethodSignature signature = method.getSignature(substitutor);
        PsiMethod method1 = MethodSignatureUtil.findMethodBySignature(inheritor, signature, false);
        if (method1 == null ||
            method1.hasModifierProperty(PsiModifier.STATIC) ||
            (method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
             !method1.getManager().arePackagesTheSame(parentClass, inheritor))) {
          return true;
        }
        return processor.execute(method1);
      }
    };
    if (!processInheritors(inheritorsProcessor, parentClass, searchScope, true)) return false;
    final EjbMethodRole ejbRole = J2EERolesUtil.getEjbRole(method);
    if (ejbRole instanceof EjbDeclMethodRole) {
      final PsiMethod[] implementations = ((EjbDeclMethodRole)ejbRole).findImplementations();
      for (int i = 0; i < implementations.length; i++) {
        PsiMethod implementation = implementations[i];
        // same signature methods were processed already
        if (implementation.getSignature(PsiSubstitutor.EMPTY).equals(method.getSignature(PsiSubstitutor.EMPTY))) continue;
        if (!processor.execute(implementation)) return false;
      }
    }
    return true;
  }

  public PsiPointcutDef[] findOverridingPointcuts(PsiPointcutDef pointcut,
                                                  SearchScope searchScope,
                                                  boolean checkDeep) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiPointcutDef> processor = new PsiElementProcessor.CollectElements<PsiPointcutDef>();
    processOverridingPointcuts(processor, pointcut, searchScope, checkDeep);

    final Collection<PsiPointcutDef> foundPointcuts = processor.getCollection();
    return foundPointcuts.toArray(new PsiPointcutDef[foundPointcuts.size()]);
  }

  public boolean processOverridingPointcuts(final PsiElementProcessor processor,
                                            final PsiPointcutDef pointcut,
                                            SearchScope searchScope,
                                            boolean checkDeep) {
    PsiAspect parentAspect = pointcut.getContainingAspect();

    if (parentAspect == null || pointcut.hasModifierProperty(PsiModifier.FINAL)) {
      return true;
    }

    PsiElementProcessor<PsiClass> inheritorsProcessor = new PsiBaseElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        if (!(element instanceof PsiAspect)) return true;
        PsiAspect inheritor = (PsiAspect)element;
        PsiPointcutDef pointcut1 = inheritor.findPointcutDefBySignature(pointcut, false);
        if (pointcut1 == null) return true;
        return processor.execute(pointcut1);
      }
    };

    return processInheritors(inheritorsProcessor, parentAspect, searchScope, true);
  }

  public PsiReference[] findReferencesIncludingOverriding(final PsiMethod method,
                                                          SearchScope searchScope,
                                                          boolean isStrictSignatureSearch) {
    LOG.assertTrue(searchScope != null);

    PsiReferenceProcessor.CollectElements processor = new PsiReferenceProcessor.CollectElements();
    processReferencesIncludingOverriding(processor, method, searchScope, isStrictSignatureSearch);
    return processor.toArray(PsiReference.EMPTY_ARRAY);
  }

  public boolean processReferencesIncludingOverriding(final PsiReferenceProcessor processor,
                                                      final PsiMethod method,
                                                      SearchScope searchScope) {
    return processReferencesIncludingOverriding(processor, method, searchScope, true);
  }

  public boolean processReferencesIncludingOverriding(final PsiReferenceProcessor processor,
                                                       final PsiMethod method,
                                                       SearchScope searchScope,
                                                       final boolean isStrictSignatureSearch) {
    LOG.assertTrue(searchScope != null);

    PsiClass parentClass = method.getContainingClass();
    if (method.isConstructor()) {
      return processConstructorReferences(processor, method, searchScope, !isStrictSignatureSearch,
                                          isStrictSignatureSearch);
    }

    if (isStrictSignatureSearch && (parentClass == null
                                    || parentClass instanceof PsiAnonymousClass
                                    || parentClass.hasModifierProperty(PsiModifier.FINAL)
                                    || method instanceof PsiAnnotationMethod
                                    || method.hasModifierProperty(PsiModifier.STATIC)
                                    || method.hasModifierProperty(PsiModifier.FINAL)
                                    || method.hasModifierProperty(PsiModifier.PRIVATE))
    ) {
      return processReferences(processor, method, searchScope, false);
    }

    final String text = method.getName();
    final PsiMethod[] methods = isStrictSignatureSearch ? new PsiMethod[]{method} : getOverloadsMayBeOverriden(method);

    SearchScope accessScope = getAccessScope(methods[0]);
    for (int i = 0; i < methods.length; i++) {
      SearchScope someScope = PsiSearchScopeUtil.scopesUnion(accessScope, getAccessScope(methods[i]));
      accessScope = someScope == null ? accessScope : someScope;
    }

    final PsiClass aClass = method.getContainingClass();

    final PsiElementProcessorEx processor1 = new PsiElementProcessorEx() {
      public boolean execute(PsiElement element, int offsetInElement) {
        PsiReference reference = element.findReferenceAt(offsetInElement);

        if (reference != null) {
          for (int i = 0; i < methods.length; i++) {
            PsiMethod method = methods[i];
            if (reference.isReferenceTo(method)) {
              return processor.execute(reference);
            }
            PsiElement refElement = reference.resolve();

            if (refElement instanceof PsiMethod) {
              PsiMethod refMethod = (PsiMethod)refElement;
              PsiClass refMethodClass = refMethod.getContainingClass();
              if (refMethodClass == null) return true;

              if (!refMethod.hasModifierProperty(PsiModifier.STATIC)) {
                PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(aClass, refMethodClass, PsiSubstitutor.EMPTY);
                if (substitutor != null) {
                  if (method.getSignature(PsiSubstitutor.EMPTY).equals(refMethod.getSignature(substitutor))) {
                    if (!processor.execute(reference)) return false;
                  }
                }
              }

              if (!isStrictSignatureSearch) {
                PsiManager manager = method.getManager();
                if (manager.areElementsEquivalent(refMethodClass, aClass)) {
                  return processor.execute(reference);
                }
              }
            }
            else {
              return true;
            }
          }
        }

        return true;
      }
    };

    searchScope = searchScope.intersectWith(accessScope);
    if (searchScope == null) return true;

    short occurrenceMask = WordInfo.IN_CODE | WordInfo.IN_COMMENTS;
    boolean toContinue = processElementsWithWord(processor1,
                                                 searchScope,
                                                 text,
                                                 IDENTIFIER_OR_DOC_VALUE_BIT_SET,
                                                 occurrenceMask, false);
    if (!toContinue) return false;

    if (PropertyUtil.isSimplePropertyAccessor(method)) {
      String propertyName = PropertyUtil.getPropertyName(method);
      if (myManager.getNameHelper().isIdentifier(propertyName)) {
        toContinue = processElementsWithWord(processor1,
                                             searchScope,
                                             propertyName,
                                             IDENTIFIER_OR_ATTRIBUTE_VALUE_BIT_SET,
                                             WordInfo.JSP_ATTRIBUTE_VALUE, false);
        if (!toContinue) return false;
      }
    }

    return true;
  }

  public PsiClass[] findInheritors(PsiClass aClass, SearchScope searchScope, boolean checkDeep) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiClass> processor = new PsiElementProcessor.CollectElements<PsiClass>();
    processInheritors(processor, aClass, searchScope, checkDeep);
    return processor.toArray(PsiClass.EMPTY_ARRAY);
  }

  public boolean processInheritors(PsiElementProcessor<PsiClass> processor,
                                   PsiClass aClass,
                                   SearchScope searchScope,
                                   boolean checkDeep) {
    return processInheritors(processor, aClass, searchScope, checkDeep, true);
  }

  public boolean processInheritors(PsiElementProcessor<PsiClass> processor,
                                   PsiClass aClass,
                                   SearchScope searchScope,
                                   boolean checkDeep,
                                   boolean checkInheritance) {
    LOG.assertTrue(searchScope != null);

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.pushState();
      String className = aClass.getName();
      progress.setText("Searching inheritors" + (className != null ? " of " + className : "") + "...");
    }

    ArrayList<PsiClass> processed = new ArrayList<PsiClass>();
    processed.add(aClass);
    boolean result = processInheritors(processor, aClass, searchScope, checkDeep, processed,
                                       checkInheritance);

    if (progress != null) {
      progress.popState();
    }

    return result;
  }

  private boolean processInheritors(PsiElementProcessor<PsiClass> processor,
                                    PsiClass aClass,
                                    final SearchScope searchScope,
                                    boolean checkDeep,
                                    ArrayList<PsiClass> processed,
                                    boolean checkInheritance) {
    LOG.assertTrue(searchScope != null);

    if (aClass instanceof PsiAnonymousClass) return true;

    if (aClass.hasModifierProperty(PsiModifier.FINAL)) return true;

    String name = aClass.getName();

    RepositoryManager repositoryManager = myManager.getRepositoryManager();
    RepositoryElementsManager repositoryElementsManager = myManager.getRepositoryElementsManager();

    if ("java.lang.Object".equals(aClass.getQualifiedName())) { // special case
      // TODO!
    }

    if (aClass instanceof PsiAspect) {
      PsiAspectManager aspectManager = aClass.getManager().getAspectManager();
      PsiAspect[] aspects = aspectManager.getAspects();
      for (int i = 0; i < aspects.length; i++) {
        if (!processInheritorCandidate(processor, aspects[i], aClass, searchScope, checkDeep, processed,
                                       checkInheritance)) {
          return false;
        }
      }
      return true;
    }

    final SearchScope searchScope1 = searchScope.intersectWith(PsiSearchScopeUtil.getAccessScope(aClass));

    RepositoryIndex repositoryIndex = repositoryManager.getIndex();
    VirtualFileFilter rootFilter;
    if (searchScope1 instanceof GlobalSearchScope) {
      rootFilter = repositoryIndex.rootFilterBySearchScope((GlobalSearchScope)searchScope1);
    }
    else {
      rootFilter = null;
    }
    long[] candidateIds = repositoryIndex.getNameOccurrencesInExtendsLists(name, rootFilter);
    for (int i = 0; i < candidateIds.length; i++) {
      long id = candidateIds[i];
      PsiClass candidate = (PsiClass)repositoryElementsManager.findOrCreatePsiElementById(id);
      LOG.assertTrue(candidate.isValid());
      if (!processInheritorCandidate(processor, candidate, aClass, searchScope, checkDeep, processed,
                                     checkInheritance)) {
        return false;
      }
    }

    final EjbClassRole classRole = J2EERolesUtil.getEjbRole(aClass);
    if (classRole != null && classRole.isDeclarationRole()) {
      final PsiClass[] implementations = classRole.findImplementations();
      for (int i = 0; i < implementations.length; i++) {
        PsiClass candidate = implementations[i];
        if (!processInheritorCandidate(processor, candidate, aClass, searchScope, checkDeep, processed,
                                       checkInheritance)) {
          return false;
        }
      }
    }

    return true;
  }

  private boolean processInheritorCandidate(PsiElementProcessor<PsiClass> processor,
                                            PsiClass candidate,
                                            PsiClass baseClass,
                                            SearchScope searchScope,
                                            boolean checkDeep,
                                            ArrayList<PsiClass> processed,
                                            boolean checkInheritance) {
    if (checkInheritance || (checkDeep && !(candidate instanceof PsiAnonymousClass))) {
      if (!candidate.isInheritor(baseClass, false)) return true;
    }

    if (processed.contains(candidate)) return true;
    processed.add(candidate);

    if (candidate instanceof PsiAnonymousClass) {
      if (!processor.execute(candidate)) return false;
    }
    else {
      if (PsiSearchScopeUtil.isInScope(searchScope, candidate)) {
        if (searchScope instanceof GlobalSearchScope) {
          String qName = candidate.getQualifiedName();
          if (qName != null) {
            PsiClass candidate1 = myManager.findClass(qName, (GlobalSearchScope)searchScope);
            if (candidate != candidate1) return true;
          }
        }
        if (!processor.execute(candidate)) return false;
      }

      if (checkDeep) {
        if (!processInheritors(processor, candidate, searchScope, checkDeep, processed, checkInheritance)) return false;
      }
    }

    return true;
  }

  public JspDirective[] findIncludeDirectives(final PsiFile file, SearchScope searchScope) {
    LOG.assertTrue(searchScope != null);

    String name = file.getVirtualFile().getName();
    final ArrayList<JspDirective> directives = new ArrayList<JspDirective>();
    PsiElementProcessorEx processor = new PsiElementProcessorEx() {
      public boolean execute(PsiElement element, int offsetInElement) {
        CompositeElement parent = (SourceTreeToPsiMap.psiElementToTree(element)).getTreeParent();
        if (parent.getElementType() == ElementType.JSP_FILE_REFERENCE) {
          CompositeElement pparent = parent.getTreeParent();
          if (SourceTreeToPsiMap.treeElementToPsi(pparent) instanceof JspAttribute &&
              pparent.getTreeParent().getElementType() == ElementType.JSP_DIRECTIVE) {
            JspDirective directive = (JspDirective)SourceTreeToPsiMap.treeElementToPsi(pparent.getTreeParent());
            if (directive.getName().equals("include")) {
              JspAttribute attribute = (JspAttribute)SourceTreeToPsiMap.treeElementToPsi(pparent);
              if (attribute.getName().equals("file")) {
                PsiFile refFile = (PsiFile)((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(parent)).resolve();
                if (file.equals(refFile)) {
                  directives.add(directive);
                }
              }
            }
          }
        }
        return true;
      }
    };
    processElementsWithWord(processor,
                            searchScope,
                            name,
                            JSP_DIRECTIVE_ATTRIBUTE_BIT_SET,
                            WordInfo.JSP_INCLUDE_DIRECTIVE_FILE_NAME, false);
    return directives.toArray(new JspDirective[directives.size()]);
  }

  private static final TokenSet JSP_DIRECTIVE_ATTRIBUTE_BIT_SET = TokenSet.create(new IElementType[]{ElementType.JSP_DIRECTIVE_ATTRIBUTE_VALUE_TOKEN});

  public PsiFile[] findFilesWithTodoItems() {
    return myManager.getCacheManager().getFilesWithTodoItems();
  }

  private static final TokenSet XML_COMMENT_BIT_SET = TokenSet.create(new IElementType[]{TreeElement.XML_COMMENT_CHARACTERS});

  public TodoItem[] findTodoItems(PsiFile file) {
    return findTodoItems(file, null);
  }

  public TodoItem[] findTodoItems(PsiFile file, int startOffset, int endOffset) {
    return findTodoItems(file, new TextRange(startOffset, endOffset));
  }

  private TodoItem[] findTodoItems(PsiFile file, TextRange range) {
    if (file instanceof PsiBinaryFile || file instanceof PsiCompiledElement ||
        file.getVirtualFile() == null) {
      return EMPTY_TODO_ITEMS;
    }

    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile());
    if (count == 0) {
      return EMPTY_TODO_ITEMS;
    }

    TIntArrayList commentStarts = new TIntArrayList();
    TIntArrayList commentEnds = new TIntArrayList();
    char[] chars = file.textToCharArray();
    if (file instanceof PsiPlainTextFile) {
      commentStarts.add(0);
      commentEnds.add(file.getTextLength());
    }
    else {
      // collect comment offsets to prevent long locks by PsiManagerImpl.LOCK
      synchronized (PsiLock.LOCK) {
        final Lexer lexer = ((PsiFileImpl)file).createLexer();
        TokenSet COMMENT_TOKEN_BIT_SET;
        if (file instanceof PsiJavaFile || file instanceof JspFile) {
          COMMENT_TOKEN_BIT_SET = ElementType.COMMENT_BIT_SET;
        }
        else if (file instanceof XmlFile) {
          COMMENT_TOKEN_BIT_SET = XML_COMMENT_BIT_SET;
        }
        else {
          // TODO: ask the lexer about comment types!
          //LOG.assertTrue(false);
          return EMPTY_TODO_ITEMS;
        }
        for (lexer.start(chars); ; lexer.advance()) {
          IElementType tokenType = lexer.getTokenType();
          if (tokenType == null) break;

          if (range != null) {
            if (lexer.getTokenEnd() <= range.getStartOffset()) continue;
            if (lexer.getTokenStart() >= range.getEndOffset()) break;
          }

          if (COMMENT_TOKEN_BIT_SET.isInSet(tokenType)) {
            commentStarts.add(lexer.getTokenStart());
            commentEnds.add(lexer.getTokenEnd());
          }
        }
      }
    }

    ArrayList<TodoItem> list = new ArrayList<TodoItem>();

    for (int i = 0; i < commentStarts.size(); i++) {
      int commentStart = commentStarts.get(i);
      int commentEnd = commentEnds.get(i);

      TodoPattern[] patterns = TodoConfiguration.getInstance().getTodoPatterns();
      for (int j = 0; j < patterns.length; j++) {
        TodoPattern toDoPattern = patterns[j];
        Pattern pattern = toDoPattern.getPattern();
        if (pattern != null) {
          ProgressManager.getInstance().checkCanceled();

          CharSequence input = new CharArrayCharSequence(chars, commentStart, commentEnd);
          Matcher matcher = pattern.matcher(input);
          while (true) {
            //long time1 = System.currentTimeMillis();
            boolean found = matcher.find();
            //long time2 = System.currentTimeMillis();
            //System.out.println("scanned text of length " + (lexer.getTokenEnd() - lexer.getTokenStart() + " in " + (time2 - time1) + " ms"));

            if (!found) break;
            int start = matcher.start() + commentStart;
            int end = matcher.end() + commentStart;
            if (start != end) {
              if (range == null || range.getStartOffset() <= start && end <= range.getEndOffset()) {
                list.add(new TodoItemImpl(file, start, end, toDoPattern));
              }
            }

            ProgressManager.getInstance().checkCanceled();
          }
        }
      }
    }

    return list.toArray(new TodoItem[list.size()]);
  }

  public int getTodoItemsCount(PsiFile file) {
    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile());
    if (count != -1) return count;
    return findTodoItems(file).length;
  }

  public int getTodoItemsCount(PsiFile file, TodoPattern pattern) {
    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile(), pattern);
    if (count != -1) return count;
    TodoItem[] items = findTodoItems(file);
    count = 0;
    for (int i = 0; i < items.length; i++) {
      TodoItem item = items[i];
      if (item.getPattern().equals(pattern)) count++;
    }
    return count;
  }

  public PsiIdentifier[] findIdentifiers(String identifier, SearchScope searchScope, int position) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiIdentifier> processor = new PsiElementProcessor.CollectElements<PsiIdentifier>();
    processIdentifiers(processor, identifier, searchScope, position);
    return processor.toArray(new PsiIdentifier[0]);
  }

  public boolean processIdentifiers(final PsiElementProcessor processor,
                                    final String identifier,
                                    SearchScope searchScope,
                                    int position) {
    LOG.assertTrue(searchScope != null);

    short occurrenceMask;
    switch (position) {
      case IdentifierPosition.ANY:
        occurrenceMask = WordInfo.ANY;
        break;

      case IdentifierPosition.IN_CODE:
        occurrenceMask = WordInfo.IN_CODE;
        break;

      default:
        LOG.assertTrue(false);
        return true;
    }

    PsiElementProcessorEx processor1 = new PsiElementProcessorEx() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element.getText().equals(identifier)) {
          return processor.execute(element);
        }
        return true;
      }
    };
    return processElementsWithWord(processor1, searchScope, identifier, IDENTIFIER_BIT_SET, occurrenceMask, false);
  }


  public PsiElement[] findThrowUsages(final PsiThrowStatement aThrow, final SearchScope searchScope) {
    return new PsiElement[]{aThrow};
  }


  private static final TokenSet IDENTIFIER_BIT_SET = TokenSet.create(new IElementType[]{ElementType.IDENTIFIER});

  private static final TokenSet IDENTIFIER_OR_DOC_VALUE_BIT_SET = TokenSet.create(new IElementType[]{ElementType.IDENTIFIER, ElementType.DOC_TAG_VALUE_TOKEN});

  private static final TokenSet IDENTIFIER_OR_ATTRIBUTE_VALUE_BIT_SET = TokenSet.create(new IElementType[]{ElementType.IDENTIFIER,
                                                                                      ElementType.JSP_ACTION_ATTRIBUTE_VALUE_TOKEN});

  private static final TokenSet IDENTIFIER_OR_DOC_VALUE_OR_JSP_ATTRIBUTE_VALUE_BIT_SET = TokenSet.create(new IElementType[]{
    ElementType.IDENTIFIER, ElementType.JSP_ACTION_ATTRIBUTE_VALUE_TOKEN, ElementType.DOC_TAG_VALUE_TOKEN});

  public PsiElement[] findCommentsContainingIdentifier(String identifier, SearchScope searchScope) {
    LOG.assertTrue(searchScope != null);

    final ArrayList<PsiElement> results = new ArrayList<PsiElement>();
    PsiElementProcessorEx processor = new PsiElementProcessorEx() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element.findReferenceAt(offsetInElement) == null) {
          results.add(element);
        }
        return true;
      }
    };
    processElementsWithWord(processor, searchScope, identifier, COMMENT_BIT_SET, WordInfo.IN_COMMENTS, false);
    return results.toArray(new PsiElement[results.size()]);
  }

  private static final TokenSet COMMENT_BIT_SET = TokenSet.create(new IElementType[]{
    ElementType.DOC_COMMENT_DATA,
    ElementType.DOC_TAG_VALUE_TOKEN,
    ElementType.C_STYLE_COMMENT,
    ElementType.END_OF_LINE_COMMENT});

  public PsiLiteralExpression[] findStringLiteralsContainingIdentifier(String identifier, SearchScope searchScope) {
    LOG.assertTrue(searchScope != null);

    final ArrayList<PsiLiteralExpression> results = new ArrayList<PsiLiteralExpression>();
    PsiElementProcessorEx processor = new PsiElementProcessorEx() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element instanceof PsiLiteralExpression && StringUtil.startsWithChar(element.getText(), '\"')) {
          results.add((PsiLiteralExpression)element);
        }
        return true;
      }
    };
    processElementsWithWord(processor,
                            searchScope,
                            identifier,
                            LITERAL_EXPRESSION_BIT_SET,
                            WordInfo.IN_STRING_LITERALS,
                            false);
    return results.toArray(new PsiLiteralExpression[results.size()]);
  }

  public boolean processAllClasses(final PsiElementProcessor<PsiClass> processor, SearchScope searchScope) {
    if (searchScope instanceof GlobalSearchScope) {
      return processAllClassesInGlobalScope((GlobalSearchScope)searchScope, processor);
    }

    PsiElement[] scopeRoots = ((LocalSearchScope)searchScope).getScope();
    for (int i = 0; i < scopeRoots.length; i++) {
      final PsiElement scopeRoot = scopeRoots[i];
      if (!processScopeRootForAllClasses(scopeRoot, processor)) return false;
    }
    return true;
  }

  private static boolean processScopeRootForAllClasses(PsiElement scopeRoot, final PsiElementProcessor<PsiClass> processor) {
    if (scopeRoot == null) return true;
    final boolean[] stopped = new boolean[]{false};

    scopeRoot.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (!stopped[0]) {
          visitElement(expression);
        }
      }

      public void visitClass(PsiClass aClass) {
        stopped[0] = !processor.execute(aClass);
        super.visitClass(aClass);
      }
    });

    return !stopped[0];
  }

  private boolean processAllClassesInGlobalScope(GlobalSearchScope searchScope, PsiElementProcessor<PsiClass> processor) {
    myManager.getRepositoryManager().updateAll();

    LinkedList<PsiDirectory> queue = new LinkedList<PsiDirectory>();
    PsiDirectory[] roots = myManager.getRootDirectories(PsiRootPackageType.SOURCE_PATH);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex();
    for (int i = 0; i < roots.length; i++) {
      final PsiDirectory root = roots[i];
      if (fileIndex.isInContent(root.getVirtualFile())) {
        queue.addFirst(root);
      }
    }

    roots = myManager.getRootDirectories(PsiRootPackageType.CLASS_PATH);
    for (int i = 0; i < roots.length; i++) {
      queue.addFirst(roots[i]);
    }

    while (!queue.isEmpty()) {
      PsiDirectory dir = queue.removeFirst();
      Module module = ModuleUtil.findModuleForPsiElement(dir);
      if (!(module != null ? searchScope.isSearchInModuleContent(module) : searchScope.isSearchInLibraries())) continue;

      PsiDirectory[] subdirectories = dir.getSubdirectories();
      for (int i = 0; i < subdirectories.length; i++) {
        queue.addFirst(subdirectories[i]);
      }

      PsiFile[] files = dir.getFiles();
      for (int i = 0; i < files.length; i++) {
        PsiFile file = files[i];
        if (!searchScope.contains(file.getVirtualFile())) continue;
        if (!(file instanceof PsiJavaFile)) continue;

        long fileId = myManager.getRepositoryManager().getFileId(file.getVirtualFile());
        if (fileId >= 0) {
          long[] allClasses = myManager.getRepositoryManager().getFileView().getAllClasses(fileId);
          for (int j = 0; j < allClasses.length; j++) {
            PsiClass psiClass = (PsiClass)myManager.getRepositoryElementsManager().findOrCreatePsiElementById(allClasses[j]);
            if (!processor.execute(psiClass)) return false;
          }
        }
        else {
          if (!processAllClasses(processor, new LocalSearchScope(file))) return false;
        }
      }
    }

    return true;
  }

  public PsiClass[] findAllClasses(SearchScope searchScope) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiClass> processor = new PsiElementProcessor.CollectElements<PsiClass>();
    processAllClasses(processor, searchScope);
    return processor.toArray(PsiClass.EMPTY_ARRAY);
  }

  static class SearchDescriptor {
    private HashSet<String> myNames = new HashSet<String>();
    private HashSet<PsiFile> myFiles = new HashSet<PsiFile>();
    private HashSet<PsiElement> myElements = new HashSet<PsiElement>();
  }

  public SearchDescriptor prepareBundleReferenceSearch(PsiElement[] refElements) {
    SearchDescriptor descriptor = new SearchDescriptor();
    for (int i = 0; i < refElements.length; i++) {
      PsiElement refElement = refElements[i];
      if (refElement instanceof PsiNamedElement) {
        String name = ((PsiNamedElement)refElement).getName();
        if (name != null) {
          PsiFile[] files = myManager.getCacheManager().getFilesWithWord(name,
                                                                         WordInfo.IN_CODE,
                                                                         GlobalSearchScope.allScope(myManager.getProject()));
          descriptor.myNames.add(name);
          descriptor.myFiles.addAll(Arrays.asList(files));
          descriptor.myElements.add(refElement);
        }
      }
    }

    return descriptor;
  }

  public boolean processReferencesToElementsInLocalScope(final PsiReferenceProcessor processor,
                                                         final SearchDescriptor bundleSearchDescriptor,
                                                         LocalSearchScope scope) {
    PsiElement[] scopeElements = scope.getScope();
    for (int i = 0; i < scopeElements.length; i++) {
      final PsiElement scopeElement = scopeElements[i];
      if (!processReferencesToElementInScopeElement(scopeElement, bundleSearchDescriptor, processor)) {
        return false;
      }
    }
    return true;

  }

  private static boolean processReferencesToElementInScopeElement(PsiElement scopeElement,
                                                           final SearchDescriptor bundleSearchDescriptor,
                                                           final PsiReferenceProcessor processor) {
    if (scopeElement == null) return true;
    if (!bundleSearchDescriptor.myFiles.contains(scopeElement.getContainingFile())) return true;

    final PsiElementProcessor processor1 = new PsiBaseElementProcessor() {
      public boolean execute(PsiElement element) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiJavaCodeReferenceElement) {
          PsiReference ref = (PsiJavaCodeReferenceElement)parent;
          PsiElement target = ref.resolve();
          //TODO: including overriding!
          if (bundleSearchDescriptor.myElements.contains(target)) {
            return processor.execute(ref);
          }
        }

        return true;
      }
    };

    TreeElement scopeTreeElement = SourceTreeToPsiMap.psiElementToTree(scopeElement);
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    return LowLevelSearchUtil.processIdentifiersBySet(processor1,
                                                      scopeTreeElement,
                                                      IDENTIFIER_BIT_SET,
                                                      bundleSearchDescriptor.myNames,
                                                      progress);
  }

  public PsiElement[] findJoinPointsByPointcut(PsiPointcut pointcut, SearchScope searchScope) {
    return myJoinPointSearchHelper.findJoinPointsByPointcut(pointcut, searchScope);
  }

  public boolean processJoinPointsByPointcut(PsiElementProcessor processor,
                                             PsiPointcut pointcut,
                                             SearchScope searchScope) {
    return myJoinPointSearchHelper.processJoinPointsByPointcut(processor, pointcut, searchScope);
  }

  private static final TokenSet LITERAL_EXPRESSION_BIT_SET = TokenSet.create(new IElementType[]{ElementType.LITERAL_EXPRESSION});

  private boolean processElementsWithWord(PsiElementProcessorEx processor,
                                          SearchScope searchScope,
                                          String word,
                                          TokenSet elementTypes,
                                          short occurrenceMask,
                                          boolean caseInsensitive) {
    LOG.assertTrue(searchScope != null);

    if (searchScope instanceof GlobalSearchScope) {
      StringSearcher searcher = new StringSearcher(word);
      searcher.setCaseSensitive(!caseInsensitive);

      return processElementsWithWordInGlobalScope(processor,
                                                  (GlobalSearchScope)searchScope,
                                                  searcher,
                                                  elementTypes,
                                                  occurrenceMask);
    }
    else {
      LocalSearchScope _scope = (LocalSearchScope)searchScope;
      PsiElement[] scopeElements = _scope.getScope();

      for (int i = 0; i < scopeElements.length; i++) {
        final PsiElement scopeElement = scopeElements[i];
        if (!processElementsWithWordInScopeElement(scopeElement, processor, word, elementTypes, caseInsensitive)) return false;
      }
      return true;
    }
  }

  private static boolean processElementsWithWordInScopeElement(PsiElement scopeElement,
                                                        PsiElementProcessorEx processor,
                                                        String word,
                                                        TokenSet elementTypes,
                                                        boolean caseInsensitive) {
    if (SourceTreeToPsiMap.hasTreeElement(scopeElement)) {
      StringSearcher searcher = new StringSearcher(word);
      searcher.setCaseSensitive(!caseInsensitive);

      return LowLevelSearchUtil.processElementsContainingWordInElement(processor,
                                                                       scopeElement,
                                                                       searcher,
                                                                       elementTypes,
                                                                       null);
    }
    else {
      return true;
    }
  }

  private boolean processElementsWithWordInGlobalScope(PsiElementProcessorEx processor,
                                                       GlobalSearchScope scope,
                                                       StringSearcher searcher,
                                                       TokenSet elementTypes,
                                                       short occurrenceMask) {

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.pushState();
      progress.setText("Scanning files...");
    }
    myManager.startBatchFilesProcessingMode();

    try {
      PsiFile[] files = myManager.getCacheManager().getFilesWithWord(searcher.getPattern(), occurrenceMask, scope);

      if (progress != null) {
        progress.setText("Searching for " + searcher.getPattern() + "...");
      }

      for (int i = 0; i < files.length; i++) {
        ProgressManager.getInstance().checkCanceled();

        PsiFile file = files[i];
        PsiFile[] psiRoots = file.getPsiRoots();
        for (int j = 0; j < psiRoots.length; j++) {
          PsiFile psiRoot = psiRoots[j];
          if (!LowLevelSearchUtil.processElementsContainingWordInElement(processor, psiRoot, searcher, elementTypes, progress)) {
            return false;
          }
        }

        if (progress != null) {
          double fraction = (double)i / files.length;
          progress.setFraction(fraction);
        }

        myManager.dropResolveCaches();
      }
    }
    finally {
      if (progress != null) {
        progress.popState();
      }
      myManager.finishBatchFilesProcessingMode();
    }

    return true;
  }

  public PsiFile[] findFilesWithPlainTextWords(String word) {
    return myManager.getCacheManager().getFilesWithWord(word,
                                                        WordInfo.PLAIN_TEXT,
                                                        GlobalSearchScope.projectScope(myManager.getProject()));
  }

  private static PsiReference[] findReferencesInNonJavaFile(PsiFile file, final PsiElement element, String refText) {
    class MyRefsProcessor implements PsiNonJavaFileReferenceProcessor {
      private List<PsiReference> myRefs = new ArrayList<PsiReference>();

      PsiReference[] getResult() {
        return myRefs.toArray(new PsiReference[myRefs.size()]);
      }

      public boolean process(PsiFile file, int startOffset, int endOffset) {
        PsiElement elementAt = file.findElementAt(startOffset);
        if (elementAt != null) {
          PsiReference ref = elementAt.findReferenceAt(startOffset - elementAt.getTextRange().getStartOffset());
          if (ref != null && ref.isReferenceTo(element)) myRefs.add(ref);
        }

        return true;
      }
    }

    MyRefsProcessor processor = new MyRefsProcessor();

    char[] text = file.textToCharArray();
    StringSearcher searcher = new StringSearcher(refText);
    for (int index = LowLevelSearchUtil.searchWord(text, 0, text.length, searcher); index >= 0;) {
      if (!processor.process(file, index, index + searcher.getPattern().length())) break;
      index = LowLevelSearchUtil.searchWord(text, index + searcher.getPattern().length(), text.length, searcher);
    }
    return processor.getResult();
  }


  public void processUsagesInNonJavaFiles(String qName,
                                          PsiNonJavaFileReferenceProcessor processor,
                                          GlobalSearchScope searchScope) {
    ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator progress = progressManager.getProgressIndicator();

    int dotIndex = qName.lastIndexOf('.');
    int dollarIndex = qName.lastIndexOf('$');
    int maxIndex = Math.max(dotIndex, dollarIndex);
    String wordToSearch = maxIndex >= 0 ? qName.substring(maxIndex + 1) : qName;
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(wordToSearch, WordInfo.PLAIN_TEXT, searchScope);

    StringSearcher searcher = new StringSearcher(qName);
    searcher.setCaseSensitive(true);
    searcher.setForwardDirection(true);

    if (progress != null) {
      progress.pushState();
      progress.setText("Analyzing usages in non-java files...");
    }

    AllFilesLoop:
    for (int i = 0; i < files.length; i++) {
      ProgressManager.getInstance().checkCanceled();

      PsiFile psiFile = files[i];
      char[] text = psiFile.textToCharArray();
      for (int index = LowLevelSearchUtil.searchWord(text, 0, text.length, searcher); index >= 0;) {
        PsiReference referenceAt = psiFile.findReferenceAt(index);
        if (referenceAt == null || referenceAt.isSoft()) { //?
          if (!processor.process(psiFile, index, index + searcher.getPattern().length())) break AllFilesLoop;
        }

        index = LowLevelSearchUtil.searchWord(text, index + searcher.getPattern().length(), text.length, searcher);
      }

      if (progress != null) {
        progress.setFraction((double)(i + 1) / files.length);
      }
    }

    if (progress != null) {
      progress.popState();
    }
  }

  public SearchScope getAccessScope(PsiElement element) {
    return PsiSearchScopeUtil.getAccessScope(element);
  }

  public PsiFile[] findFormsBoundToClass(String className) {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myManager.getProject());
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(className, WordInfo.GUI_FORM_CLASS_NAME,
                                                                   projectScope);
    List<PsiFile> boundForms = new ArrayList<PsiFile>(files.length);
    for (int i = 0; i < files.length; i++) {
      PsiFile psiFile = files[i];
      LOG.assertTrue(psiFile.getFileType() == StdFileTypes.GUI_DESIGNER_FORM);
      String text = psiFile.getText();
      try {
        LwRootContainer container = Utils.getRootContainer(text, null);
        if (className.equals(container.getClassToBind())) boundForms.add(psiFile);
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    }

    return boundForms.toArray(new PsiFile[boundForms.size()]);
  }

  public boolean isFieldBoundToForm(PsiField field) {
    PsiClass aClass = field.getContainingClass();
    if (aClass != null && aClass.getQualifiedName() != null) {
      PsiFile[] formFiles = findFormsBoundToClass(aClass.getQualifiedName());
      for (int i = 0; i < formFiles.length; i++) {
        PsiFile file = formFiles[i];
        final PsiReference[] references = file.getReferences();
        for (int j = 0; j < references.length; j++) {
          final PsiReference reference = references[j];
          if (reference.isReferenceTo(field)) return true;
        }
      }
    }

    return false;
  }

  public void processAllFilesWithWord(String word, GlobalSearchScope scope, FileSink sink) {
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(word, WordInfo.IN_CODE, scope);

    for (int i = 0; i < files.length; i++) {
      sink.foundFile(files[i]);
    }
  }

  public void processAllFilesWithWordInComments(String word, GlobalSearchScope scope, FileSink sink) {
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(word, WordInfo.IN_COMMENTS, scope);

    for (int i = 0; i < files.length; i++) {
      sink.foundFile(files[i]);
    }
  }

  public void processAllFilesWithWordInLiterals(String word, GlobalSearchScope scope, FileSink sink) {
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(word, WordInfo.IN_STRING_LITERALS, scope);

    for (int i = 0; i < files.length; i++) {
      sink.foundFile(files[i]);
    }
  }

  private static PsiMethod[] getOverloadsMayBeOverriden(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return new PsiMethod[]{method};
    PsiMethod[] methods = aClass.findMethodsByName(method.getName(), false);
    List<PsiMethod> result = new ArrayList<PsiMethod>();
    for (int i = 0; i < methods.length; i++) {
      PsiMethod psiMethod = methods[i];
      PsiModifierList modList = psiMethod.getModifierList();
      if (!modList.hasModifierProperty(PsiModifier.STATIC) &&
          !modList.hasModifierProperty(PsiModifier.FINAL)) {
        result.add(psiMethod);
      }
    }

    //Should not happen
    if (result.size() == 0) return new PsiMethod[]{method};

    return result.toArray(new PsiMethod[result.size()]);
  }
}
