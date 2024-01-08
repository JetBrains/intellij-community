package com.jetbrains.python.refactoring;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.NotNullPredicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.refactoring.classes.PyDependenciesComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class PyPsiRefactoringUtil {
  /**
   * Adds element to statement list to the correct place according to its dependencies.
   *
   * @param element       to insert
   * @param statementList where element should be inserted
   * @return inserted element
   */
  public static <T extends PyElement> T addElementToStatementList(@NotNull final T element,
                                                                  @NotNull final PyStatementList statementList) {
    PsiElement before = null;
    PsiElement after = null;
    for (final PyStatement statement : statementList.getStatements()) {
      if (PyDependenciesComparator.depends(element, statement)) {
        after = statement;
      }
      else if (PyDependenciesComparator.depends(statement, element)) {
        before = statement;
      }
    }
    final PsiElement result;
    if (after != null) {

      result = statementList.addAfter(element, after);
    }
    else if (before != null) {
      result = statementList.addBefore(element, before);
    }
    else {
      result = addElementToStatementList(element, statementList, true);
    }
    @SuppressWarnings("unchecked") // Inserted element can't have different type
    final T resultCasted = (T)result;
    return resultCasted;
  }

  /**
   * Inserts specified element into the statement list either at the beginning or at its end. If new element is going to be
   * inserted at the beginning, any preceding docstrings and/or calls to super methods will be skipped.
   * Moreover if statement list previously didn't contain any statements, explicit new line and indentation will be inserted in
   * front of it.
   *
   * @param element        element to insert
   * @param statementList  statement list
   * @param toTheBeginning whether to insert element at the beginning or at the end of the statement list
   * @return actually inserted element as for {@link PsiElement#add(PsiElement)}
   */
  @NotNull
  public static PsiElement addElementToStatementList(@NotNull PsiElement element,
                                                     @NotNull PyStatementList statementList,
                                                     boolean toTheBeginning) {
    final PsiElement prevElem = PyPsiUtils.getPrevNonWhitespaceSibling(statementList);
    // If statement list is on the same line as previous element (supposedly colon), move its only statement on the next line
    if (prevElem != null && PyUtil.onSameLine(statementList, prevElem)) {
        final PsiDocumentManager manager = PsiDocumentManager.getInstance(statementList.getProject());
        final Document document = statementList.getContainingFile().getFileDocument();
        final PyStatementListContainer container = (PyStatementListContainer)statementList.getParent();
        manager.doPostponedOperationsAndUnblockDocument(document);
        final String indentation = "\n" + PyIndentUtil.getElementIndent(statementList);
        // If statement list was empty initially, we need to add some anchor statement ("pass"), so that preceding new line was not
        // parsed as following entire StatementListContainer (e.g. function). It's going to be replaced anyway.
        final String text = statementList.getStatements().length == 0 ? indentation + PyNames.PASS : indentation;
        document.insertString(statementList.getTextRange().getStartOffset(), text);
        manager.commitDocument(document);
        statementList = container.getStatementList();
    }
    final PsiElement firstChild = statementList.getFirstChild();
    if (firstChild == statementList.getLastChild() && firstChild instanceof PyPassStatement) {
      element = firstChild.replace(element);
    }
    else {
      final PyStatement[] statements = statementList.getStatements();
      if (toTheBeginning && statements.length > 0) {
        final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(statementList, PyDocStringOwner.class);
        PyStatement anchor = statements[0];
        if (docStringOwner != null && anchor instanceof PyExpressionStatement &&
            ((PyExpressionStatement)anchor).getExpression() == docStringOwner.getDocStringExpression()) {
          final PyStatement next = PsiTreeUtil.getNextSiblingOfType(anchor, PyStatement.class);
          if (next == null) {
            return statementList.addAfter(element, anchor);
          }
          anchor = next;
        }
        while (anchor instanceof PyExpressionStatement) {
          final PyExpression expression = ((PyExpressionStatement)anchor).getExpression();
          if (expression instanceof PyCallExpression) {
            final PyExpression callee = ((PyCallExpression)expression).getCallee();
            if ((PyUtil.isSuperCall((PyCallExpression)expression) || (callee != null && PyNames.INIT.equals(callee.getName())))) {
              final PyStatement next = PsiTreeUtil.getNextSiblingOfType(anchor, PyStatement.class);
              if (next == null) {
                return statementList.addAfter(element, anchor);
              }
              anchor = next;
              continue;
            }
          }
          break;
        }
        element = statementList.addBefore(element, anchor);
      }
      else {
        element = statementList.add(element);
      }
    }
    return element;
  }

  @NotNull
  public static List<PyFunction> getAllSuperAbstractMethods(@NotNull PyClass cls, @NotNull TypeEvalContext context) {
    return ContainerUtil.filter(getAllSuperMethods(cls, context), method -> isAbstractMethodForClass(method, cls, context));
  }

  private static boolean isAbstractMethodForClass(@NotNull PyFunction method, @NotNull PyClass cls, @NotNull TypeEvalContext context) {
    final String methodName = method.getName();
    if (methodName == null ||
        cls.findMethodByName(methodName, false, context) != null ||
        cls.findClassAttribute(methodName, false, context) != null) {
      return false;
    }
    final PyClass methodClass = method.getContainingClass();
    if (methodClass != null) {
      for (PyClass ancestor : cls.getAncestorClasses(context)) {
        if (ancestor.equals(methodClass)) break;
        if (ancestor.findClassAttribute(methodName, false, context) != null) return false;
      }
    }
    return method.onlyRaisesNotImplementedError() || PyKnownDecoratorUtil.hasAbstractDecorator(method, context);
  }

  /**
   * Returns all super functions available through MRO.
   */
  @NotNull
  public static List<PyFunction> getAllSuperMethods(@NotNull PyClass pyClass, @NotNull TypeEvalContext context) {
    final Map<String, PyFunction> functions = Maps.newLinkedHashMap();
    for (final PyClassLikeType type : pyClass.getAncestorTypes(context)) {
      if (type != null) {
        for (PyFunction function : PyTypeUtil.getMembersOfType(type, PyFunction.class, false, context)) {
          final String name = function.getName();
          if (name != null) {
            if (!functions.containsKey(name) || PyiUtil.isOverload(functions.get(name), context) && !PyiUtil.isOverload(function, context)) {
              functions.put(name, function);
            }
          }
        }
      }
    }
    return Lists.newArrayList(functions.values());
  }

  public static boolean isValidQualifiedName(QualifiedName name) {
    if (name == null) {
      return false;
    }
    final Collection<String> components = name.getComponents();
    if (components.isEmpty()) {
      return false;
    }
    for (String s : components) {
      if (!PyNames.isIdentifier(s) || PyNames.isReserved(s)) {
        return false;
      }
    }
    return true;
  }

  public static void insertImport(PsiElement anchor, Collection<? extends PsiNamedElement> elements) {
    for (PsiNamedElement newClass : elements) {
      insertImport(anchor, newClass);
    }
  }

  public static boolean insertImport(@NotNull PsiElement anchor, @NotNull PsiNamedElement element) {
    return insertImport(anchor, element, null);
  }

  public static boolean insertImport(@NotNull PsiElement anchor, @NotNull PsiNamedElement element, @Nullable String asName) {
    return insertImport(anchor, element, asName, PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT);
  }

  public static boolean insertImport(@NotNull PsiElement anchor,
                                     @NotNull PsiNamedElement element,
                                     @Nullable String asName,
                                     boolean preferFromImport) {
    if (PyBuiltinCache.getInstance(element).isBuiltin(element)) return false;
    final PsiFileSystemItem elementSource = element instanceof PsiDirectory ? (PsiFileSystemItem)element : element.getContainingFile();
    final PsiFile file = anchor.getContainingFile();
    if (elementSource == file || elementSource == file.getOriginalFile()) return false;
    final QualifiedName qname = QualifiedNameFinder.findCanonicalImportPath(element, anchor);
    if (qname == null || !isValidQualifiedName(qname)) {
      return false;
    }
    final QualifiedName containingQName;
    final String importedName;
    final boolean importingModuleOrPackage = element instanceof PyFile || element instanceof PsiDirectory;
    if (importingModuleOrPackage) {
      containingQName = qname.removeLastComponent();
      importedName = qname.getLastComponent();
    }
    // See PyClassRefactoringUtil.DynamicNamedElement
    else if (PyUtil.isTopLevel(element) || element instanceof LightElement) {
      containingQName = qname;
      importedName = getOriginalName(element);
    }
    else {
      return false;
    }
    final AddImportHelper.ImportPriority priority = AddImportHelper.getImportPriority(anchor, elementSource);
    if (preferFromImport && !containingQName.getComponents().isEmpty() || !importingModuleOrPackage) {
      return AddImportHelper.addOrUpdateFromImportStatement(file, containingQName.toString(), importedName, asName, priority, anchor);
    }
    else {
      return AddImportHelper.addImportStatement(file, containingQName.append(importedName).toString(), asName, priority, anchor);
    }
  }

  @Nullable
  public static String getOriginalName(@NotNull PsiNamedElement element) {
    if (element instanceof PyFile) {
      VirtualFile virtualFile = PsiUtilBase.asVirtualFile(PyUtil.turnInitIntoDir(element));
      if (virtualFile != null) {
        return virtualFile.getNameWithoutExtension();
      }
      return null;
    }
    return element.getName();
  }

  @Nullable
  public static String getOriginalName(PyImportElement element) {
    final QualifiedName qname = element.getImportedQName();
    if (qname != null && qname.getComponentCount() > 0) {
      return qname.getComponents().get(0);
    }
    return null;
  }

  /**
   * Adds expressions to superclass list
   *
   * @param project          project
   * @param clazz            class to add expressions to superclass list
   * @param paramExpressions param expressions. Like "object" or "MySuperClass". Will not add any param exp. if null.
   * @param keywordArguments keyword args like "metaclass=ABCMeta". key-value pairs.  Will not add any keyword arg. if null.
   */
  public static void addSuperClassExpressions(@NotNull final Project project,
                                              @NotNull final PyClass clazz,
                                              @Nullable final Collection<String> paramExpressions,
                                              @Nullable final Collection<Pair<String, String>> keywordArguments) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final LanguageLevel languageLevel = LanguageLevel.forElement(clazz);

    PyArgumentList superClassExpressionList = clazz.getSuperClassExpressionList();
    boolean addExpression = false;
    if (superClassExpressionList == null) {
      superClassExpressionList = generator.createFromText(languageLevel, PyClass.class, "class foo():pass").getSuperClassExpressionList();
      assert superClassExpressionList != null : "expression not created";
      addExpression = true;
    }


    generator.createFromText(LanguageLevel.PYTHON34, PyClass.class, "class foo(object, metaclass=Foo): pass").getSuperClassExpressionList();
    if (paramExpressions != null) {
      for (final String paramExpression : paramExpressions) {
        superClassExpressionList.addArgument(generator.createParameter(paramExpression));
      }
    }

    if (keywordArguments != null) {
      for (final Pair<String, String> keywordArgument : keywordArguments) {
        superClassExpressionList.addArgument(generator.createKeywordArgument(languageLevel, keywordArgument.first, keywordArgument.second));
      }
    }

    // If class has no expression list, then we need to add it manually.
    if (addExpression) {
      final ASTNode classNameNode = clazz.getNameNode(); // For nameless classes we simply add expression list directly to them
      final PsiElement elementToAddAfter = (classNameNode == null) ? clazz.getFirstChild() : classNameNode.getPsi();
      clazz.addAfter(superClassExpressionList, elementToAddAfter);
    }
  }

  /**
   * Adds class attributeName (field) if it does not exist. like __metaclass__ = ABCMeta. Or CLASS_FIELD = 42.
   *
   * @param aClass        where to add
   * @param attributeName attribute's name. Like __metaclass__ or CLASS_FIELD
   * @param value         it's value. Like ABCMeta or 42.
   * @return newly inserted attribute
   */
  @Nullable
  public static PsiElement addClassAttributeIfNotExist(
    @NotNull final PyClass aClass,
    @NotNull final String attributeName,
    @NotNull final String value) {
    if (aClass.findClassAttribute(attributeName, false, null) != null) {
      return null; //Do not add any if exist already
    }
    final PyElementGenerator generator = PyElementGenerator.getInstance(aClass.getProject());
    final String text = String.format("%s = %s", attributeName, value);
    final LanguageLevel level = LanguageLevel.forElement(aClass);

    final PyAssignmentStatement assignmentStatement = generator.createFromText(level, PyAssignmentStatement.class, text);
    //TODO: Add metaclass to the top. Add others between last attributeName and first method
    return addElementToStatementList(assignmentStatement, aClass.getStatementList(), true);
  }

  public static boolean addMetaClassIfNotExist(@NotNull PyClass cls, @NotNull PyClass metaClass, @NotNull TypeEvalContext context) {
    final String metaClassName = metaClass.getName();
    if (metaClassName == null) return false;

    final PyType metaClassType = cls.getMetaClassType(false, context);
    if (metaClassType != null) return false;

    insertImport(cls, metaClass);

    final LanguageLevel languageLevel = LanguageLevel.forElement(cls);
    if (languageLevel.isPython2()) {
      addClassAttributeIfNotExist(cls, PyNames.DUNDER_METACLASS, metaClassName);
    }
    else {
      final List<Pair<String, String>> keywordArguments = Collections.singletonList(Pair.create(PyNames.METACLASS, metaClassName));
      addSuperClassExpressions(cls.getProject(), cls, null, keywordArguments);
    }

    return true;
  }

  /**
   * Adds super classes to certain class.
   *
   * @param project      project where refactoring takes place
   * @param clazz        destination
   * @param superClasses classes to add
   */
  public static void addSuperclasses(@NotNull final Project project,
                                     @NotNull final PyClass clazz,
                                     final PyClass @NotNull ... superClasses) {

    final Collection<String> superClassNames = new ArrayList<>();


    for (final PyClass superClass : Collections2.filter(Arrays.asList(superClasses), NotNullPredicate.INSTANCE)) {
      if (superClass.getName() != null) {
        superClassNames.add(superClass.getName());
        insertImport(clazz, superClass);
      }
    }

    addSuperClassExpressions(project, clazz, superClassNames, null);
  }

  public static boolean shouldCopyAnnotations(@NotNull PsiElement copiedElement, @NotNull PsiFile destFile) {
    return !LanguageLevel.forElement(copiedElement).isPython2() &&
           (!PyiUtil.isInsideStub(copiedElement) || PyiUtil.isPyiFileOfPackage(destFile));
  }
}
