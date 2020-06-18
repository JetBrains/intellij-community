// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Due to the complexity of the language grammar and its metaprogramming facilities, for Python it's often impossible
 * to keep all the relevant information in PSI stubs. Because of it there are often two different types of code analysis
 * for the same code insight feature:
 * <ul>
 *   <li>deeper and more precise, but also more time-consuming analysis operating over a complete AST in cases where
 *   a user is ready to wait for the results, e.g. during a batch inspection pass or when performing some refactoring</li>
 *   <li>faster, but less precise analysis using only the information retained in PSI stubs in cases where results
 *   need to be provided on the fly, e.g. in a normal in-editor inspection pass</li>
 * </ul>
 * Unfortunately, relying on the result of {@link StubBasedPsiElement#getStub()} to decide which of these two needs to
 * be applied is a poor practice, because at any given moment a PSI stub for an element can be removed and replaced with AST
 * and vice-versa for numerous reasons, first of all by another unrelated user action, e.g. opening this file in the editor.
 * In other words, from code insight point of view this state is non-deterministic, and using it as the only indicator would
 * lead to unpredictable and flaky code analysis results. Imagine an inspection that reports different problems depending
 * on whether some imported module was opened in another editor tab or not.
 * <p>
 * Do deal with it each {@link TypeEvalContext} instance provides a flag indicating if it's allowed to perform a deeper analysis on
 * a given file -- {@link TypeEvalContext#maySwitchToAST(PsiElement)}. If it returns true, it's safe to access
 * any AST-based API within this file boundaries. Otherwise, the analysis must operate over stubs if they are present, but also
 * <i>have identical results when PSI stubs are absent</i> by the reasons mentioned above.
 * <p>
 * Alternatively, it can be expressed by the following pattern:
 * <pre>{@code
 * if (context.maySwitchToAST(psi)) {
 *   return processOverAst(psi);
 * }
 * else if (psi.getStub() != null) {
 *   return processOverStub(psi.getStub());
 * }
 * else {
 *   return processOverAstStubLike(psi);
 * }
 * }</pre>
 * <p>
 * The last branch is particularly tricky, because it means deliberately dumbing down the analysis, even though a full AST
 * is available, just in order to avoid flaky results. Note that quite often it's possible to express {@code processOverAstStubLike(psi)}
 * as {@code processOverStub(buildStub(psi))} if you have a fast way to build a new stub for a given PSI element.
 * <p>
 * The pattern described above is quite easy to get wrong, e.g. by accidentally switching the first two conditions.
 * This class aids to strictly follow it by providing a builder-like API to supply necessary computation steps.
 * <p>
 * A typical usage is as following:
 * <pre>{@code
 * StubAwareComputation.on(element)
 *   .overAst(this::processOverAst)
 *   .overStub(this::processOverStub)
 *   .overAstStubLike(this::processOverAstStubLike)
 *   .compute(context);
 * }</pre>
 * or, if you can shortcut {@code processOverAstStubLike()} by constructing a new stub and processing it instead of AST:
 * <pre>{@code
 * StubAwareComputation.on(element)
 *   .overAst(this::processOverAst)
 *   .overStub(this::processOverStub)
 *   .withStubBuilder(this::buildStub)
 *   .compute(context);
 * }</pre>
 */
@ApiStatus.Experimental
public final class StubAwareComputation<Psi extends PsiElement, CustomStub, Result> {
  @NotNull
  public static <Psi extends StubBasedPsiElement<PsiStub>, PsiStub extends StubElement<Psi>>
  PsiToStubConversion<Psi, PsiStub, PsiStub> on(@NotNull Psi element) {
    return new PsiToStubConversion<>(element, StubBasedPsiElement::getStub, StubBasedPsiElement::getStub);
  }

  public static final class PsiToStubConversion<Psi extends PsiElement, PsiStub extends StubElement<Psi>, CustomStub> {
    @NotNull private final Psi myElement;
    @NotNull private final Function<Psi, @Nullable PsiStub> myStubGetter;
    @NotNull private final Function<Psi, @Nullable CustomStub> myCustomStubGetter;

    private PsiToStubConversion(@NotNull Psi element,
                                @NotNull Function<Psi, @Nullable PsiStub> psiStubGetter,
                                @NotNull Function<Psi, @Nullable CustomStub> customStubGetter) {
      myElement = element;
      myStubGetter = psiStubGetter;
      myCustomStubGetter = customStubGetter;
    }

    @Nullable
    private PsiStub getPsiStub() {
      return myStubGetter.apply(myElement);
    }

    @Nullable
    private CustomStub getCustomStub() {
      return myCustomStubGetter.apply(myElement);
    }

    /**
     * Provide a function to extract a partial "custom" stub to process instead of a complete PSI stub for an element.
     * Most likely in this case you also want to supply a method to build such a stub anew with {@link #withStubBuilder(Function)}
     * because unlike traditional PSI stubs these can often be built independently of the rest of the stub tree.
     *
     * @see StubElement
     * @see com.jetbrains.python.psi.impl.stubs.PyCustomStub
     */
    @NotNull
    public <S> PsiToStubConversion<Psi, PsiStub, S> withCustomStub(@NotNull Function<@NotNull PsiStub, @Nullable S> stubGetter) {
      return new PsiToStubConversion<>(myElement, myStubGetter, psi -> {
        PsiStub stub = myStubGetter.apply(psi);
        return stub != null ? stubGetter.apply(stub) : null;
      });
    }

    /**
     * @see StubAwareComputation#overStub(Function)
     */
    @NotNull
    public <Result> StubAwareComputation<Psi, CustomStub, Result> overStub(@NotNull Function<CustomStub, Result> stubComputation) {
      return new StubAwareComputation<>(this, stubComputation, null, null, null);
    }

    /**
     * @see StubAwareComputation#overAst(Function)
     */
    @NotNull
    public <Result> StubAwareComputation<Psi, CustomStub, Result> overAst(@NotNull Function<Psi, Result> astComputation) {
      return new StubAwareComputation<>(this, null, astComputation, null, null);
    }

    /**
     * @see StubAwareComputation#overAstStubLike(Function)
     */
    @NotNull
    public <Result> StubAwareComputation<Psi, CustomStub, Result> overAstStubLike(@NotNull Function<Psi, Result> astStubLikeComputation) {
      return new StubAwareComputation<>(this, null, null, astStubLikeComputation, null);
    }
  }

  @NotNull private final PsiToStubConversion<Psi, ?, CustomStub> myConversion;
  @Nullable private final Function<CustomStub, @Nullable Result> myStubComputation;
  @Nullable private final Function<Psi, @Nullable Result> myAstComputation;
  @Nullable private final Function<Psi, @Nullable Result> myAstStubLikeComputation;
  @Nullable private final Function<Psi, @Nullable CustomStub> myStubBuilder;


  private StubAwareComputation(@NotNull PsiToStubConversion<Psi, ?, CustomStub> conversion,
                               @Nullable Function<@Nullable CustomStub, Result> stubComputation,
                               @Nullable Function<@NotNull Psi, Result> astComputation,
                               @Nullable Function<@NotNull Psi, Result> astStubLikeComputation,
                               @Nullable Function<@NotNull Psi, @Nullable CustomStub> stubBuilder) {
    myConversion = conversion;
    myAstComputation = astComputation;
    myStubComputation = stubComputation;
    myAstStubLikeComputation = astStubLikeComputation;
    myStubBuilder = stubBuilder;
  }

  /**
   * Provide a function to analyze an element solely by its stub, either a complete PSI stub or a partial "custom" one.
   * It's a mandatory parameter of the builder.
   *
   * @see PsiToStubConversion#withCustomStub(Function)
   */
  @NotNull
  public StubAwareComputation<Psi, CustomStub, Result> overStub(@NotNull Function<@Nullable CustomStub, Result> stubComputation) {
    return new StubAwareComputation<>(myConversion, stubComputation, myAstComputation, myAstStubLikeComputation, myStubBuilder);
  }

  /**
   * Provide a function to analyze an element by its complete AST.
   * <p>
   * It's an optional parameter and can be emulated by providing a stub builder which result will be passed to {@link #overStub(Function)}
   * computation. Effectively, it means that all the relevant information can be retained in stubs, and the analysis results are exactly
   * the same for both AST and stubs.
   *
   * @see #withStubBuilder(Function)
   */
  @NotNull
  public StubAwareComputation<Psi, CustomStub, Result> overAst(@NotNull Function<@NotNull Psi, Result> astComputation) {
    return new StubAwareComputation<>(myConversion, myStubComputation, astComputation, myAstStubLikeComputation, myStubBuilder);
  }

  /**
   * Provide a function to analyze an element over its complete AST but with the same results as if it was done over a stub.
   * <p>
   * It's an optional parameter and can be emulated by providing a stub builder which result will be passed to {@link #overStub(Function)}
   * computation.
   *
   * @see #withStubBuilder(Function)
   */
  @NotNull
  public StubAwareComputation<Psi, CustomStub, Result> overAstStubLike(@NotNull Function<@NotNull Psi, Result> astStubLikeComputation) {
    return new StubAwareComputation<>(myConversion, myStubComputation, myAstComputation, astStubLikeComputation, myStubBuilder);
  }

  /**
   * Provide a function to construct a new stub from a given PSI element. Usually, it's possible for partial "custom" stubs that
   * can be cheaply built anew for the corresponding element.
   *
   * @see com.jetbrains.python.psi.impl.stubs.PyCustomStub
   */
  @NotNull
  public StubAwareComputation<Psi, CustomStub, Result> withStubBuilder(@NotNull Function<@NotNull Psi, @Nullable CustomStub> stubBuilder) {
    return new StubAwareComputation<>(myConversion, myStubComputation, myAstComputation, myAstStubLikeComputation, stubBuilder);
  }

  /**
   * Apply one of the provided computation kinds depending on the results of {@link TypeEvalContext#maySwitchToAST(PsiElement)} and
   * {@link StubBasedPsiElement#getStub()}, following the logic described in {@link StubAwareComputation} documentation.
   */
  public Result compute(@NotNull TypeEvalContext context) {
    if (myStubComputation == null) {
      throw new IllegalStateException("Over-stub computation must be provided");
    }
    Function<Psi, Result> overNewStubComputation = myStubBuilder != null ? myStubBuilder.andThen(myStubComputation) : null;
    Function<Psi, Result> astComputation = ObjectUtils.chooseNotNull(myAstComputation, overNewStubComputation);
    if (astComputation == null) {
      throw new IllegalStateException("Either over-AST computation or a stub builder must be provided");
    }
    Function<Psi, Result> astStubLikeComputation = ObjectUtils.chooseNotNull(myAstStubLikeComputation, overNewStubComputation);
    if (astStubLikeComputation == null) {
      throw new IllegalStateException("Either over-AST-stub-like computation or a stub builder must be provided");
    }

    Psi psi = myConversion.myElement;
    if (context.maySwitchToAST(psi)) {
      return astComputation.apply(psi);
    }
    else if (myConversion.getPsiStub() != null) {
      return myStubComputation.apply(myConversion.getCustomStub());
    }
    else {
      return astStubLikeComputation.apply(psi);
    }
  }
}
