package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Represents a type of an expression.
 * @author yole
 */
public interface PyType {

  /**
   * Resolves an attribute of type.
   * @param name attribute name
   * @param context
   * @return attribute's definition element
   */
  @NotNull
  Maybe<? extends PsiElement> resolveMember(final String name, Context context);

  /**
   * Return this from resolveMember() when the name is neither definitely resolved nor definitely unresolved.
   */
  Maybe<PsiElement> NOT_RESOLVED_YET = new Maybe<PsiElement>();

  /**
   * Return this from resolveMember() when the name definitely cannot be resolved, and no other attempts should be made.
   */
  Maybe<PsiElement> UNRESOLVED = new Maybe<PsiElement>(null);

  /**
   * Proposes completion variants from type's attributes.
   * @param referenceExpression which is to be completed
   * @param context to share state between nested invocations
   * @return completion variants good for {@link com.intellij.psi.PsiReference#getVariants} return value.
   */
  Object[] getCompletionVariants(final PyQualifiedExpression referenceExpression, ProcessingContext context);

  /**
   * Context key for access to a set of names already found by variant search.
   */
  Key<Set<String>> CTX_NAMES = new Key<Set<String>>("Completion variants names");

  /**
   * @return name of the type
   */
  @Nullable
  String getName();

  /** How we refer to a name */
  enum Context {

    /** Reference */
    READ,

    /** Target of assignment */
    WRITE,

    /** Target of del statement */
    DELETE
  }

}
