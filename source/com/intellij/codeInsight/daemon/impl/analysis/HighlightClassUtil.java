/*
 * Checks and Highlights problems with classes
 * User: cdr
 * Date: Aug 19, 2002
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.RefCountHolder;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class HighlightClassUtil {
  public static final String INTERFACE_EXPECTED = "Interface expected here";
  public static final String CLASS_EXPECTED = "No interface expected here";
  public static final String NO_IMPLEMENTS_ALLOWED = "No implements clause allowed for interface";
  private static final String STATIC_DECLARATION_IN_INNER_CLASS = "Inner classes cannot have static declarations";
  public static final String CLASS_MUST_BE_ABSTRACT = "Class ''{0}'' is not abstract and does not implement abstract method ''{1}'' in ''{2}''";
  public static final String DUPLICATE_CLASS = "Duplicate class: ''{0}''";
  private static final String REFERENCED_FROM_STATIC_CONTEXT = "''{0}'' cannot be referenced from a static context";

  //@top
  /**
   * new ref(...) or new ref(..) { ... } where ref is abstract class
   */
  static HighlightInfo checkAbstractInstantiation(PsiJavaCodeReferenceElement ref) {
    final PsiElement parent = ref.getParent();
    HighlightInfo highlightInfo = null;
    if (parent instanceof PsiNewExpression && !PsiUtil.hasErrorElementChild(parent)) {
      if (((PsiNewExpression)parent).getType() instanceof PsiArrayType) return null;
      PsiElement refElement = ref.resolve();
      if (refElement instanceof PsiClass) {
        highlightInfo = checkInstantiationOfAbstractClass((PsiClass)refElement, ref);
      }
    }
    else if (parent instanceof PsiAnonymousClass
             && parent.getParent() instanceof PsiNewExpression
             && !PsiUtil.hasErrorElementChild(parent.getParent())) {
      PsiAnonymousClass aClass = (PsiAnonymousClass)parent;
      final MethodSignatureUtil.MethodSignatureToMethods allMethods = MethodSignatureUtil.getSameSignatureMethods(aClass);
      final PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(aClass, allMethods);

      if (abstractMethod != null && abstractMethod.getContainingClass() != null) {
        String baseClassName = HighlightUtil.formatClass((PsiClass)parent, false);
        String methodName = HighlightUtil.formatMethod(abstractMethod);
        String message = MessageFormat.format(CLASS_MUST_BE_ABSTRACT,
                                              new Object[]{
                                                baseClassName,
                                                methodName,
                                                HighlightUtil.formatClass(abstractMethod.getContainingClass(), false)
                                              });
        highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                          ref,
                                                          message);
        if (ClassUtil.getAnyMethodToImplement(aClass, allMethods) != null) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new ImplementMethodsFix(aClass));
        }
      }
    }
    return highlightInfo;
  }

  //@top
  public static HighlightInfo checkInstantiationOfAbstractClass(PsiClass aClass, PsiElement highlighElement) {
    HighlightInfo errorResult = null;
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      String baseClassName = aClass.getName();
      String message = MessageFormat.format("''{0}'' is abstract; cannot be instantiated", new Object[]{baseClassName});
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, highlighElement, message);
      if (!aClass.isInterface() && ClassUtil.getAnyAbstractMethod(aClass, MethodSignatureUtil.getSameSignatureMethods(aClass)) == null) {
        // suggest to make not abstract only if possible
        QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(aClass, PsiModifier.ABSTRACT, false));
      }
    }
    return errorResult;
  }

  //@top
  static HighlightInfo checkClassMustBeAbstract(PsiClass aClass) {
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) || aClass.getRBrace() == null ||
        (aClass.isEnum() && hasEnumConstants(aClass))) {
      return null;
    }
    HighlightInfo errorResult = null;
    final MethodSignatureUtil.MethodSignatureToMethods allMethods = MethodSignatureUtil.getSameSignatureMethods(aClass);
    PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(aClass, allMethods);
    if (abstractMethod != null) {
      String message = MessageFormat.format(CLASS_MUST_BE_ABSTRACT,
                                            new Object[]{
                                              HighlightUtil.formatClass(aClass, false),
                                              HighlightUtil.formatMethod(abstractMethod),
                                              HighlightUtil.formatClass(abstractMethod.getContainingClass())
                                            });
      final TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                      textRange,
                                                      message);
      if (ClassUtil.getAnyMethodToImplement(aClass, allMethods) != null) {
        QuickFixAction.registerQuickFixAction(errorResult, new ImplementMethodsFix(aClass));
      }
      if (!(aClass instanceof PsiAnonymousClass)
          && HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, aClass.getModifierList()) == null) {
        QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(aClass, PsiModifier.ABSTRACT, true));
      }
    }
    return errorResult;
  }

  private static boolean hasEnumConstants(final PsiClass aClass) {
    final PsiField[] fields = aClass.getFields();
    for (int i = 0; i < fields.length; i++) {
      if (fields[i] instanceof PsiEnumConstant) return true;
    }
    return false;
  }

  //@top
  static HighlightInfo checkDuplicateTopLevelClass(final PsiClass aClass) {
    if (!(aClass.getParent() instanceof PsiFile)) return null;
    String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) return null;
    int numOfClassesToFind = 2;
    if (qualifiedName.indexOf("$") != -1) {
      qualifiedName = qualifiedName.replaceAll("\\$", ".");
      numOfClassesToFind = 1;
    }
    PsiManager manager = aClass.getManager();
    Module module = ModuleUtil.findModuleForPsiElement(aClass);
    if (module == null) return null;

    final PsiClass[] classes = manager.findClasses(qualifiedName, GlobalSearchScope.moduleScope(module));
    if (classes.length < numOfClassesToFind) return null;
    String dupFileName = null;
    for (int i = 0; i < classes.length; i++) {
      PsiClass dupClass = classes[i];
      // do not use equals
      if (dupClass != aClass) {
        final VirtualFile file = dupClass.getContainingFile().getVirtualFile();
        if (file != null && manager.isInProject(dupClass)) {
          dupFileName = FileUtil.toSystemDependentName(file.getPath());
          break;
        }
      }
    }
    if (dupFileName == null) return null;
    String message = MessageFormat.format("Duplicate class found in the file ''{0}''", new Object[]{dupFileName});
    final TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);
    final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);

    return highlightInfo;
  }

  //@top
  static HighlightInfo checkDuplicateNestedClass(final PsiClass aClass) {
    if (aClass == null) return null;
    PsiElement parent = aClass;
    if (aClass.getParent() instanceof PsiDeclarationStatement) {
      parent = aClass.getParent();
    }
    final String name = aClass.getName();
    if (name == null) return null;
    boolean duplicateFound = false;
    boolean checkSiblings = true;
    while (parent != null) {
      if (parent instanceof PsiFile) break;
      PsiElement element = checkSiblings ? parent.getPrevSibling() : null;
      if (element == null) {
        element = parent.getParent();
        // JLS 14.3:
        // The name of a local class C may not be redeclared
        //  as a local class of the directly enclosing method, constructor, or initializer block within the scope of C
        // , or a compile-time error occurs.
        //  However, a local class declaration may be shadowed (?6.3.1)
        //  anywhere inside a class declaration nested within the local class declaration's scope.
        if (element instanceof PsiMethod || element instanceof PsiClass) {
          checkSiblings = false;
        }
      }
      parent = element;

      if (element instanceof PsiDeclarationStatement) element = PsiTreeUtil.getChildOfType(element, PsiClass.class);
      if (element instanceof PsiClass && name.equals(((PsiClass)element).getName())) {
        duplicateFound = true;
        break;
      }
    }

    if (duplicateFound) {
      String message = MessageFormat.format(DUPLICATE_CLASS, new Object[]{name});
      final TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
    }
    return null;
  }


  //@top
  static HighlightInfo checkPublicClassInRightFile(PsiKeyword keyword, PsiModifierList psiModifierList) {
    // todo most testcase classes located in wrong files
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    if (new PsiMatcherImpl(keyword)
      .dot(PsiMatcherImpl.hasText(PsiModifier.PUBLIC))
      .parent(PsiMatcherImpl.hasClass(PsiModifierList.class))
      .parent(PsiMatcherImpl.hasClass(PsiClass.class))
      .parent(PsiMatcherImpl.hasClass(PsiJavaFile.class))
      .getElement() == null) {
      return null;
    }
    PsiClass aClass = (PsiClass)keyword.getParent().getParent();
    PsiJavaFile file = (PsiJavaFile)aClass.getContainingFile();
    final VirtualFile virtualFile = file.getVirtualFile();
    HighlightInfo errorResult = null;
    if (virtualFile != null && !aClass.getName().equals(virtualFile.getNameWithoutExtension())) {
      String message = MessageFormat.format("Class ''{0}'' is public, should be declared in a file named ''{0}.java''",
                                            new Object[]{aClass.getName()});
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                      aClass.getNameIdentifier(),
                                                      message);
      QuickFixAction.registerQuickFixAction(errorResult,
                                            new ModifierFix(psiModifierList, PsiModifier.PUBLIC, false));
      PsiClass[] classes = file.getClasses();
      if (classes.length > 1) {
        QuickFixAction.registerQuickFixAction(errorResult, new MoveClassToSeparateFileFix(aClass));
      }
      for (int i = 0; i < classes.length; i++) {
        PsiClass otherClass = classes[i];
        if (!otherClass.getManager().areElementsEquivalent(otherClass, aClass) && otherClass.hasModifierProperty(PsiModifier.PUBLIC)
            && otherClass.getName().equals(virtualFile.getNameWithoutExtension())) {
          return errorResult;
        }
      }
      QuickFixAction.registerQuickFixAction(errorResult, new RenameFileFix(aClass.getName()));
      QuickFixAction.registerQuickFixAction(errorResult, new RenamePublicClassFix(aClass));
    }
    return errorResult;
  }

  //@top
  private static HighlightInfo checkStaticFieldDeclarationInInnerClass(PsiKeyword keyword) {
    if (new PsiMatcherImpl(keyword)
      .dot(PsiMatcherImpl.hasText(PsiModifier.STATIC))
      .parent(PsiMatcherImpl.hasClass(PsiModifierList.class))
      .parent(PsiMatcherImpl.hasClass(PsiField.class))
      .parent(PsiMatcherImpl.hasClass(PsiClass.class))
      .dot(PsiMatcherImpl.hasModifier(PsiModifier.STATIC, false))
      .parent(PsiMatcherImpl.hasClass(new Class[]{PsiClass.class, PsiDeclarationStatement.class, PsiNewExpression.class, PsiEnumConstant.class}))
      .getElement() == null) {
      return null;
    }
    final PsiField field = (PsiField)keyword.getParent().getParent();
    if (PsiUtil.hasErrorElementChild(field)) return null;
    // except compile time constants
    if (PsiUtil.isCompileTimeConstant(field)) {
      return null;
    }
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                  keyword,
                                                                  STATIC_DECLARATION_IN_INNER_CLASS);
    QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(field, PsiModifier.STATIC, false));
    final PsiModifierList classModifiers = ((PsiClass)field.getParent()).getModifierList();
    QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(classModifiers, PsiModifier.STATIC, true));
    return errorResult;
  }

  //@top
  private static HighlightInfo checkStaticMethodDeclarationInInnerClass(PsiKeyword keyword) {
    if (new PsiMatcherImpl(keyword)
      .dot(PsiMatcherImpl.hasText(PsiModifier.STATIC))
      .parent(PsiMatcherImpl.hasClass(PsiModifierList.class))
      .parent(PsiMatcherImpl.hasClass(PsiMethod.class))
      .parent(PsiMatcherImpl.hasClass(PsiClass.class))
      .dot(PsiMatcherImpl.hasModifier(PsiModifier.STATIC, false))
      .parent(PsiMatcherImpl.hasClass(new Class[]{PsiClass.class, PsiDeclarationStatement.class, PsiNewExpression.class, PsiEnumConstant.class}))
      .getElement() == null) {
      return null;
    }
    PsiMethod method = (PsiMethod)keyword.getParent().getParent();
    if (PsiUtil.hasErrorElementChild(method)) return null;
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                  keyword,
                                                                  STATIC_DECLARATION_IN_INNER_CLASS);
    QuickFixAction.registerQuickFixAction(errorResult,
                                          new ModifierFix(method, PsiModifier.STATIC, false));
    final PsiModifierList classModifiers = ((PsiClass)keyword.getParent().getParent().getParent()).getModifierList();
    QuickFixAction.registerQuickFixAction(errorResult,
                                          new ModifierFix(classModifiers, PsiModifier.STATIC, true));
    return errorResult;
  }

  //@top
  private static HighlightInfo checkStaticInitializerDeclarationInInnerClass(PsiKeyword keyword) {
    if (new PsiMatcherImpl(keyword)
      .dot(PsiMatcherImpl.hasText(PsiModifier.STATIC))
      .parent(PsiMatcherImpl.hasClass(PsiModifierList.class))
      .parent(PsiMatcherImpl.hasClass(PsiClassInitializer.class))
      .parent(PsiMatcherImpl.hasClass(PsiClass.class))
      .dot(PsiMatcherImpl.hasModifier(PsiModifier.STATIC, false))
      .parent(PsiMatcherImpl.hasClass(new Class[]{PsiClass.class, PsiDeclarationStatement.class, PsiNewExpression.class, PsiEnumConstant.class}))
      .getElement() == null) {
      return null;
    }
    PsiClassInitializer initializer = (PsiClassInitializer)keyword.getParent().getParent();
    if (PsiUtil.hasErrorElementChild(initializer)) return null;
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                  keyword,
                                                                  STATIC_DECLARATION_IN_INNER_CLASS);
    QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(initializer, PsiModifier.STATIC, false));
    final PsiModifierList classModifiers = ((PsiClass)keyword.getParent().getParent().getParent()).getModifierList();
    QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(classModifiers, PsiModifier.STATIC, true));
    return errorResult;
  }

  //@top
  private static HighlightInfo checkStaticClassDeclarationInInnerClass(PsiKeyword keyword) {
    // keyword points to 'class' or 'interface' or 'enum'
    if (new PsiMatcherImpl(keyword)
      .parent(PsiMatcherImpl.hasClass(PsiClass.class))
      .dot(PsiMatcherImpl.hasModifier(PsiModifier.STATIC, true))
      .parent(PsiMatcherImpl.hasClass(PsiClass.class))
      .dot(PsiMatcherImpl.hasModifier(PsiModifier.STATIC, false))
      .parent(PsiMatcherImpl.hasClass(new Class[]{PsiClass.class, PsiDeclarationStatement.class, PsiNewExpression.class, PsiEnumConstant.class}))
      .getElement() == null) {
      return null;
    }
    PsiClass aClass = (PsiClass)keyword.getParent();
    if (PsiUtil.hasErrorElementChild(aClass)) return null;
    // highlight 'static' keyword if any, or class or interface if not
    PsiElement context = null;
    final PsiModifierList modifierList = aClass.getModifierList();
    final PsiElement[] children = modifierList.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement element = children[i];
      if (Comparing.equal(element.getText(), PsiModifier.STATIC)) {
        context = element;
        break;
      }
    }
    TextRange textRange = context == null ? null : context.getTextRange();
    if (textRange == null) {
      textRange = ClassUtil.getClassDeclarationTextRange(aClass);
    }
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, STATIC_DECLARATION_IN_INNER_CLASS);
    if (context != keyword) {
      QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(aClass, PsiModifier.STATIC, false));
    }
    QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(aClass.getContainingClass(), PsiModifier.STATIC, true));
    return errorResult;
  }

  //@top
  static HighlightInfo checkStaticDeclarationInInnerClass(PsiKeyword keyword) {
    HighlightInfo errorResult = checkStaticFieldDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticMethodDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticClassDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticInitializerDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    return null;
  }

  //@top
  static HighlightInfo checkImplementsAllowed(PsiReferenceList list) {
    if (list.getParent() instanceof PsiClass) {
      PsiClass aClass = (PsiClass)list.getParent();
      if (aClass.isInterface()) {
        boolean isImplements = list.equals(aClass.getImplementsList());
        if (isImplements) {
          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, NO_IMPLEMENTS_ALLOWED);
        }
      }
    }
    return null;
  }

  //@top
  static HighlightInfo checkExtendsClassAndImplementsInterface(PsiReferenceList referenceList,
                                                               ResolveResult resolveResult,
                                                               PsiJavaCodeReferenceElement context) {
    PsiClass aClass = (PsiClass)referenceList.getParent();
    boolean isImplements = referenceList.equals(aClass.getImplementsList());
    boolean isInterface = aClass.isInterface();
    if (isInterface && isImplements) return null;
    boolean mustBeInterface = isImplements || isInterface;
    HighlightInfo errorResult = null;
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom.isInterface() != mustBeInterface) {
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                      context,
                                                      mustBeInterface ? INTERFACE_EXPECTED : CLASS_EXPECTED);
      PsiClassType type = aClass.getManager().getElementFactory().createType(extendFrom, resolveResult.getSubstitutor());
      QuickFixAction.registerQuickFixAction(errorResult, new ChangeExtendsToImplementsFix(aClass, type));
    }
    return errorResult;
  }

  //@top
  static HighlightInfo checkCannotInheritFromFinal(PsiClass superClass, PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    if (superClass.hasModifierProperty(PsiModifier.FINAL) || superClass.isEnum()) {
      String message = MessageFormat.format("Cannot inherit from final ''{0}''", new Object[]{superClass.getQualifiedName()});
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, elementToHighlight, message);
      QuickFixAction.registerQuickFixAction(errorResult, new ModifierFix(superClass, PsiModifier.FINAL, false));
    }
    return errorResult;
  }

  //@top
  static HighlightInfo checkAnonymousInheritFinal(PsiNewExpression expression) {
    final PsiAnonymousClass aClass = PsiTreeUtil.getChildOfType(expression, PsiAnonymousClass.class);
    if (aClass == null) return null;
    final PsiClassType baseClassReference = aClass.getBaseClassType();
    if (baseClassReference == null) return null;
    PsiClass baseClass = baseClassReference.resolve();
    if (baseClass == null) return null;
    return checkCannotInheritFromFinal(baseClass, aClass.getBaseClassReference());
  }

  //@top
  public static HighlightInfo checkPackageNameConformsToDirectoryName(PsiPackageStatement statement) {
    if (!DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile().isToolEnabled(HighlightDisplayKey.WRONG_PACKAGE_STATEMENT)) {
      return null;
    }

    // does not work in tests since CodeInsightTestCase copies file into temporary location
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    final PsiJavaFile file = (PsiJavaFile)statement.getContainingFile();
    final PsiDirectory directory = file.getContainingDirectory();
    if (directory == null) return null;
    final PsiPackage dirPackage = directory.getPackage();
    if (dirPackage == null) return null;
    final PsiPackage classPackage = (PsiPackage)statement.getPackageReference().resolve();
    if (!Comparing.equal(dirPackage.getQualifiedName(), statement.getPackageReference().getText(), true)) {
      String description = MessageFormat.format("Package name ''{0}'' does not correspond to the file path ''{1}''",
                                                new Object[]{statement.getPackageReference().getText(), dirPackage.getQualifiedName()});

      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_PACKAGE_STATEMENT,
                                                                            statement,
                                                                            description);
      if (classPackage != null) QuickFixAction.registerQuickFixAction(highlightInfo, new MoveToPackageFix(file, classPackage));
      QuickFixAction.registerQuickFixAction(highlightInfo, new AdjustPackageNameFix(file, statement, dirPackage));
      return highlightInfo;
    }
    return null;
  }

  //@top
  public static HighlightInfo checkMissingPackageStatement(PsiClass aClass) {
    if (!DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile().isToolEnabled(HighlightDisplayKey.WRONG_PACKAGE_STATEMENT)) {
      return null;
    }

    // does not work in tests since CodeInsightTestCase copies file into temporary location
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    final PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;
    final PsiJavaFile javaFile = (PsiJavaFile)file;
    // highlight the first class in the file only
    final PsiClass[] classes = javaFile.getClasses();
    if (classes == null || classes.length == 0 || !aClass.getManager().areElementsEquivalent(aClass, classes[0])) return null;
    final PsiDirectory directory = javaFile.getContainingDirectory();
    if (directory == null) return null;
    final PsiPackage dirPackage = directory.getPackage();
    if (dirPackage == null) return null;
    final PsiPackageStatement packageStatement = javaFile.getPackageStatement();

    final String packageName = dirPackage.getQualifiedName();
    if (!Comparing.strEqual(packageName, "", true) && packageStatement == null) {
      String description = MessageFormat.format("Missing package statement: ''{0}''",
                                                new Object[]{packageName});
      final TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);

      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_PACKAGE_STATEMENT,
                                                                            textRange,
                                                                            description);
      QuickFixAction.registerQuickFixAction(highlightInfo, new AdjustPackageNameFix(javaFile, null, dirPackage));
      return highlightInfo;
    }
    return null;
  }

  //@top
  private static String checkDefaultConstructorThrowsException(PsiMethod constructor, PsiClassType[] handledExceptions) {
    final PsiClassType[] referencedTypes = constructor.getThrowsList().getReferencedTypes();
    List<PsiClassType> exceptions = new ArrayList<PsiClassType>();
    for (int i = 0; i < referencedTypes.length; i++) {
      PsiClassType referencedType = referencedTypes[i];
      if (!ExceptionUtil.isUncheckedException(referencedType) && !ExceptionUtil.isHandledBy(referencedType, handledExceptions)) {
        exceptions.add(referencedType);
      }
    }
    if (exceptions.size() != 0) {
      return HighlightUtil.getUnhandledExceptionsDescriptor(exceptions.toArray(new PsiClassType[exceptions.size()]));
    }
    return null;
  }

  //@top
  public static HighlightInfo checkClassDoesNotCallSuperConstructorOrHandleExceptions(final PsiClass aClass,
                                                                                      RefCountHolder refCountHolder) {
    if (aClass.isEnum()) return null;
    // check only no-ctr classes. Problem with specific constructor will be highlighted inside it
    if (aClass.getConstructors().length != 0) return null;
    // find no-args base class ctr
    final TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);
    return checkBaseClassDefaultConstructorProblem(aClass, refCountHolder, textRange, PsiClassType.EMPTY_ARRAY);
  }

  public static HighlightInfo checkBaseClassDefaultConstructorProblem(PsiClass aClass,
                                                                      RefCountHolder refCountHolder,
                                                                      TextRange textRange,
                                                                      PsiClassType[] handledExceptions) {
    final PsiClass baseClass = aClass.getSuperClass();
    if (baseClass == null) return null;
    final PsiMethod[] constructors = baseClass.getConstructors();
    if (constructors.length == 0) return null;

    PsiResolveHelper resolveHelper = aClass.getManager().getResolveHelper();
    for (int j = 0; j < constructors.length; j++) {
      PsiMethod constructor = constructors[j];
      if (resolveHelper.isAccessible(constructor, aClass, null)) {
        if (constructor.getParameterList().getParameters().length == 0 ||
            constructor.getParameterList().getParameters().length == 1 && constructor.isVarArgs()
        ) {
          // it is an error if base ctr throws exceptions
          String description = checkDefaultConstructorThrowsException(constructor, handledExceptions);
          if (description != null) {
            HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
            return info;
          }
          if (refCountHolder != null) {
            refCountHolder.registerReference(aClass, new MethodCandidateInfo(constructor, PsiSubstitutor.EMPTY));
          }
          return null;
        }
      }
    }

    String description = MessageFormat.format(HighlightUtil.NO_DEFAULT_CONSTRUCTOR, new Object[]{HighlightUtil.formatClass(baseClass)});
    HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
    QuickFixAction.registerQuickFixAction(info, new CreateConstructorMatchingSuperAction(aClass));

    return info;
  }

  //@top
  static HighlightInfo checkInterfaceCannotBeLocal(PsiClass aClass) {
    if (PsiUtil.isLocalClass(aClass)) {
      TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               textRange,
                                               "Modifier 'interface' not allowed here");
    }
    return null;
  }

  //@top
  public static HighlightInfo checkCyclicInheritance(PsiClass aClass) {
    PsiClass circularClass = getCircularClass(aClass, new HashSet<PsiClass>());
    if (circularClass != null) {
      String description = MessageFormat.format("Cyclic inheritance involving ''{0}''",
                                                new Object[]{HighlightUtil.formatClass(circularClass)});
      final TextRange textRange = ClassUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
    }
    return null;
  }

  private static PsiClass getCircularClass(final PsiClass aClass, Collection<PsiClass> usedClasses) {
    if (usedClasses.contains(aClass)) {
      return aClass;
    }
    try {
      usedClasses.add(aClass);
      final PsiClass[] superTypes = aClass.getSupers();
      for (int i = 0; i < superTypes.length; i++) {
        PsiElement superType = superTypes[i];
        while (superType instanceof PsiClass) {
          if (!"java.lang.Object".equals(((PsiClass)superType).getQualifiedName())) {
            final PsiClass circularClass = getCircularClass((PsiClass)superType, usedClasses);
            if (circularClass != null) return circularClass;
          }
          // check class qualifier
          superType = superType.getParent();
        }
      }
    }
    finally {
      usedClasses.remove(aClass);
    }
    return null;
  }

  //@top
  public static HighlightInfo checkExtendsDuplicate(PsiJavaCodeReferenceElement element, PsiElement resolved) {
    if (!(element.getParent() instanceof PsiReferenceList)) return null;
    PsiReferenceList list = (PsiReferenceList)element.getParent();
    if (!(list.getParent() instanceof PsiClass)) return null;
    if (!(resolved instanceof PsiClass)) return null;
    PsiClass aClass = (PsiClass)resolved;
    final PsiClassType[] referencedTypes = list.getReferencedTypes();
    int dupCount = 0;
    for (int i = 0; i < referencedTypes.length; i++) {
      PsiClassType referencedType = referencedTypes[i];
      PsiClass resolvedElement = referencedType.resolve();
      if (resolvedElement != null && list.getManager().areElementsEquivalent(resolvedElement, aClass)) {
        dupCount++;
      }
    }
    if (dupCount > 1) {
      String description = MessageFormat.format(DUPLICATE_CLASS,
                                                new Object[]{HighlightUtil.formatClass(aClass)});
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                            element,
                                                                            description);
      return highlightInfo;
    }
    return null;
  }

  //@top
  public static HighlightInfo checkClassAlreadyImported(PsiClass aClass, PsiElement elementToHighlight) {
    final PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;
    final PsiJavaFile javaFile = (PsiJavaFile)file;
    // check only top-level classes conflicts
    if (aClass.getParent() != javaFile) return null;
    final PsiImportStatementBase[] importStatements = javaFile.getImportList().getAllImportStatements();
    for (int i = 0; i < importStatements.length; i++) {
      PsiImportStatementBase importStatement = importStatements[i];
      if (importStatement.isOnDemand()) continue;
      PsiElement resolved = importStatement.resolve();
      if (resolved instanceof PsiClass && Comparing.equal(aClass.getName(), ((PsiClass)resolved).getName(), true)) {
        String description = MessageFormat.format("''{0}'' is already defined in this compilation unit",
                                                  new Object[]{HighlightUtil.formatClass(aClass)});
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 elementToHighlight,
                                                 description);
      }
    }
    return null;
  }

  //@top
  public static HighlightInfo checkClassExtendsOnlyOneClass(PsiReferenceList list) {
    final PsiClassType[] referencedTypes = list.getReferencedTypes();
    final PsiElement parent = list.getParent();
    if (!(parent instanceof PsiClass)) return null;

    final PsiClass aClass = (PsiClass)parent;
    if (!aClass.isInterface()
        && referencedTypes.length > 1
        && aClass.getExtendsList() == list) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               list,
                                               "Class cannot extend multiple classes");
    }

    return null;
  }

  //@top
  public static HighlightInfo checkThingNotAllowedInInterface(PsiElement element, PsiClass aClass) {
    if (aClass == null || !aClass.isInterface()) return null;
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                             element,
                                             "Not allowed in interface");
  }

  //@top
  public static HighlightInfo checkQualifiedNewOfStaticClass(final PsiNewExpression expression) {
    final PsiExpression qualifier = expression.getQualifier();
    if (qualifier == null) return null;
    final PsiType type = expression.getType();
    PsiClass aClass = PsiUtil.resolveClassInType(type);
    if (aClass != null && aClass.hasModifierProperty(PsiModifier.STATIC)) {
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                            expression,
                                                                            "Qualified new of static class");
      if (!aClass.isEnum()) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new ModifierFix(aClass, PsiModifier.STATIC, false));
        QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveNewQualifierFix(expression, aClass));
      }
      return highlightInfo;
    }
    return null;
  }

  //@top
  /**
   * class c extends foreign.inner {}
   *
   * @param extendRef points to the class in the extends list
   * @param resolved  extendRef resolved
   */
  public static HighlightInfo checkClassExtendsForeignInnerClass(PsiJavaCodeReferenceElement extendRef, PsiElement resolved) {
    if (!(extendRef.getParent() instanceof PsiReferenceList)) {
      return null;
    }
    if (!(extendRef.getParent().getParent() instanceof PsiClass &&
          ((PsiClass)extendRef.getParent().getParent()).getExtendsList() == extendRef.getParent())) {
      return null;
    }
    if (!(resolved instanceof PsiClass)) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, extendRef, "Class name expected");
    }
    final PsiClass base = (PsiClass)resolved;
    // must be inner class
    if (!PsiUtil.isInnerClass(base)) return null;
    PsiClass baseOuter = (PsiClass)base.getParent();

    if (!hasEnclosedInstanceInScope(baseOuter, extendRef)) {
      String description = MessageFormat.format("No enclosing instance of type ''{0}'' is in scope",
                                                new Object[]{HighlightUtil.formatClass(baseOuter)});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, extendRef, description);
    }

    return null;
  }

  //@top
  static HighlightInfo checkExternalizableHasPublicNoArgsConstructor(PsiClass aClass, PsiElement context) {
    if (!isExternalizable(aClass) || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return null;
    }
    final PsiMethod[] constructors = aClass.getConstructors();
    boolean hasPublicNoArgsConstructor = constructors.length == 0;
    for (int i = 0; i < constructors.length; i++) {
      PsiMethod constructor = constructors[i];
      if (constructor.getParameterList().getParameters().length == 0 && constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
        hasPublicNoArgsConstructor = true;
        break;
      }
    }
    if (!hasPublicNoArgsConstructor) {
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WARNING,
                                                                            context,
                                                                            "Externalizable class should have public no-args constructor");
      QuickFixAction.registerQuickFixAction(highlightInfo, new AddDefaultConstructorFix(aClass));
      return highlightInfo;
    }
    return null;
  }

  public static boolean isExternalizable(PsiClass aClass) {
    PsiManager manager = aClass.getManager();
    PsiClass externalizableClass = manager.findClass("java.io.Externalizable", aClass.getResolveScope());
    if (externalizableClass == null) return false;
    return aClass.isInheritor(externalizableClass, true);
  }

  //todo topdown
  public static boolean hasEnclosedInstanceInScope(PsiClass aClass, PsiElement scope) {
    PsiElement place = scope;
    while (place != null && place != aClass && !(place instanceof PsiFile)) {
      if (place instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass)place, aClass, true)) {
        return true;
      }
      if (place instanceof PsiModifierListOwner && ((PsiModifierListOwner)place).hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      place = place.getParent();
    }
    return place == aClass;
  }

  //@top
  public static HighlightInfo checkCreateInnerClassFromStaticContext(PsiNewExpression expression) {
    final PsiType type = expression.getType();
    if (type == null || type instanceof PsiArrayType || type instanceof PsiPrimitiveType) return null;
    PsiClass aClass = PsiUtil.resolveClassInType(type);
    if (aClass == null) return null;
    if (aClass instanceof PsiAnonymousClass) {
      aClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
      if (aClass == null) return null;
    }

    if (!PsiUtil.isInnerClass(aClass)) {
      return null;
    }
    final PsiClass outerClass;
    if (aClass instanceof PsiAnonymousClass) {
      outerClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
    }
    else {
      outerClass = aClass.getContainingClass();
    }

    if (outerClass == null) return null;
    final PsiElement placeToSearchEnclosingFrom;
    if (expression.getQualifier() != null) {
      final PsiType qtype = expression.getQualifier().getType();
      placeToSearchEnclosingFrom = PsiUtil.resolveClassInType(qtype);
    }
    else {
      placeToSearchEnclosingFrom = expression;
    }
    if (hasEnclosedInstanceInScope(outerClass, placeToSearchEnclosingFrom)) return null;
    return reportIllegalEnclosingUsage(placeToSearchEnclosingFrom, aClass, outerClass, expression);
  }

  public static HighlightInfo reportIllegalEnclosingUsage(final PsiElement place,
                                                          PsiClass aClass, final PsiClass outerClass,
                                                          PsiElement elementToHighlight) {
    final PsiElement staticParent = HighlightUtil.getPossibleStaticParentElement(place, outerClass);
    if (staticParent == null) {
      String description = MessageFormat.format("''{0}'' is not an enclosing class",
                                                new Object[]{outerClass == null ? "" : HighlightUtil.formatClass(outerClass)});
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, elementToHighlight, description);
    }
    final PsiModifierList modifierList = PsiUtil.getModifierList(staticParent);
    if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
      String description = MessageFormat.format(REFERENCED_FROM_STATIC_CONTEXT,
                                                new Object[]{outerClass == null ? "" : HighlightUtil.formatClass(outerClass) + ".this"});
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, elementToHighlight, description);
      // make context not static or referenced class static
      QuickFixAction.registerQuickFixAction(highlightInfo, new ModifierFix(modifierList, PsiModifier.STATIC, false));
      if (aClass != null && HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, aClass.getModifierList()) == null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new ModifierFix(aClass, PsiModifier.STATIC, true));
      }
      return highlightInfo;
    }
    return null;
  }
}
