// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleType;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyTypeHintGenerationUtil {

  public static final String TYPE_COMMENT_PREFIX = "# type: ";

  private PyTypeHintGenerationUtil() {}

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
    final String assignedValue = langLevel.isAtLeast(LanguageLevel.PYTHON30) ? "..." : "None";
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

  @Nullable
  private static PsiElement findPrecedingAnchorForAttributeDeclaration(@NotNull PyClass pyClass) {
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
    final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, updatedVirtualFile, initialCaretOffset);
    final Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);

    if (editor != null) {
      editor.getCaretModel().moveToOffset(initialCaretOffset);
      final TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(annotated);
      final String annotation = annotated.getAnnotationValue();
      final String replacementText = ApplicationManager.getApplication().isUnitTestMode() ? "[" + annotation + "]" : annotation;
      //noinspection ConstantConditions
      templateBuilder.replaceElement(annotated.getAnnotation().getValue(), replacementText);
      templateBuilder.run(editor, true);
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
      insertionAnchor = PyUtil.getHeaderEndAnchor((PyStatementListContainer)statement);
    }
    else if (statement instanceof PyForStatement) {
      insertionAnchor = PyUtil.getHeaderEndAnchor(((PyForStatement)statement).getForPart());
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
      insertComment = () -> {
        PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
          document.replaceString(startOffset, endOffset, combinedTypeCommentText);
        });
      };
    }
    else if (insertionAnchor != null) {
      final int offset = insertionAnchor.getTextRange().getEndOffset();
      insertComment = () -> {
        PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
          document.insertString(offset, typeCommentText);
        });
      };
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
    final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, updatedVirtualFile, initialCaretOffset);
    final Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);

    if (editor != null) {
      final boolean testMode = ApplicationManager.getApplication().isUnitTestMode();
      editor.getCaretModel().moveToOffset(initialCaretOffset);
      final TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(insertedComment);
      //noinspection ConstantConditions
      for (TextRange range : typeRanges) {
        final String individualType = range.substring(annotation);
        final String replacementText = testMode ? "[" + individualType + "]" : individualType;
        templateBuilder.replaceRange(range.shiftRight(TYPE_COMMENT_PREFIX.length()), replacementText);
      }
      templateBuilder.run(editor, true);
    }
  }

  private static void addImportsForTypeAnnotations(@NotNull List<PyType> types,
                                                   @NotNull TypeEvalContext context,
                                                   @NotNull PsiFile file) {
    final Set<PsiNamedElement> symbols = new HashSet<>();
    final Set<String> namesFromTyping = new HashSet<>();

    for (PyType type : types) {
      collectImportTargetsFromType(type, context, symbols, namesFromTyping);
    }

    final boolean builtinTyping = LanguageLevel.forElement(file).isAtLeast(LanguageLevel.PYTHON35);
    final ImportPriority priority = builtinTyping ? ImportPriority.BUILTIN : ImportPriority.THIRD_PARTY;
    for (String name : namesFromTyping) {
      AddImportHelper.addOrUpdateFromImportStatement(file, "typing", name, null, priority, null);
    }

    for (PsiNamedElement symbol : symbols) {
      PyClassRefactoringUtil.insertImport(file, symbol, null, true);
    }
  }

  private static void collectImportTargetsFromType(@Nullable PyType type,
                                                   @NotNull TypeEvalContext context,
                                                   @NotNull Set<PsiNamedElement> symbols,
                                                   @NotNull Set<String> typingTypes) {
    if (type == null) {
      typingTypes.add("Any");
    }
    else if (type instanceof PyUnionType) {
      final Collection<PyType> members = ((PyUnionType)type).getMembers();
      final boolean isOptional = members.size() == 2 && members.contains(PyNoneType.INSTANCE);
      typingTypes.add(isOptional ? "Optional" : "Union");
      for (PyType pyType : members) {
        collectImportTargetsFromType(pyType, context, symbols, typingTypes);
      }
    }
    else if (type instanceof PyNamedTupleType) {
      final PyQualifiedNameOwner element = type.getDeclarationElement();
      if (element instanceof PsiNamedElement) {
        symbols.add((PsiNamedElement)element);
      }
    }
    else if (type instanceof PyCollectionType) {
      if (type instanceof PyCollectionTypeImpl) {
        final PyClass pyClass = ((PyCollectionTypeImpl)type).getPyClass();
        final String typingCollectionName = PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.get(pyClass.getQualifiedName());
        if (typingCollectionName != null && type.isBuiltin()) {
          typingTypes.add(typingCollectionName);
        }
        else {
          symbols.add(pyClass);
        }
      }
      else if (type instanceof PyTupleType) {
        typingTypes.add("Tuple");
      }
      for (PyType pyType : ((PyCollectionType)type).getElementTypes()) {
        collectImportTargetsFromType(pyType, context, symbols, typingTypes);
      }
    }
    else if (type instanceof PyClassType) {
      symbols.add(((PyClassType)type).getPyClass());
    }
    else if (type instanceof PyCallableType) {
      typingTypes.add("Callable");
      final PyCallableType callableType = (PyCallableType)type;
      for (PyCallableParameter parameter : ContainerUtil.notNullize(callableType.getParameters(context))) {
        collectImportTargetsFromType(parameter.getType(context), context, symbols, typingTypes);
      }
      collectImportTargetsFromType(callableType.getReturnType(context), context, symbols, typingTypes);
    }
    else if (type instanceof PyGenericType) {
      final PyTargetExpression target = as(type.getDeclarationElement(), PyTargetExpression.class);
      if (target != null) {
        symbols.add(target);
      }
    }
    if (type instanceof PyInstantiableType && ((PyInstantiableType)type).isDefinition()) {
      typingTypes.add("Type");
    }
  }

  public static void checkPep484Compatibility(@Nullable PyType type, @NotNull TypeEvalContext context) {
    if (type == null ||
        type instanceof PyNoneType ||
        type instanceof PyGenericType) {
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
    else if (type instanceof PyCallableType) {
      final PyCallableType callableType = (PyCallableType)type;
      for (PyCallableParameter parameter : ContainerUtil.notNullize(callableType.getParameters(context))) {
        checkPep484Compatibility(parameter.getType(context), context);
      }
      checkPep484Compatibility(callableType.getReturnType(context), context);
    }
    else {
      throw new Pep484IncompatibleTypeException(
        PyBundle.message("INTN.add.type.hint.for.variable.PEP484.incompatible.type", type.getName()));
    }
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

    @NotNull
    public String getAnnotationText() {
      return myAnnotationText;
    }

    @NotNull
    public List<PyType> getTypes() {
      return myTypes;
    }

    @NotNull
    public List<TextRange> getTypeRanges() {
      return myTypeRanges;
    }
  }
}
