// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotationOwner;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyIntersectionType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.PyUnsafeUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.psi.types.PyTypeUtilKt.isAnyOrUnknown;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public final class PyTypeHintGenerationUtil {

  public static final String TYPE_COMMENT_PREFIX = "# type: ";

  private PyTypeHintGenerationUtil() { }

  /**
   * Declares the attribute {@code target} with a type comment at the start of its class.
   * With an {@code updater}, starts a template on the types.
   */
  public static void insertStandaloneAttributeTypeComment(@NotNull PyTargetExpression target,
                                                          AnnotationInfo info,
                                                          @Nullable ModPsiUpdater updater) {

    final PyClass pyClass = target.getContainingClass();
    if (pyClass == null) {
      throw new IllegalArgumentException("Target '" + target.getText() + "' in not contained in a class definition");
    }

    final PyElementGenerator generator = PyElementGenerator.getInstance(target.getProject());
    final LanguageLevel langLevel = LanguageLevel.forElement(target);
    final String assignedValue = langLevel.isPython2() ? "None" : "...";
    final String declarationText = target.getName() + " = " + assignedValue + " " + TYPE_COMMENT_PREFIX + info.getAnnotationText();
    final PyAssignmentStatement declaration = generator.createFromText(langLevel, PyAssignmentStatement.class, declarationText);
    final PsiElement anchorBefore = findPrecedingAnchorForAttributeDeclaration(pyClass);

    final PyAssignmentStatement inserted = (PyAssignmentStatement)pyClass.getStatementList().addAfter(declaration, anchorBefore);
    PsiComment insertedComment = as(inserted.getLastChild(), PsiComment.class);
    if (insertedComment == null) return;

    addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), target);

    insertedComment = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(insertedComment);
    if (updater != null && insertedComment != null) {
      addTemplateForTypeComment(insertedComment, info.getAnnotationText(), info.getTypeRanges(), updater);
    }
  }

  /**
   * Declares the attribute {@code target} with an annotation at the start of its class.
   * With an {@code updater}, starts a template on the annotation.
   */
  public static void insertStandaloneAttributeAnnotation(@NotNull PyTargetExpression target,
                                                         @NotNull AnnotationInfo info,
                                                         @Nullable ModPsiUpdater updater) {
    final LanguageLevel langLevel = LanguageLevel.forElement(target);
    if (langLevel.isOlderThan(LanguageLevel.PYTHON36)) {
      throw new IllegalArgumentException("Target '" + target.getText() + "' doesn't belong to Python 3.6+ project: " + langLevel);
    }

    final PyClass pyClass = target.getContainingClass();
    if (pyClass == null) {
      throw new IllegalArgumentException("Target '" + target.getText() + "' in not contained in a class definition");
    }

    final PyElementGenerator generator = PyElementGenerator.getInstance(target.getProject());
    final String declarationText = target.getName() + ": " + info.getAnnotationText();
    final PyTypeDeclarationStatement declaration = generator.createFromText(langLevel, PyTypeDeclarationStatement.class, declarationText);
    final PsiElement anchorBefore = findPrecedingAnchorForAttributeDeclaration(pyClass);

    PyTypeDeclarationStatement inserted = (PyTypeDeclarationStatement)pyClass.getStatementList().addAfter(declaration, anchorBefore);

    addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), target);

    inserted = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(inserted);
    if (updater != null && inserted != null) {
      addTemplateForAnnotation(inserted, updater);
    }
  }

  private static @Nullable PsiElement findPrecedingAnchorForAttributeDeclaration(@NotNull PyClass pyClass) {
    final PyStatement firstStatement = pyClass.getStatementList().getStatements()[0];
    final PyStringLiteralExpression classDocstring = pyClass.getDocStringExpression();
    if (firstStatement instanceof PyExpressionStatement && classDocstring == ((PyExpressionStatement)firstStatement).getExpression()) {
      return firstStatement;
    }
    return null;
  }

  /**
   * Annotates the variable {@code target}, inline when it is the only target of an assignment, else in a declaration before it.
   * Adds the imports of the types when {@code addImports} is true. With an {@code updater}, starts a template on the annotation.
   */
  public static void insertVariableAnnotation(@NotNull PyTargetExpression target,
                                              boolean addImports,
                                              @NotNull AnnotationInfo info,
                                              @Nullable ModPsiUpdater updater) {
    final LanguageLevel langLevel = LanguageLevel.forElement(target);
    if (langLevel.isOlderThan(LanguageLevel.PYTHON36)) {
      throw new IllegalArgumentException("Target '" + target.getText() + "' doesn't belong to Python 3.6+ project: " + langLevel);
    }

    final Project project = target.getProject();
    PyAnnotationOwner createdAnnotationOwner;
    if (canUseInlineAnnotation(target)) {
      final SmartPointerManager manager = SmartPointerManager.getInstance(project);
      final PyAssignmentStatement assignment = (PyAssignmentStatement)target.getParent();
      final SmartPsiElementPointer<PyAssignmentStatement> pointer = manager.createSmartPsiElementPointer(assignment);
      PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
        document.insertString(target.getTextRange().getEndOffset(), ": " + info.getAnnotationText());
      });
      createdAnnotationOwner = pointer.getElement();
    }
    else {
      final PyElementGenerator generator = PyElementGenerator.getInstance(project);
      final String declarationText = target.getName() + ": " + info.getAnnotationText();
      final PyTypeDeclarationStatement declaration = generator.createFromText(langLevel, PyTypeDeclarationStatement.class, declarationText);
      final PyStatement statement = PsiTreeUtil.getParentOfType(target, PyStatement.class);
      assert statement != null;
      createdAnnotationOwner = (PyAnnotationOwner)statement.getParent().addBefore(declaration, statement);
    }
    if (createdAnnotationOwner == null) return;

    if (addImports) {
      addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), createdAnnotationOwner);
    }

    createdAnnotationOwner = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(createdAnnotationOwner);
    if (updater != null && createdAnnotationOwner != null) {
      addTemplateForAnnotation(createdAnnotationOwner, updater);
    }
  }

  private static void addTemplateForAnnotation(@NotNull PyAnnotationOwner annotated, @NotNull ModPsiUpdater updater) {
    assert annotated.isValid();
    assert annotated.getAnnotationValue() != null;

    final String annotation = annotated.getAnnotationValue();
    final String replacementText = ApplicationManager.getApplication().isUnitTestMode() ? "[" + annotation + "]" : annotation;
    //noinspection ConstantConditions
    final PyExpression annotationValue = annotated.getAnnotation().getValue();
    updater.moveCaretTo(annotated);
    updater.templateBuilder().field(annotationValue, new ConstantNode(replacementText));
  }

  private static boolean canUseInlineAnnotation(@NotNull PyTargetExpression target) {
    final PyAssignmentStatement assignment = as(target.getParent(), PyAssignmentStatement.class);
    return assignment != null && assignment.getRawTargets().length == 1 && assignment.getLeftHandSideExpression() == target;
  }

  /**
   * Adds a type comment for the variable {@code target} to its statement.
   * With an {@code updater}, starts a template on the types.
   */
  public static void insertVariableTypeComment(@NotNull PyTargetExpression target,
                                               @NotNull AnnotationInfo info,
                                               @Nullable ModPsiUpdater updater) {
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

    if (insertionAnchor instanceof PsiComment) {
      final String combinedTypeCommentText = typeCommentText + " " + insertionAnchor.getText();
      final PsiElement lastNonComment = PyPsiUtils.getPrevNonCommentSibling(insertionAnchor, true);
      final int startOffset = lastNonComment.getTextRange().getEndOffset();
      final int endOffset = insertionAnchor.getTextRange().getEndOffset();
      PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
        document.replaceString(startOffset, endOffset, combinedTypeCommentText);
      });
    }
    else if (insertionAnchor != null) {
      final int offset = insertionAnchor.getTextRange().getEndOffset();
      PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
        document.insertString(offset, typeCommentText);
      });
    }
    else {
      return;
    }

    PsiComment insertedComment = target.getTypeComment();
    if (insertedComment == null) return;

    addImportsForTypeAnnotations(info.getFullyQualifiedTypeHints(), target);

    insertedComment = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(insertedComment);
    if (updater != null && insertedComment != null) {
      addTemplateForTypeComment(insertedComment, info.getAnnotationText(), info.getTypeRanges(), updater);
    }
  }

  private static void addTemplateForTypeComment(@NotNull PsiComment insertedComment,
                                                @NotNull String annotation,
                                                @NotNull List<TextRange> typeRanges,
                                                @NotNull ModPsiUpdater updater) {
    final boolean testMode = ApplicationManager.getApplication().isUnitTestMode();
    updater.moveCaretTo(insertedComment);
    final ModTemplateBuilder templateBuilder = updater.templateBuilder();
    // Each field writes its value into the document at once, so a longer value moves the fields after it.
    int shift = TYPE_COMMENT_PREFIX.length();
    for (int i = 0; i < typeRanges.size(); i++) {
      final TextRange range = typeRanges.get(i);
      final String individualType = range.substring(annotation);
      final String replacementText = testMode ? "[" + individualType + "]" : individualType;
      templateBuilder.field(insertedComment, range.shiftRight(shift), "TYPE" + i, new ConstantNode(replacementText));
      shift += replacementText.length() - individualType.length();
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
    if (isAnyOrUnknown(type) ||
        isNoneType(type) ||
        // Will be rendered as just Any
        type instanceof PyUnsafeUnionType ||
        type instanceof PyIntersectionType ||
        type instanceof PyTypeParameterType) {
      return;
    }
    else if (type instanceof PyUnionType unionType) {
      for (PyType memberType : unionType.getMembers()) {
        checkPep484Compatibility(memberType, context);
      }
    }
    else if (type instanceof PyClassType pyClassType) {
      for (PyType typeParam : pyClassType.getTypeArguments()) {
        checkPep484Compatibility(typeParam, context);
      }
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
