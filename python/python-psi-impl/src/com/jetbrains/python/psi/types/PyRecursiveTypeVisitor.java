// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Recursively traverses {@link PyType} trees in DFS order.
 * <p>
 * Should be used as follows:
 * <pre>{@code
 *     PyRecursiveTypeVisitor.traverse(type, new PyRecursiveTypeVisitor.PyTypeTraverser() {
 *       @Override
 *       public @NotNull Traversal visitPyType(@NotNull PyType type) {
 *         if (isTheRightOne(type)) {
 *           return Traversal.TERMINATE;
 *         }
 *         return Traversal.CONTINUE;
 *       }
 *
 *       @Override
 *       public @NotNull Traversal visitPyGenericType(@NotNull PyCollectionType genericType) {
 *         // Irrelevant, don't go deeper
 *         return Traversal.PRUNE;
 *       }
 *     });
 * }</pre>
 */
@ApiStatus.Experimental
public final class PyRecursiveTypeVisitor extends PyTypeVisitorExt<PyRecursiveTypeVisitor.Traversal> {
  private final PyTypeTraverser myTraverser;
  private final PyTypeComponentsStrategy myComponentsStrategy;
  private final Set<PyType> myVisited;

  private PyRecursiveTypeVisitor(@NotNull PyTypeTraverser traverser, @NotNull TypeEvalContext context) {
    myComponentsStrategy = new TypeHintComponents(context);
    myTraverser = traverser;
    myVisited = new HashSet<>();
  }

  @Override
  public Traversal visitPyType(@NotNull PyType type) {
    if (!myVisited.add(type)) {
      return Traversal.CONTINUE;
    }
    try {
      Traversal next = visit(type, myTraverser);
      if (next == Traversal.TERMINATE) {
        return Traversal.TERMINATE;
      }
      if (next != Traversal.PRUNE) {
        for (@Nullable PyType childType : visit(type, myComponentsStrategy)) {
          if (visit(childType, this) == Traversal.TERMINATE) {
            return Traversal.TERMINATE;
          }
        }
      }
      return Traversal.CONTINUE;
    }
    finally {
      myVisited.remove(type);
    }
  }

  @Override
  public Traversal visitUnknownType() {
    Traversal traversal = myTraverser.visitUnknownType();
    return traversal == Traversal.TERMINATE ? Traversal.TERMINATE : Traversal.CONTINUE;
  }

  public enum Traversal {
    /**
     * Continue the depth-first traversal normally.
     */
    CONTINUE,
    /**
     * Don't traverse the current subtree, but continue otherwise.
     */
    PRUNE,
    /**
     * Completely stop the traversal.
     */
    TERMINATE,
  }

  public static abstract class PyTypeTraverser extends PyTypeVisitorExt<@NotNull Traversal> {
    @Override
    public @NotNull Traversal visitPyType(@NotNull PyType type) {
      return Traversal.CONTINUE;
    }

    @Override
    public @NotNull Traversal visitUnknownType() {
      return Traversal.CONTINUE;
    }
  }

  private static abstract class PyTypeComponentsStrategy extends PyTypeVisitorExt<@NotNull List<@Nullable PyType>> {
    @Override
    public @NotNull List<@Nullable PyType> visitPyType(@NotNull PyType type) {
      return Collections.emptyList();
    }

    @Override
    public @NotNull List<@Nullable PyType> visitUnknownType() {
      return Collections.emptyList();
    }
  }

  public static void traverse(@Nullable PyType type, @NotNull TypeEvalContext context, @NotNull PyTypeTraverser delegate) {
    visit(type, new PyRecursiveTypeVisitor(delegate, context));
  }

  /**
   * Visits only those component types that appear inside type expressions.
   * For instance, it skips bounds and constraints of {@link PyTypeVarType}, defaults of any
   * {@link PyTypeParameterType}, as well as fields of {@link PyNamedTupleType}.
   */
  private static class TypeHintComponents extends PyTypeComponentsStrategy {
    private final @NotNull TypeEvalContext myTypeEvalContext;

    protected TypeHintComponents(@NotNull TypeEvalContext context) {
      myTypeEvalContext = context;
    }

    @Override
    public @NotNull List<@Nullable PyType> visitPyCallableType(@NotNull PyCallableType callableType) {
      List<PyType> result = new ArrayList<>();
      List<PyCallableParameter> parameters = callableType.getParameters(myTypeEvalContext);
      if (parameters != null) {
        for (PyCallableParameter parameter : parameters) {
          result.add(parameter.getType(myTypeEvalContext));
        }
      }
      result.add(callableType.getReturnType(myTypeEvalContext));
      return result;
    }

    @Override
    public @NotNull List<@Nullable PyType> visitPyGenericType(@NotNull PyCollectionType genericType) {
      return genericType.getElementTypes();
    }

    @Override
    public @NotNull List<@Nullable PyType> visitPyConcatenateType(@NotNull PyConcatenateType concatenateType) {
      return ContainerUtil.append(concatenateType.getFirstTypes(), concatenateType.getParamSpec());
    }

    @Override
    public @NotNull List<@Nullable PyType> visitPyTypedDictType(@NotNull PyTypedDictType typedDictType) {
      // TODO this is not entirely correct. Field types of a non-generic typed dict are not used in its notation.
      return ContainerUtil.map(typedDictType.getFields().values(), field -> field.getType());
    }

    @Override
    public @NotNull List<@Nullable PyType> visitPyCallableParameterListType(@NotNull PyCallableParameterListType callableParameterListType) {
      return ContainerUtil.map(callableParameterListType.getParameters(), param -> param.getType(myTypeEvalContext));
    }

    @Override
    public @NotNull List<@Nullable PyType> visitPyTupleType(@NotNull PyTupleType tupleType) {
      return tupleType.getElementTypes();
    }

    @Override
    public @NotNull List<@Nullable PyType> visitPyNamedTupleType(@NotNull PyNamedTupleType namedTupleType) {
      // Types of named tuple fields don't belong to its notation.
      // Unlike regular tuple, it's a named type, i.e. the type of
      //  
      // class Person(NamedTuple):
      //     name: str
      //     age: int
      //
      // is just Person, not Person[str, int]
      return Collections.emptyList();
    }

    @Override
    public @NotNull List<@Nullable PyType> visitPyUnionType(@NotNull PyUnionType unionType) {
      return Collections.unmodifiableList(new ArrayList<>(unionType.getMembers()));
    }

    @Override
    public @NotNull List<@Nullable PyType> visitPyUnpackedTupleType(@NotNull PyUnpackedTupleType unpackedTupleType) {
      return unpackedTupleType.getElementTypes();
    }

    @Override
    public @NotNull List<@Nullable PyType> visitPyNarrowedType(@NotNull PyNarrowedType narrowedType) {
      return Collections.singletonList(narrowedType.getNarrowedType());
    }
  }
}
