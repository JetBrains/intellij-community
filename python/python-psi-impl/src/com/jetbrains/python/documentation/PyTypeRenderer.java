package com.jetbrains.python.documentation;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyLanguageFacadeKt;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstSingleStarParameter;
import com.jetbrains.python.ast.PyAstSlashParameter;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static com.jetbrains.python.documentation.PyDocSignaturesHighlighterKt.highlightExpressionText;
import static com.jetbrains.python.documentation.PyDocSignaturesHighlighterKt.styledSpan;
import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;

// TODO visitPyConcatenateType
public abstract class PyTypeRenderer extends PyTypeVisitorExt<@NotNull HtmlChunk> {
  private static final int MAX_DEPTH = 6;

  protected int myDepth = 0;
  protected final @NotNull TypeEvalContext myTypeEvalContext;
  protected final EnumSet<Feature> myRenderingFeatures;
  
  public enum Feature {
    /**
     * Render fully qualified names of all classes and type forms, e.g. {@code typing.Callable[[mod.MyClass], typing.Any]}.
     */
    USE_FQN,
    /**
     * Render internal "unsafe" unions as {@code UnsafeUnion[...]}, otherwise render them as regular union types.
     */
    UNSAFE_UNION,
    /**
     * Render bounds and constraints of TypeVars.
     */
    TYPE_VAR_BOUNDS,
  }

  protected final boolean renderFqn() {
    return myRenderingFeatures.contains(Feature.USE_FQN);
  }

  protected final boolean renderTypeVarBounds() {
    return myRenderingFeatures.contains(Feature.TYPE_VAR_BOUNDS);
  }

  protected final boolean renderUnsafeUnion() {
    return myRenderingFeatures.contains(Feature.UNSAFE_UNION);
  }

  private PyTypeRenderer(@NotNull TypeEvalContext typeEvalContext, @NotNull EnumSet<Feature> features) {
    myTypeEvalContext = typeEvalContext;
    myRenderingFeatures = features;
  }

  static abstract class HtmlRenderer extends PyTypeRenderer {
    private final @NotNull PsiElement myAnchor;

    private HtmlRenderer(@NotNull TypeEvalContext typeEvalContext, @NotNull PsiElement anchor, @NotNull EnumSet<Feature> features) {
      super(typeEvalContext, features);
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
      super(typeEvalContext, anchor, EnumSet.noneOf(Feature.class));
    }
  }

  static final class Documentation extends PyTypeRenderer {
    Documentation(@NotNull TypeEvalContext typeEvalContext, @NotNull EnumSet<Feature> features) {
      super(typeEvalContext, features);
    }
  }

  public static final class TypeHint extends PyTypeRenderer {
    private static final EnumSet<Feature> SUPPORTED = EnumSet.of(Feature.USE_FQN); 
    
    public TypeHint(@NotNull TypeEvalContext typeEvalContext, @NotNull EnumSet<Feature> features) {
      super(typeEvalContext, validateFeatures(features));
    }

    private static @NotNull EnumSet<Feature> validateFeatures(@NotNull EnumSet<Feature> features) {
      EnumSet<Feature> unsupported = EnumSet.copyOf(features);
      unsupported.removeAll(SUPPORTED);
      if (!unsupported.isEmpty()) {
        throw new IllegalArgumentException("Unsupported features for type hint rendering " + unsupported);
      }
      return features;
    }

    @Override
    protected boolean maxDepthExceeded() {
      return false;
    }

    @Override
    public HtmlChunk visitPyCallableType(@NotNull PyCallableType callableType) {
      HtmlBuilder result = new HtmlBuilder();
      result.append(HtmlChunk.raw(renderFqn() ? "typing.Callable" : "Callable")); //NON-NLS
      result.append(styled("[", PyHighlighter.PY_BRACKETS));
      List<PyCallableParameter> parameters = getParameters(callableType);
      if (parameters != null) {
        result.append(styled("[", PyHighlighter.PY_BRACKETS));
        result.append(renderList(ContainerUtil.map(parameters, this::visitPyCallableParameter)));
        result.append(styled("]", PyHighlighter.PY_BRACKETS));
      }
      else {
        result.append(styled("...", PyHighlighter.PY_DOT));
      }
      result.append(HtmlChunk.raw(", "));
      result.append(render(callableType.getReturnType(myTypeEvalContext)));
      result.append(styled("]", PyHighlighter.PY_BRACKETS));
      return result.toFragment();
    }

    // Returns `callableType`'s parameter list (excluding terminating '/') if it can be expressed using typing.Callable, `null` otherwise.
    // Parameters specified using typing.Callable are assumed to be positional-only (a parameter list must be terminated with '/'). There
    // is no way to specify keyword-only or variadic parameters.
    private @Nullable List<PyCallableParameter> getParameters(@NotNull PyCallableType callableType) {
      List<PyCallableParameter> parameters = callableType.getParameters(myTypeEvalContext);
      if (parameters == null) return null;
      if (parameters.isEmpty()) return List.of();

      for (int i = 0; i < parameters.size(); i++) {
        PyCallableParameter parameter = parameters.get(i);
        if (parameter.isPositionalContainer()) {
          return null;
        }
        if (parameter.isKeywordOnlySeparator()) {
          return null;
        }
        if (parameter.isPositionOnlySeparator()) {
          if (i == parameters.size() - 1) {
            return parameters.subList(0, i);
          }
          return null;
        }
      }

      // TODO If CallableType is inferred from a 'Callable[]' type hint, there is no terminating '/' parameter.
      // Check whether all parameters have no name then.
      if (ContainerUtil.all(parameters, parameter -> parameter.getName() == null)) {
        return parameters;
      }
      return null;
    }

    @Override
    protected @NotNull HtmlChunk visitPyCallableParameter(@NotNull PyCallableParameter param) {
      return render(param.getType(myTypeEvalContext));
    }

    @Override
    public @NotNull HtmlChunk visitPyUnsafeUnionType(@NotNull PyUnsafeUnionType unsafeUnionType) {
      // There is no way to represent weak unions through type hints
      return visitUnknownType();
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
    return origin == null || PyLanguageFacadeKt.getEffectiveLanguageLevel(origin).isAtLeast(LanguageLevel.PYTHON39);
  }

  @Override
  public HtmlChunk visitPyGenericType(@NotNull PyCollectionType collectionOf) {
    HtmlChunk genericTypeRender = renderGenericType(collectionOf);
    return collectionOf.isDefinition() ? wrapInTypingType(genericTypeRender) : genericTypeRender;
  }

  private @NotNull HtmlChunk renderGenericType(@NotNull PyCollectionType genericType) {
    HtmlBuilder result = new HtmlBuilder();
    boolean renderTypeArgumentList = !genericType.getElementTypes().isEmpty();
    String className = genericType.getName();
    if (renderTypeArgumentList && !isGenericBuiltinsAvailable() && PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.containsKey(className)) {
      className = PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.get(className);
      if (renderFqn()) {
        className = PyTypingTypeProvider.TYPING + "." + className;
      }
      result.append(className(className));
    }
    else {
      result.append(className(renderFqn() ? genericType.getClassQName() : className));
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
      .append(isGenericBuiltinsAvailable() ? styled("type", PyHighlighter.PY_BUILTIN_NAME) : //NON-NLS
              escaped(renderFqn() ? "typing.Type" : "Type")) //NON-NLS
      .append(styled("[", PyHighlighter.PY_BRACKETS))
      .append(instanceTypeRender)
      .append(styled("]", PyHighlighter.PY_BRACKETS))
      .toFragment();
  }

  @Override
  public @NotNull HtmlChunk visitPyClassLikeType(@NotNull PyClassLikeType classLikeType) {
    HtmlChunk classTypeRender = className(getTypeName(classLikeType));
    return classLikeType.isDefinition() ? wrapInTypingType(classTypeRender) : classTypeRender;
  }

  @Override
  public HtmlChunk visitPyNarrowedType(@NotNull PyNarrowedType narrowedType) {
    HtmlBuilder result = new HtmlBuilder();
    if (narrowedType.getTypeIs()) {
      result.append(styled(renderFqn() ? "typing.TypeIs" : "TypeIs", PyHighlighter.PY_CLASS_DEFINITION));
    }
    else {
      result.append(styled(renderFqn() ? "typing.TypeGuard" : "TypeGuard", PyHighlighter.PY_CLASS_DEFINITION));
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
    if (unionType.getMembers().contains(null)) {
      // Always put Any at the end of the union
      return renderUnion(List.of(render(PyUnionType.union(ContainerUtil.skipNulls(unionType.getMembers()))), visitUnknownType()));
    }
    return renderUnion(ContainerUtil.map(unionType.getMembers(), this::render));
  }

  @Override
  public @NotNull HtmlChunk visitPyUnsafeUnionType(@NotNull PyUnsafeUnionType unsafeUnionType) {
    if (renderUnsafeUnion()) {
      HtmlBuilder result = new HtmlBuilder();
      result.append(escaped("UnsafeUnion")); //NON-NLS
      result.append(styled("[", PyHighlighter.PY_BRACKETS));
      result.append(renderList(ContainerUtil.map(unsafeUnionType.getMembers(), this::render)));
      result.append(styled("]", PyHighlighter.PY_BRACKETS));
      return result.toFragment();
    }
    return renderUnion(ContainerUtil.map(unsafeUnionType.getMembers(), this::render));
  }

  private @NotNull HtmlChunk renderUnionOfLiterals(@NotNull List<PyLiteralType> literals) {
    return new HtmlBuilder()
      .append(escaped(renderFqn() ? "typing.Literal" : "Literal")) //NON-NLS
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
      result.append(escaped(renderFqn() ? "typing.Union" : "Union")); //NON-NLS
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
      result.append(escaped(renderFqn() ? "typing.Optional" : "Optional")); //NON-NLS
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
      result.append(escaped(renderFqn() ? "typing.Tuple" : "Tuple")); //NON-NLS
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
    return HtmlChunk.raw(renderFqn() ? "typing.Any" : "Any"); //NON-NLS
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
    if (param.isPositionOnlySeparator()) {
      result.append(escaped(PyAstSlashParameter.TEXT));
    }
    else if (param.isKeywordOnlySeparator()) {
      result.append(escaped(PyAstSingleStarParameter.TEXT));
    }
    else {
      PyType type = param.getType(myTypeEvalContext);
      // TODO remove that
      if (!(type instanceof PyParamSpecType) && !(type instanceof PyConcatenateType)) {
        if (param.getName() != null) {
          result.append(styled(param.getName(), PyHighlighter.PY_PARAMETER));
          result.append(styled(": ", PyHighlighter.PY_OPERATION_SIGN));
        }
      }
      result.append(render(type));
    }
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
    HtmlBuilder result = new HtmlBuilder();
    result.append(escaped(getTypeName(typeVarType)));
    if (renderTypeVarBounds()) {
      PyType effectiveBound = PyTypeUtil.getEffectiveBound(typeVarType);
      if (effectiveBound != null) {
        result.append(escaped(" ≤: "));
        result.append(render(effectiveBound));
      }
    }
    return typeVarType.isDefinition() ? wrapInTypingType(result.toFragment()) : result.toFragment();
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

  protected final @Nullable @NlsSafe String getTypeName(@NotNull PyType type) {
    if (isNoneType(type)) {
      return PyNames.NONE;
    }
    if (renderFqn()) {
      PyQualifiedNameOwner declarationElement = type.getDeclarationElement();
      return declarationElement != null ? declarationElement.getQualifiedName() : null;
    }
    return type.getName();
  }
}
