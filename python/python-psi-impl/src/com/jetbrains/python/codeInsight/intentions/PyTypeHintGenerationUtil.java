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
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;

/**
 * @author Mikhail Golubev
 */
public final class PyTypeHintGenerationUtil {

  public static final String TYPE_COMMENT_PREFIX = "# type: ";

  private PyTypeHintGenerationUtil() { }

  public static void insertStandaloneAttributeTypeComment(@NotNull PyTargetExpression target,
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

      addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), target);

      insertedComment = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(insertedComment);
      if (startTemplate && insertedComment != null) {
        openEditorAndAddTemplateForTypeComment(insertedComment, info.getAnnotationText(), info.getTypeRanges());
      }
    });
  }

  public static void insertStandaloneAttributeAnnotation(@NotNull PyTargetExpression target,
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

      addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), target);

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
        addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), target);
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

      addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), target);

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

  /** Adds imports for type annotations. Sorts imports by name. */
  public static void addImportsForTypeAnnotations(@NotNull Collection<String> types, @NotNull PsiElement anchor) {
    final Set<PsiNamedElement> symbols =
      new TreeSet<>(Comparator.comparing(PsiNamedElement::getName, Comparator.nullsFirst(Comparator.naturalOrder())));

    for (String type : types) {
      collectImportTargetsFromTypeExpression(type, anchor, symbols);
    }

    PsiFile file = anchor.getContainingFile();
    for (PsiNamedElement symbol : symbols) {
      PyPsiRefactoringUtil.insertImport(file, symbol, null, true);
    }
  }

  private static void collectImportTargetsFromTypeExpression(@NotNull String typeExpressionText,
                                                             @NotNull PsiElement anchor,
                                                             @NotNull Set<@NotNull PsiNamedElement> symbols) {
    PyExpression typeExpression = PyUtil.createExpressionFromFragment(typeExpressionText, anchor);
    assert typeExpression != null;
    PyQualifiedNameResolveContext qNameResolveContext = PyResolveImportUtil.fromFoothold(anchor);
    typeExpression.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
        if (node.isQualified()) {
          QualifiedName qualifiedName = node.asQualifiedName();
          if (qualifiedName != null) {
            PsiElement element = PyResolveImportUtil.resolveTopLevelMember(qualifiedName, qNameResolveContext);
            if (element instanceof PsiNamedElement namedElement) {
              symbols.add(namedElement);
              return;
            }
          }
        }
        super.visitPyReferenceExpression(node);
      }
    });
  }

  public static void checkPep484Compatibility(@Nullable PyType type, @NotNull TypeEvalContext context) {
    if (type == null ||
        isNoneType(type) ||
        // Will be rendered as just Any
        type instanceof PyUnsafeUnionType || 
        type instanceof PyTypeParameterType) {
      return;
    }
    else if (type instanceof PyUnionType unionType) {
      for (PyType memberType : unionType.getMembers()) {
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
    private final List<String> myFullyQualifiedTypeHints;
    private final List<TextRange> myTypeRanges;

    public AnnotationInfo(@NotNull String annotationText) {
      this(annotationText, Collections.emptyList(), Collections.singletonList(TextRange.allOf(annotationText)));
    }

    public AnnotationInfo(@NotNull String annotationText, @NotNull String fullyQualifiedTypeHint) {
      this(annotationText, Collections.singletonList(fullyQualifiedTypeHint), Collections.singletonList(TextRange.allOf(annotationText)));
    }

    public AnnotationInfo(@NotNull String annotationText,
                          @NotNull List<String> fullyQualifiedTypeHints,
                          @NotNull List<TextRange> typeRanges) {
      myAnnotationText = annotationText;
      myFullyQualifiedTypeHints = fullyQualifiedTypeHints;
      myTypeRanges = typeRanges;
    }

    public @NotNull String getAnnotationText() {
      return myAnnotationText;
    }

    public @NotNull List<String> getFullyQualifiedTypeHints() {
      return myFullyQualifiedTypeHints;
    }

    public @NotNull List<TextRange> getTypeRanges() {
      return myTypeRanges;
    }
  }
}
