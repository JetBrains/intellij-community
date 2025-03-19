// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.psi.types.PyRecursiveTypeVisitor.PyTypeTraverser;
import com.jetbrains.python.psi.types.PyRecursiveTypeVisitor.Traversal;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public final class PyTypeHintGenerationUtil {

  public static final String TYPE_COMMENT_PREFIX = "# type: ";

  private PyTypeHintGenerationUtil() { }

  public static void insertStandaloneAttributeTypeComment(@NotNull PyTargetExpression target,
                                                          @NotNull TypeEvalContext context,
                                                          AnnotationInfo info,
                                                          boolean startTemplate) {

    final PyClass pyClass = target.getContainingClass();
    if (pyClass == null) {
      throw new IllegalArgumentException("Target '" + target.getText() + "' in not contained in a class definition");
    }

    if (!FileModificationService.getInstance().preparePsiElementForWrite(target)) return;

    final PyElementGenerator generator = PyElementGenerator.getInstance(target.getProject());
    final LanguageLevel langLevel = LanguageLevel.forElement(target);
    final String assignedValue = langLevel.isPython2() ? "None" : "...";
    final String declarationText = target.getName() + " = " + assignedValue + " " + TYPE_COMMENT_PREFIX + info.getAnnotationText();
    final PyAssignmentStatement declaration = generator.createFromText(langLevel, PyAssignmentStatement.class, declarationText);
    final PsiElement anchorBefore = findPrecedingAnchorForAttributeDeclaration(pyClass);

    WriteAction.run(() -> {
      final PyAssignmentStatement inserted = (PyAssignmentStatement)pyClass.getStatementList().addAfter(declaration, anchorBefore);
      PsiComment insertedComment = as(inserted.getLastChild(), PsiComment.class);
      if (insertedComment == null) return;

      addImportsForTypeAnnotations(info.getTypes(), context, target.getContainingFile());

      insertedComment = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(insertedComment);
      if (startTemplate && insertedComment != null) {
        openEditorAndAddTemplateForTypeComment(insertedComment, info.getAnnotationText(), info.getTypeRanges());
      }
    });
  }

  public static void insertStandaloneAttributeAnnotation(@NotNull PyTargetExpression target,
                                                         @NotNull TypeEvalContext context,
                                                         @NotNull AnnotationInfo info,
                                                         boolean startTemplate) {
    final LanguageLevel langLevel = LanguageLevel.forElement(target);
    if (langLevel.isOlderThan(LanguageLevel.PYTHON36)) {
      throw new IllegalArgumentException("Target '" + target.getText() + "' doesn't belong to Python 3.6+ project: " + langLevel);
    }

    final PyClass pyClass = target.getContainingClass();
    if (pyClass == null) {
      throw new IllegalArgumentException("Target '" + target.getText() + "' in not contained in a class definition");
    }

    if (!FileModificationService.getInstance().preparePsiElementForWrite(target)) return;

    final PyElementGenerator generator = PyElementGenerator.getInstance(target.getProject());
    final String declarationText = target.getName() + ": " + info.getAnnotationText();
    final PyTypeDeclarationStatement declaration = generator.createFromText(langLevel, PyTypeDeclarationStatement.class, declarationText);
    final PsiElement anchorBefore = findPrecedingAnchorForAttributeDeclaration(pyClass);

    WriteAction.run(() -> {
      PyTypeDeclarationStatement inserted = (PyTypeDeclarationStatement)pyClass.getStatementList().addAfter(declaration, anchorBefore);

      addImportsForTypeAnnotations(info.getTypes(), context, target.getContainingFile());

      inserted = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(inserted);
      if (startTemplate && inserted != null) {
        openEditorAndAddTemplateForAnnotation(inserted);
      }
    });
  }

  private static @Nullable PsiElement findPrecedingAnchorForAttributeDeclaration(@NotNull PyClass pyClass) {
    final PyStatement firstStatement = pyClass.getStatementList().getStatements()[0];
    final PyStringLiteralExpression classDocstring = pyClass.getDocStringExpression();
    if (firstStatement instanceof PyExpressionStatement && classDocstring == ((PyExpressionStatement)firstStatement).getExpression()) {
      return firstStatement;
    }
    return null;
  }

  public static void insertVariableAnnotation(@NotNull PyTargetExpression target,
                                              @Nullable TypeEvalContext context,
                                              @NotNull AnnotationInfo info,
                                              boolean startTemplate) {
    final LanguageLevel langLevel = LanguageLevel.forElement(target);
    if (langLevel.isOlderThan(LanguageLevel.PYTHON36)) {
      throw new IllegalArgumentException("Target '" + target.getText() + "' doesn't belong to Python 3.6+ project: " + langLevel);
    }

    if (!FileModificationService.getInstance().preparePsiElementForWrite(target)) return;

    final Project project = target.getProject();
    final ThrowableComputable<PyAnnotationOwner, RuntimeException> addOrUpdateAnnotatedStatement;
    if (canUseInlineAnnotation(target)) {
      final SmartPointerManager manager = SmartPointerManager.getInstance(project);
      final PyAssignmentStatement assignment = (PyAssignmentStatement)target.getParent();
      final SmartPsiElementPointer<PyAssignmentStatement> pointer = manager.createSmartPsiElementPointer(assignment);
      addOrUpdateAnnotatedStatement = () -> {
        PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
          document.insertString(target.getTextRange().getEndOffset(), ": " + info.getAnnotationText());
        });
        return pointer.getElement();
      };
    }
    else {
      final PyElementGenerator generator = PyElementGenerator.getInstance(project);
      final String declarationText = target.getName() + ": " + info.getAnnotationText();
      final PyTypeDeclarationStatement declaration = generator.createFromText(langLevel, PyTypeDeclarationStatement.class, declarationText);
      final PyStatement statement = PsiTreeUtil.getParentOfType(target, PyStatement.class);
      assert statement != null;
      addOrUpdateAnnotatedStatement = () -> (PyAnnotationOwner)statement.getParent().addBefore(declaration, statement);
    }

    WriteAction.run(() -> {
      PyAnnotationOwner createdAnnotationOwner = addOrUpdateAnnotatedStatement.compute();
      if (createdAnnotationOwner == null) return;

      if (context != null) {
        addImportsForTypeAnnotations(info.getTypes(), context, target.getContainingFile());
      }

      createdAnnotationOwner = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(createdAnnotationOwner);
      if (startTemplate && createdAnnotationOwner != null) {
        openEditorAndAddTemplateForAnnotation(createdAnnotationOwner);
      }
    });
  }

  private static void openEditorAndAddTemplateForAnnotation(@NotNull PyAnnotationOwner annotated) {
    assert annotated.isValid();
    assert annotated.getAnnotationValue() != null;

    final Project project = annotated.getProject();
    final int initialCaretOffset = annotated.getTextRange().getStartOffset();
    final VirtualFile updatedVirtualFile = annotated.getContainingFile().getVirtualFile();
    final TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(annotated);
    final String annotation = annotated.getAnnotationValue();
    final String replacementText = ApplicationManager.getApplication().isUnitTestMode() ? "[" + annotation + "]" : annotation;
    //noinspection ConstantConditions
    templateBuilder.replaceElement(annotated.getAnnotation().getValue(), replacementText);

    final Editor editor = PythonUiService.getInstance().openTextEditor(project, updatedVirtualFile, initialCaretOffset);
    if (editor != null) {
      editor.getCaretModel().moveToOffset(initialCaretOffset);
      templateBuilder.run(editor, true);
    }
    else {
      templateBuilder.runNonInteractively(true);
    }
  }

  private static boolean canUseInlineAnnotation(@NotNull PyTargetExpression target) {
    final PyAssignmentStatement assignment = as(target.getParent(), PyAssignmentStatement.class);
    return assignment != null && assignment.getRawTargets().length == 1 && assignment.getLeftHandSideExpression() == target;
  }

  public static void insertVariableTypeComment(@NotNull PyTargetExpression target,
                                               TypeEvalContext context,
                                               @NotNull AnnotationInfo info,
                                               boolean startTemplate) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(target)) return;

    final String typeCommentText = "  " + TYPE_COMMENT_PREFIX + info.getAnnotationText();

    final PyStatement statement = PsiTreeUtil.getParentOfType(target, PyStatement.class);
    final PsiElement insertionAnchor;
    if (statement instanceof PyAssignmentStatement) {
      insertionAnchor = statement.getLastChild();
    }
    else if (statement instanceof PyWithStatement) {
      insertionAnchor = PyUtilCore.getHeaderEndAnchor((PyStatementListContainer)statement);
    }
    else if (statement instanceof PyForStatement) {
      insertionAnchor = PyUtilCore.getHeaderEndAnchor(((PyForStatement)statement).getForPart());
    }
    else {
      throw new IllegalArgumentException("Target expression must belong to an assignment, \"with\" statement or \"for\" loop");
    }

    final ThrowableRunnable<RuntimeException> insertComment;
    if (insertionAnchor instanceof PsiComment) {
      final String combinedTypeCommentText = typeCommentText + " " + insertionAnchor.getText();
      final PsiElement lastNonComment = PyPsiUtils.getPrevNonCommentSibling(insertionAnchor, true);
      final int startOffset = lastNonComment.getTextRange().getEndOffset();
      final int endOffset = insertionAnchor.getTextRange().getEndOffset();
      insertComment = () -> PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
        document.replaceString(startOffset, endOffset, combinedTypeCommentText);
      });
    }
    else if (insertionAnchor != null) {
      final int offset = insertionAnchor.getTextRange().getEndOffset();
      insertComment = () -> PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
        document.insertString(offset, typeCommentText);
      });
    }
    else {
      return;
    }

    WriteAction.run(() -> {
      insertComment.run();
      PsiComment insertedComment = target.getTypeComment();
      if (insertedComment == null) return;

      addImportsForTypeAnnotations(info.getTypes(), context, target.getContainingFile());

      insertedComment = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(insertedComment);
      if (startTemplate && insertedComment != null) {
        openEditorAndAddTemplateForTypeComment(insertedComment, info.getAnnotationText(), info.getTypeRanges());
      }
    });
  }

  private static void openEditorAndAddTemplateForTypeComment(@NotNull PsiComment insertedComment,
                                                             @NotNull String annotation,
                                                             @NotNull List<TextRange> typeRanges) {
    final int initialCaretOffset = insertedComment.getTextRange().getStartOffset();
    final VirtualFile updatedVirtualFile = insertedComment.getContainingFile().getVirtualFile();
    final Project project = insertedComment.getProject();
    final TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(insertedComment);
    final boolean testMode = ApplicationManager.getApplication().isUnitTestMode();
    for (TextRange range : typeRanges) {
      final String individualType = range.substring(annotation);
      final String replacementText = testMode ? "[" + individualType + "]" : individualType;
      templateBuilder.replaceRange(range.shiftRight(TYPE_COMMENT_PREFIX.length()), replacementText);
    }

    final Editor editor = PythonUiService.getInstance().openTextEditor(project, updatedVirtualFile, initialCaretOffset);
    if (editor != null) {
      editor.getCaretModel().moveToOffset(initialCaretOffset);
      templateBuilder.run(editor, true);
    }
    else {
      templateBuilder.runNonInteractively(true);
    }
  }

  public static void addImportsForTypeAnnotations(@NotNull List<PyType> types,
                                                  @NotNull TypeEvalContext context,
                                                  @NotNull PsiFile file) {
    final Set<PsiNamedElement> symbols = new LinkedHashSet<>();
    final Set<String> namesFromTyping = new LinkedHashSet<>();

    for (PyType type : types) {
      collectImportTargetsFromType(type, context, symbols, namesFromTyping);
    }

    final boolean builtinTyping = LanguageLevel.forElement(file).isAtLeast(LanguageLevel.PYTHON35);
    final ImportPriority priority = builtinTyping ? ImportPriority.BUILTIN : ImportPriority.THIRD_PARTY;
    for (String name : namesFromTyping) {
      AddImportHelper.addOrUpdateFromImportStatement(file, "typing", name, null, priority, null);
    }

    for (PsiNamedElement symbol : symbols) {
      PyPsiRefactoringUtil.insertImport(file, symbol, null, true);
    }
  }

  private static void collectImportTargetsFromType(@Nullable PyType type,
                                                   @NotNull TypeEvalContext context,
                                                   @NotNull Set<PsiNamedElement> symbols,
                                                   @NotNull Set<String> typingTypes) {
    boolean useGenericAliasFromTyping =
      context.getOrigin() != null && LanguageLevel.forElement(context.getOrigin()).isOlderThan(LanguageLevel.PYTHON39);
    PyRecursiveTypeVisitor.traverse(type, context, new PyTypeTraverser() {
      @Override
      public @NotNull Traversal visitUnknownType() {
        typingTypes.add("Any");
        return Traversal.CONTINUE;
      }

      @Override
      public @NotNull Traversal visitPyUnionType(@NotNull PyUnionType unionType) {
        final Collection<PyType> members = unionType.getMembers();
        final boolean isOptional = members.size() == 2 && members.contains(PyNoneType.INSTANCE);
        if (!PyTypingTypeProvider.isBitwiseOrUnionAvailable(context)) {
          typingTypes.add(isOptional ? "Optional" : "Union");
        }
        return Traversal.CONTINUE;
      }

      @Override
      public @NotNull Traversal visitPyNamedTupleType(@NotNull PyNamedTupleType namedTupleType) {
        final PyQualifiedNameOwner element = namedTupleType.getDeclarationElement();
        if (element instanceof PsiNamedElement) {
          symbols.add((PsiNamedElement)element);
        }
        addTypingTypeIfNeeded(namedTupleType);
        return Traversal.CONTINUE;
      }

      @Override
      public @NotNull Traversal visitPyGenericType(@NotNull PyCollectionType genericType) {
        final PyClass pyClass = genericType.getPyClass();
        final String typingCollectionName = PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.get(pyClass.getQualifiedName());
        if (typingCollectionName != null && genericType.isBuiltin() && useGenericAliasFromTyping) {
          typingTypes.add(typingCollectionName);
        }
        else {
          symbols.add(pyClass);
        }
        addTypingTypeIfNeeded(genericType);
        return Traversal.CONTINUE;
      }

      @Override
      public @NotNull Traversal visitPyTupleType(@NotNull PyTupleType tupleType) {
        if (useGenericAliasFromTyping) {
          typingTypes.add("Tuple");
        }
        return Traversal.CONTINUE;
      }

      @Override
      public @NotNull Traversal visitPyTypedDictType(@NotNull PyTypedDictType typedDictType) {
        if (typedDictType.isInferred()) {
          if (useGenericAliasFromTyping) {
            typingTypes.add("Dict");
          }
        }
        else {
          symbols.add((PsiNamedElement)typedDictType.getDeclarationElement());
          // Don't go through its type arguments
          return Traversal.PRUNE;
        }
        return Traversal.CONTINUE;
      }

      @Override
      public @NotNull Traversal visitPyClassType(@NotNull PyClassType classType) {
        symbols.add(classType.getPyClass());
        addTypingTypeIfNeeded(classType);
        return Traversal.CONTINUE;
      }

      @Override
      public @NotNull Traversal visitPyCallableType(@NotNull PyCallableType callableType) {
        typingTypes.add("Callable");
        return Traversal.CONTINUE;
      }

      @Override
      public @NotNull Traversal visitPyTypeParameterType(@NotNull PyTypeParameterType typeParameterType) {
        final PyTargetExpression target = as(typeParameterType.getDeclarationElement(), PyTargetExpression.class);
        if (target != null) {
          symbols.add(target);
        }
        addTypingTypeIfNeeded(typeParameterType);
        return Traversal.PRUNE;
      }

      private void addTypingTypeIfNeeded(@NotNull PyType type) {
        // TODO in Python 3.9+ use the builtin "type" instead of "typing.Type"
        if (type instanceof PyInstantiableType<?> instantiableType && instantiableType.isDefinition()) {
          typingTypes.add("Type");
        }
      }
    });
  }

  public static void checkPep484Compatibility(@Nullable PyType type, @NotNull TypeEvalContext context) {
    if (type == null ||
        type instanceof PyNoneType ||
        type instanceof PyTypeParameterType) {
      return;
    }
    else if (type instanceof PyUnionType) {
      for (PyType memberType : ((PyUnionType)type).getMembers()) {
        checkPep484Compatibility(memberType, context);
      }
    }
    else if (type instanceof PyCollectionType) {
      for (PyType typeParam : ((PyCollectionType)type).getElementTypes()) {
        checkPep484Compatibility(typeParam, context);
      }
    }
    else if (type instanceof PyClassType) {
      // In this order since PyCollectionTypeImpl implements PyClassType
    }
    else if (type instanceof PyCallableType callableType) {
      for (PyCallableParameter parameter : ContainerUtil.notNullize(callableType.getParameters(context))) {
        checkPep484Compatibility(parameter.getType(context), context);
      }
      checkPep484Compatibility(callableType.getReturnType(context), context);
    }
    else {
      throw new Pep484IncompatibleTypeException(
        PyPsiBundle.message("INTN.add.type.hint.for.variable.PEP484.incompatible.type", type.getName()));
    }
  }

  public static boolean isTypeHintComment(PsiElement element) {
    return element instanceof PsiComment && element.getText().startsWith(TYPE_COMMENT_PREFIX);
  }

  public static final class Pep484IncompatibleTypeException extends RuntimeException {
    public Pep484IncompatibleTypeException(String message) {
      super(message);
    }
  }

  public static final class AnnotationInfo {
    private final String myAnnotationText;
    private final List<PyType> myTypes;
    private final List<TextRange> myTypeRanges;

    public AnnotationInfo(@NotNull String annotationText) {
      this(annotationText, Collections.emptyList(), Collections.singletonList(TextRange.allOf(annotationText)));
    }

    public AnnotationInfo(@NotNull String annotationText, @Nullable PyType type) {
      this(annotationText, Collections.singletonList(type), Collections.singletonList(TextRange.allOf(annotationText)));
    }

    public AnnotationInfo(@NotNull String annotationText, @NotNull List<PyType> types, @NotNull List<TextRange> typeRanges) {
      myAnnotationText = annotationText;
      myTypes = types;
      myTypeRanges = typeRanges;
    }

    public @NotNull String getAnnotationText() {
      return myAnnotationText;
    }

    public @NotNull List<PyType> getTypes() {
      return myTypes;
    }

    public @NotNull List<TextRange> getTypeRanges() {
      return myTypeRanges;
    }
  }
}
