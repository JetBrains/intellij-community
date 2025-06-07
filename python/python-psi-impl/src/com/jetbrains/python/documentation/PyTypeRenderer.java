package com.jetbrains.python.documentation;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.documentation.PyDocSignaturesHighlighterKt.highlightExpressionText;
import static com.jetbrains.python.documentation.PyDocSignaturesHighlighterKt.styledSpan;
import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;

// TODO visitPyConcatenateType
public abstract class PyTypeRenderer extends PyTypeVisitorExt<@NotNull HtmlChunk> {
  private static final int MAX_DEPTH = 6;

  protected int myDepth = 0;
  protected final @NotNull TypeEvalContext myTypeEvalContext;

  private PyTypeRenderer(@NotNull TypeEvalContext typeEvalContext) {
    myTypeEvalContext = typeEvalContext;
  }

  static abstract class HtmlRenderer extends PyTypeRenderer {
    private final @NotNull PsiElement myAnchor;

    private HtmlRenderer(@NotNull TypeEvalContext typeEvalContext, @NotNull PsiElement anchor) {
      super(typeEvalContext);
      myAnchor = anchor;
    }

    @Override
    protected @NotNull HtmlChunk styled(@Nls String text, @NotNull TextAttributesKey style) {
      return styledSpan(text, style);
    }

    @Override
    protected @NotNull HtmlChunk escaped(@Nls String text) {
      return HtmlChunk.text(text);
    }

    @Override
    protected @NotNull HtmlChunk className(@Nls String name) {
      return PyDocumentationLink.toPossibleClass(name, myAnchor, myTypeEvalContext);
    }

    @Override
    protected @NotNull HtmlChunk styledExpression(@NotNull PyExpression expression) {
      return highlightExpressionText(expression.getText(), expression);
    }
  }

  static final class RichDocumentation extends HtmlRenderer {
    RichDocumentation(@NotNull TypeEvalContext typeEvalContext, @NotNull PsiElement anchor) {
      super(typeEvalContext, anchor);
    }
  }

  static final class Documentation extends PyTypeRenderer {
    Documentation(@NotNull TypeEvalContext typeEvalContext) {
      super(typeEvalContext);
    }
  }

  static final class VerboseDocumentation extends PyTypeRenderer {
    VerboseDocumentation(@NotNull TypeEvalContext typeEvalContext) {
      super(typeEvalContext);
    }

    @Override
    public @NotNull HtmlChunk visitPyTypeVarType(@NotNull PyTypeVarType typeVarType) {
      HtmlBuilder result = new HtmlBuilder();
      typeVarType.getName();
      result.append(escaped(typeVarType.getName()));
      PyType effectiveBound = PyTypeUtil.getEffectiveBound(typeVarType);
      if (effectiveBound != null) {
        result.append(escaped(" ≤: "));
        result.append(render(effectiveBound));
      }
      return typeVarType.isDefinition() ? wrapInTypingType(result.toFragment()) : result.toFragment();
    }
  }

  public static final class TypeHint extends PyTypeRenderer {
    public TypeHint(@NotNull TypeEvalContext typeEvalContext) {
      super(typeEvalContext);
    }

    @Override
    protected boolean maxDepthExceeded() {
      return false;
    }

    @Override
    public HtmlChunk visitPyCallableType(@NotNull PyCallableType callableType) {
      HtmlBuilder result = new HtmlBuilder();
      result.append(HtmlChunk.raw("Callable")); //NON-NLS
      result.append(styled("[", PyHighlighter.PY_BRACKETS));
      result.append(styled("[", PyHighlighter.PY_BRACKETS));
      List<PyCallableParameter> parameters = callableType.getParameters(myTypeEvalContext);
      if (parameters != null) {
        result.append(renderList(ContainerUtil.map(parameters, this::visitPyCallableParameter)));
      }
      else {
        result.append(styled("...", PyHighlighter.PY_DOT));
      }
      result.append(styled("]", PyHighlighter.PY_BRACKETS));
      result.append(HtmlChunk.raw(", "));
      result.append(render(callableType.getReturnType(myTypeEvalContext)));
      result.append(styled("]", PyHighlighter.PY_BRACKETS));
      return result.toFragment();
    }

    @Override
    protected @NotNull HtmlChunk visitPyCallableParameter(@NotNull PyCallableParameter param) {
      return render(param.getType(myTypeEvalContext));
    }
  }

  protected boolean maxDepthExceeded() {
    return myDepth > MAX_DEPTH;
  }

  protected @NotNull HtmlChunk render(@Nullable PyType type) {
    if (maxDepthExceeded()) {
      return styled("...", PyHighlighter.PY_DOT);
    }
    myDepth++;
    try {
      return visit(type, this);
    }
    finally {
      myDepth--;
    }
  }

  protected @NotNull HtmlChunk styled(@Nls String text, @NotNull TextAttributesKey style) {
    return HtmlChunk.raw(StringUtil.notNullize(text));
  }

  protected @NotNull HtmlChunk escaped(@Nls String text) {
    return HtmlChunk.raw(StringUtil.notNullize(text));
  }

  protected @NotNull HtmlChunk className(@Nls String name) {
    return escaped(name);
  }

  protected @NotNull HtmlChunk styledExpression(@NotNull PyExpression expression) {
    return HtmlChunk.raw(expression.getText());
  }

  protected final boolean isBitwiseOrUnionAvailable() {
    return PyTypingTypeProvider.isBitwiseOrUnionAvailable(myTypeEvalContext);
  }

  protected final boolean isGenericBuiltinsAvailable() {
    PsiFile origin = myTypeEvalContext.getOrigin();
    return origin == null || PythonLanguageLevelPusher.getLanguageLevelForFile(origin).isAtLeast(LanguageLevel.PYTHON39);
  }

  @Override
  public HtmlChunk visitPyGenericType(@NotNull PyCollectionType collectionOf) {
    HtmlChunk genericTypeRender = renderGenericType(collectionOf);
    return collectionOf.isDefinition() ? wrapInTypingType(genericTypeRender) : genericTypeRender;
  }

  private @NotNull HtmlChunk renderGenericType(@NotNull PyCollectionType genericType) {
    HtmlBuilder result = new HtmlBuilder();
    boolean renderTypeArgumentList = !genericType.getElementTypes().isEmpty();
    String className = genericType.getPyClass().getName();
    if (renderTypeArgumentList && !isGenericBuiltinsAvailable() && PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.containsKey(className)) {
      result.append(className(PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.get(className))); // NON-NLS
    }
    else {
      result.append(className(className));
    }
    if (renderTypeArgumentList) {
      result.append(styled("[", PyHighlighter.PY_BRACKETS));
      result.append(renderList(ContainerUtil.map(genericType.getElementTypes(), this::render)));
      result.append(styled("]", PyHighlighter.PY_BRACKETS));
    }
    return result.toFragment();
  }

  protected @NotNull HtmlChunk wrapInTypingType(@NotNull HtmlChunk instanceTypeRender) {
    return new HtmlBuilder()
      .append(isGenericBuiltinsAvailable() ? styled("type", PyHighlighter.PY_BUILTIN_NAME) : escaped("Type")) //NON-NLS
      .append(styled("[", PyHighlighter.PY_BRACKETS))
      .append(instanceTypeRender)
      .append(styled("]", PyHighlighter.PY_BRACKETS))
      .toFragment();
  }

  @Override
  public @NotNull HtmlChunk visitPyClassLikeType(@NotNull PyClassLikeType classLikeType) {
    HtmlChunk classTypeRender = className(classLikeType.getName());
    return classLikeType.isDefinition() ? wrapInTypingType(classTypeRender) : classTypeRender;
  }

  @Override
  public HtmlChunk visitPyNarrowedType(@NotNull PyNarrowedType narrowedType) {
    HtmlBuilder result = new HtmlBuilder();
    if (narrowedType.getTypeIs()) {
      result.append(styled("TypeIs", PyHighlighter.PY_CLASS_DEFINITION));
    }
    else {
      result.append(styled("TypeGuard", PyHighlighter.PY_CLASS_DEFINITION));
    }
    result.append(styled("[", PyHighlighter.PY_BRACKETS));
    result.append(render(narrowedType.getNarrowedType()));
    result.append(styled("]", PyHighlighter.PY_BRACKETS));
    return result.toFragment();
  }

  @Override
  public @NotNull HtmlChunk visitPyNeverType(@NotNull PyNeverType neverType) {
    return className(neverType.getName());
  }

  @Override
  public HtmlChunk visitPyUnionType(@NotNull PyUnionType unionType) {
    // TODO Exclude "Unknown" once it's introduced, don't exclude explicit typing.Any
    if (isOptional(unionType)) {
      return renderOptional(unionType);
    }
    Pair<List<PyLiteralType>, List<PyType>> literalsAndOthers = extractLiterals(unionType);
    if (literalsAndOthers != null) {
      if (literalsAndOthers.second.isEmpty()) {
        return renderUnionOfLiterals(literalsAndOthers.first);
      }
      return renderUnion(ContainerUtil.prepend(
        ContainerUtil.map(literalsAndOthers.second, this::render),
        renderUnionOfLiterals(literalsAndOthers.first)
      ));
    }
    if (ContainerUtil.all(unionType.getMembers(), t -> t instanceof PyClassType ct && ct.isDefinition())) {
      return wrapInTypingType(render(unionType.map(type -> type != null ? ((PyClassType)type).toInstance() : null)));
    }
    if (PyTypeChecker.isUnknown(unionType, false, myTypeEvalContext)) {
      // Always put Any at the end of the union
      return renderUnion(List.of(render(unionType.excludeNull()), visitUnknownType()));
    }
    return renderUnion(ContainerUtil.map(unionType.getMembers(), this::render));
  }

  private @NotNull HtmlChunk renderUnionOfLiterals(@NotNull List<PyLiteralType> literals) {
    return new HtmlBuilder()
      .append(escaped("Literal")) //NON-NLS
      .append(styled("[", PyHighlighter.PY_BRACKETS))
      .append(StreamEx
                .of(literals)
                .map(PyLiteralType::getExpression)
                .map(expr -> styledExpression(expr))
                .collect(HtmlChunk.toFragment(styled(", ", PyHighlighter.PY_COMMA))))
      .append(styled("]", PyHighlighter.PY_BRACKETS))
      .toFragment();
  }

  private @NotNull HtmlChunk renderUnion(@NotNull List<HtmlChunk> renderedUnionMembers) {
    HtmlBuilder result = new HtmlBuilder();
    if (isBitwiseOrUnionAvailable()) {
      result.append(renderList(renderedUnionMembers, " | "));
    }
    else {
      result.append(escaped("Union")); //NON-NLS
      result.append(styled("[", PyHighlighter.PY_BRACKETS));
      result.append(renderList(renderedUnionMembers));
      result.append(styled("]", PyHighlighter.PY_BRACKETS));
    }
    return result.toFragment();
  }

  // TODO get rid of dedicated rendering for Optional
  private @NotNull HtmlChunk renderOptional(@NotNull PyUnionType type) {
    HtmlBuilder result = new HtmlBuilder();
    if (isBitwiseOrUnionAvailable()) {
      result.append(render(ContainerUtil.find(type.getMembers(), t -> !isNoneType(t))));
      result.append(styled(" | ", PyHighlighter.PY_OPERATION_SIGN));
      result.append(render(ContainerUtil.find(type.getMembers(), t -> isNoneType(t)))); //NON-NLS
    }
    else {
      result.append(escaped("Optional")); //NON-NLS
      result.append(styled("[", PyHighlighter.PY_BRACKETS));
      result.append(render(ContainerUtil.find(type.getMembers(), t -> !isNoneType(t))));
      result.append(styled("]", PyHighlighter.PY_BRACKETS));
    }
    return result.toFragment();
  }

  private static @Nullable Pair<@NotNull List<PyLiteralType>, @NotNull List<PyType>> extractLiterals(@NotNull PyUnionType type) {
    final Collection<PyType> members = type.getMembers();

    final List<PyLiteralType> literalTypes = ContainerUtil.filterIsInstance(members, PyLiteralType.class);
    if (literalTypes.size() < 2) return null;

    final List<PyType> otherTypes = ContainerUtil.filter(members, m -> !(m instanceof PyLiteralType));

    return Pair.create(literalTypes, otherTypes);
  }

  private static boolean isOptional(@NotNull PyUnionType type) {
    return type.getMembers().size() == 2 && ContainerUtil.find(type.getMembers(), m -> isNoneType(m)) != null;
  }

  @Override
  public HtmlChunk visitPyTupleType(@NotNull PyTupleType tupleType) {
    HtmlBuilder result = new HtmlBuilder();
    if (isGenericBuiltinsAvailable()) {
      result.append(styled("tuple", PyHighlighter.PY_BUILTIN_NAME)); //NON-NLS
    }
    else {
      result.append(escaped("Tuple")); //NON-NLS
    }
    result.append(styled("[", PyHighlighter.PY_BRACKETS));
    if (!tupleType.getElementTypes().isEmpty()) {
      result.append(renderList(ContainerUtil.map(tupleType.getElementTypes(), this::render)));
      if (tupleType.isHomogeneous()) {
        result.append(styled(", ", PyHighlighter.PY_COMMA));
        result.append(styled("...", PyHighlighter.PY_DOT));
      }
    }
    else {
      result.append(styled("()", PyHighlighter.PY_PARENTHS));
    }
    result.append(styled("]", PyHighlighter.PY_BRACKETS));
    return result.toFragment();
  }

  @Override
  public HtmlChunk visitUnknownType() {
    return HtmlChunk.raw("Any"); //NON-NLS
  }

  @Override
  public HtmlChunk visitPyType(@NotNull PyType type) {
    return escaped(type.getName());
  }

  @Override
  public HtmlChunk visitPyCallableType(@NotNull PyCallableType callableType) {
    HtmlBuilder result = new HtmlBuilder();
    result.append(styled("(", PyHighlighter.PY_PARENTHS));
    List<PyCallableParameter> parameters = callableType.getParameters(myTypeEvalContext);
    if (parameters != null) {
      result.append(renderList(ContainerUtil.map(parameters, this::visitPyCallableParameter)));
    }
    else {
      result.append(styled("...", PyHighlighter.PY_DOT));
    }
    result.append(styled(")", PyHighlighter.PY_PARENTHS));
    result.append(escaped(" -> "));
    result.append(render(callableType.getReturnType(myTypeEvalContext)));
    return result.toFragment();
  }

  @NotNull
  protected HtmlChunk visitPyCallableParameter(@NotNull PyCallableParameter param) {
    HtmlBuilder result = new HtmlBuilder();
    PyType type = param.getType(myTypeEvalContext);
    // TODO remove that
    if (!(type instanceof PyParamSpecType) && !(type instanceof PyConcatenateType)) {
      if (param.getName() != null) {
        result.append(styled(param.getName(), PyHighlighter.PY_PARAMETER));
        result.append(styled(": ", PyHighlighter.PY_OPERATION_SIGN));
      }
    }
    result.append(render(type));
    return result.toFragment();
  }

  protected @NotNull HtmlChunk renderList(@NotNull Collection<HtmlChunk> list) {
    return renderList(list, ", ");
  }

  protected @NotNull HtmlChunk renderList(@NotNull Collection<HtmlChunk> list, @NotNull @Nls String separator) {
    return StreamEx.of(list).collect(HtmlChunk.toFragment(switch (separator) {
      case ", " -> {
        yield styled(separator, PyHighlighter.PY_COMMA);
      }
      case " | " -> {
        yield styled(separator, PyHighlighter.PY_OPERATION_SIGN);
      }
      default -> {
        yield escaped(separator);
      }
    }));
  }

  @Override
  public @NotNull HtmlChunk visitPyTypeVarType(@NotNull PyTypeVarType typeVarType) {
    HtmlChunk typeVarTypeRender = escaped(typeVarType.getName());
    return typeVarType.isDefinition() ? wrapInTypingType(typeVarTypeRender) : typeVarTypeRender;
  }

  @Override
  public HtmlChunk visitPyTypeParameterType(@NotNull PyTypeParameterType typeParameterType) {
    return escaped(typeParameterType.getName());
  }

  @Override
  public @NotNull HtmlChunk visitPySelfType(@NotNull PySelfType selfType) {
    // Don't render Self as a type parameter
    return className(selfType.getName());
  }

  @Override
  public @NotNull HtmlChunk visitPyCallableParameterListType(@NotNull PyCallableParameterListType callableParameterListType) {
    HtmlBuilder result = new HtmlBuilder();
    result.append("[");
    result.append(renderList(ContainerUtil.map(callableParameterListType.getParameters(), this::visitPyCallableParameter)));
    result.append("]");
    return result.toFragment();
  }
}
