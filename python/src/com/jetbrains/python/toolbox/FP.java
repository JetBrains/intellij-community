package com.jetbrains.python.toolbox;

import com.intellij.openapi.util.Pair;
import com.jetbrains.python.PythonDocumentationProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
* Tools of functional programming, the notorious half of implementation of Lisp.
* User: dcheryasov
* Date: Nov 6, 2009 10:06:50 AM
*/
public class FP {
  /**
   * [a, b,..] -> [lambda(a), lambda(b),...]
   * @param lambda function to apply
   * @param source list to process
   * @param <S> type of source elements
   * @param <R> type of result elements
   * @return list of mapped values.
   */
  @NotNull
  public static <S, R> List<R> map(@NotNull Lambda1<S, R> lambda, @NotNull List<S> source) {
    List<R> ret = new ArrayList<R>(source.size());
    for (S item : source) ret.add(lambda.apply(item));
    return ret;
  }

  /**
   * Apply a two-argument lambda to each sequence element and an accumulator.
   * @param lambda function to apply; accumulator is the first parameter, sequence item is the second
   * @param source sequence to process
   * @param unit initial value of the accumulator (like the initial 0 for summing)
   * @return value of the accumulator after all the list is processed.
   */
  public static <R> R fold(@NotNull Lambda2<R, R, R> lambda, @NotNull Iterable<R> source, @NotNull final R unit) {
    R ret = unit;
    for (R item : source) ret = lambda.apply(ret, item);
    return ret;
  }

  /**
   * Same as fold(), but the arguments of lambda are reversed: the accumulator is the second (right) parameter.
   * @param lambda function to apply; sequence item is the first parameter, accumulator is the second
   * @param source sequence to process
   * @param unit initial value of the accumulator (like the initial 0 for summing)
   * @return value of the accumulator after all the sequence is processed.
   */
  public static <R> R foldr(@NotNull Lambda2<R, R, R> lambda, @NotNull Iterable<R> source, @NotNull final R unit) {
    R ret = unit;
    for (R item : source) ret = lambda.apply(item, ret);
    return ret;
  }

  /**
   * Zips together two sequences: [a, b,..] + [x, y,..] -> [(a, x), (b, y),..].
   * If sequences are of different length, uses up the shortest of the sequences; the rest of the longer sequence is unused.
   * @param one source of first elements
   * @param two source of second elements, possibly shorter
   * @return list of pairs of elements
   */
  public static <R1, R2> List<Pair<R1, R2>> zip(Iterable<R1> one, Iterable<R2> two) {
    return zipInternal(one, two, null, null, false, false);
  }


  /**
   * Zips together two sequences: [a, b,..] + [x, y,..] -> [(a, x), (b, y),..]. Fills missing second elements with filler.
   * Always uses up entire sequence one; if sequence two is longer, part of it is unused.
   * @param one source of first elements
   * @param two source of second elements, possibly shorter
   * @param filler value to use instead of elements of sequence two if it is shorter than sequence one 
   * @return list of pairs of elements
   */
  public static <R1, R2> List<Pair<R1, R2>> zip(Iterable<R1> one, Iterable<R2> two, R2 filler) {
    return zipInternal(one, two, null, filler, false, true);
  }

  /**
   * Zips together two sequences: [a, b,..] + [x, y,..] -> [(a, x), (b, y),..]. Fills all missing elements with filler.
   * Always uses up both sequences, using the appropriate filler for elements of the shorter sequences.
   * @param one sequences of first elements
   * @param two sequences of second elements, possibly shorter
   * @param filler1 value to use instead of elements of sequences one if it is shorter than list two
   * @param filler2 value to use instead of elements of sequences two if it is shorter than list one
   * @return list of pairs of elements
   */
  public static <R1, R2> List<Pair<R1, R2>> zip(Iterable<R1> one, Iterable<R2> two, R1 filler1, R2 filler2) {
    return zipInternal(one, two, filler1, filler2, true, true);
  }

  /**
   * Zips two lists.
   * @param one first elements
   * @param two second elements
   * @param filler1 to fill missing first elements
   * @param filler2 to fill missing second elements
   * @param fill1 use filler1 if list one is too short
   * @param fill2 use filler2 if list two is too short
   * @return zipped list
   */
  private static <R1, R2> List<Pair<R1, R2>> zipInternal(Iterable<R1> one, Iterable<R2> two, R1 filler1, R2 filler2, boolean fill1, boolean fill2) {
    // a boring premature optimization which tries to preallocate an array list of exactly the right size
    int size1 = 0;
    int size2 = 0;
    int approx_size = 0;
    if (one instanceof List) size1 = ((List)one).size();
    if (two instanceof List) size2 = ((List)two).size();
    if (fill1 && fill2) approx_size = Math.max(size1, size2);
    if (!fill1 && !fill2) approx_size = Math.min(size1, size2);
    if (fill1 && !fill2) approx_size = size1;
    if (!fill1 && fill2) approx_size = size2;
    if (approx_size == 0) approx_size = 10;
    List<Pair<R1, R2>> ret = new ArrayList<Pair<R1, R2>>(approx_size);
    // the gist
    Iterator<R1> one_iter = one.iterator();
    Iterator<R2> two_iter = two.iterator();
    while (one_iter.hasNext() && two_iter.hasNext()) ret.add(new Pair<R1, R2>(one_iter.next(), two_iter.next()));
    while (fill1 && two_iter.hasNext()) ret.add(new Pair<R1, R2>(filler1, two_iter.next()));
    while (fill2 && one_iter.hasNext()) ret.add(new Pair<R1, R2>(one_iter.next(), filler2));
    return ret;
  }

  /**
   * Combines two functions so that the second is applied to the result of the first.
   * @param f first (inner) function
   * @param g second (outer) function
   * @return their combination, f o g
   */
  public static <A1, R1, R2> Lambda1<A1, R2> combine(final Lambda1<A1, R1> f, final Lambda1<R1, R2> g) {
    return new Lambda1<A1, R2>() {
      public R2 apply(A1 arg) {
        return g.apply(f.apply(arg));
      }
    };
  }

  // TODO: add slices, array wrapping %)

  /**
   * One-argument lambda (function).
   */
  public interface Lambda1<A, R> {
    R apply(A arg);
  }

  /**
   * Two-arguments lambda (function).
   */
  public interface Lambda2<A1, A2, R> {
    R apply(A1 arg1, A2 arg2);
  }
}
