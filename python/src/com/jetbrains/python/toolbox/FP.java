/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.toolbox;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
* Tools of functional programming, the notorious half of implementation of Lisp. (And sometimes a shard or two of Haskell.)
* User: dcheryasov
* Date: Nov 6, 2009 10:06:50 AM
*/
public class FP {
  
  private FP() {
    // do not instantiate
  }

  /**
   * [a, b,..] -> [lambda(a), lambda(b),...]
   * Action is lazy: function is applied to source items only as soon as values are extracted from the resulting iterable. 
   * @param lambda function to apply
   * @param source list to process
   * @return list of mapped values.
   */
  @NotNull
  public static <S, R> Iterable<R> map(@NotNull final Lambda1<S, R> lambda, @NotNull final Iterable<S> source) {

    return new Iterable<R>() {
      final Iterator<S> feeder = source.iterator();
      public Iterator<R> iterator() {
        return new Iterator<R>() {

          public boolean hasNext() {
            return feeder.hasNext();
          }

          public R next() {
            return lambda.apply(feeder.next());
          }

          public void remove() {
            throw new UnsupportedOperationException("Cannot remove from map()");
          }
        };
      }
    };
  }

  /**
   * Convenience form of {@link #map(Lambda1, Iterable)}.
   */
  @NotNull
  public static <S, R> Iterable<R> map(@NotNull final Lambda1<S, R> lambda, @NotNull final S[] source) {
    return map(lambda, Arrays.asList(source));
  }


  /**
   * Same as {@link #map}, but non-lazy an returns a modifiable List.
   */
  public static <S, R> List<R> mapList(@NotNull final Lambda1<S, R> lambda, @NotNull final Iterable<S> source) {
    List<R> ret = new ArrayList<>(source instanceof Collection ? ((Collection)source).size() : 10);
    for (R what : map(lambda, source)) ret.add(what);
    return ret;
  }


  /**
   * Apply a two-argument lambda to each sequence element and an accumulator.
   * @param lambda function to apply; accumulator is the first parameter, sequence item is the second
   * @param source sequence to process
   * @param unit initial value of the accumulator (like the initial 0 for summing)
   * @param <ItemT> type of items in the list
   * @param <AccT> 'accumulator' type; can be same as ItemT, or reasonably different (consider ItemT=String and AccT=StringBuilder)
   * @return value of the accumulator after all the list is processed.
   */
  public static <AccT, ItemT> AccT fold(@NotNull Lambda2<AccT, ItemT, AccT> lambda, @NotNull Iterable<ItemT> source, @NotNull final AccT unit) {
    AccT ret = unit;
    for (ItemT item : source) ret = lambda.apply(ret, item);
    return ret;
  }

  /**
   * Same as fold(), but the arguments of lambda are reversed: the accumulator is the second (right) parameter.
   * @param lambda function to apply; sequence item is the first parameter, accumulator is the second
   * @param source sequence to process
   * @param unit initial value of the accumulator (like the initial 0 for summing)
   * @param <ItemT> type of items in the list
   * @param <AccT> 'accumulator' type
   * @return value of the accumulator after all the list is processed.
   */
  public static <AccT, ItemT> AccT foldr(@NotNull Lambda2<ItemT, AccT, AccT> lambda, @NotNull Iterable<ItemT> source, @NotNull final AccT unit) {
    AccT ret = unit;
    for (ItemT item : source) ret = lambda.apply(item, ret);
    return ret;
  }

  /**
   * Zips together two sequences: [a, b,..] + [x, y,..] -> [(a, x), (b, y),..].
   * If sequences are of different length, uses up the shortest of the sequences; the rest of the longer sequence is unused.
   * The action is lazy: either iterable is only accessed as many times as the result.
   * @param one source of first elements
   * @param two source of second elements, possibly shorter
   * @return list of pairs of elements
   */
  public static <R1, R2> Iterable<Pair<R1, R2>> zip(Iterable<R1> one, Iterable<R2> two) {
    return zipInternal(one, two, null, null, false, false);
  }

  /**
   * Same as {@link #zip(Iterable, Iterable)}, but non-lazy and returns a modifiable List.
   */
  public static <R1, R2> List<Pair<R1, R2>> zipList(Iterable<R1> one, Iterable<R2> two) {
    List<Pair<R1, R2>> ret = new ArrayList<>(proposeZippedListLength(one, two, false, false));
    for (Pair<R1, R2>what : zipInternal(one, two, null, null, false, false)) ret.add(what);
    return ret;
  }


  /**
   * Zips together two sequences: [a, b,..] + [x, y,..] -> [(a, x), (b, y),..]. Fills missing second elements with filler.
   * Always uses up entire sequence one; if sequence two is longer, part of it is unused.
   * The action is lazy: either iterable is only accessed as many times as the result.
   * @param one source of first elements
   * @param two source of second elements, possibly shorter
   * @param filler value to use instead of elements of sequence two if it is shorter than sequence one 
   * @return list of pairs of elements
   */
  public static <R1, R2> Iterable<Pair<R1, R2>> zip(Iterable<R1> one, Iterable<R2> two, R2 filler) {
    return zipInternal(one, two, null, filler, false, true);
  }

  /**
   * Same as {@link #zip(Iterable, Iterable, Object)}, but non-lazy and returns a modifiable List.
   */
  public static <R1, R2> List<Pair<R1, R2>> zipList(Iterable<R1> one, Iterable<R2> two, R2 filler) {
    List<Pair<R1, R2>> ret = new ArrayList<>(proposeZippedListLength(one, two, false, true));
    for (Pair<R1, R2>what : zipInternal(one, two, null, filler, false, true)) ret.add(what);
    return ret;
  }


  /**
   * Zips together two sequences: [a, b,..] + [x, y,..] -> [(a, x), (b, y),..]. Fills all missing elements with filler.
   * Always uses up both sequences, using the appropriate filler for elements of the shorter sequences.
   * The action is lazy: either iterable is only accessed as many times as the result.
   * @param one sequences of first elements
   * @param two sequences of second elements, possibly shorter
   * @param filler1 value to use instead of elements of sequences one if it is shorter than list two
   * @param filler2 value to use instead of elements of sequences two if it is shorter than list one
   * @return list of pairs of elements
   */
  public static <R1, R2> Iterable<Pair<R1, R2>> zip(Iterable<R1> one, Iterable<R2> two, R1 filler1, R2 filler2) {
    return zipInternal(one, two, filler1, filler2, true, true);
  }

  /**
   * Same as {@link #zip(Iterable, Iterable, Object, Object)}, but non-lazy and returns a modifiable List.
   */
  public static <R1, R2> List<Pair<R1, R2>> zipList(Iterable<R1> one, Iterable<R2> two, R1 filler1, R2 filler2) {
    List<Pair<R1, R2>> ret = new ArrayList<>(proposeZippedListLength(one, two, true, true));
    for (Pair<R1, R2>what : zipInternal(one, two, filler1, filler2, true, true)) ret.add(what);
    return ret;
  }

  /**
   * Tries to determine the size of an array list of exactly the right size to accommodate 
   * the result of {@link #zip(Iterable, Iterable, Object, Object)}. 
   * @param one first iterbale
   * @param two second iterable
   * @param fill1 true if padding of iterable one is required 
   * @param fill2  true if padding of iterable two is required
   * @return size, if it can be determined, or 10 (which is the default size of ArrayList).
   */
  public static int proposeZippedListLength(Iterable one, Iterable two, boolean fill1, boolean fill2) {
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
    return approx_size;
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
  private static <R1, R2> Iterable<Pair<R1, R2>> zipInternal(
    Iterable<R1> one, Iterable<R2> two, final R1 filler1, final R2 filler2, final boolean fill1, final boolean fill2
  ) {
    final Iterator<R1> one_iter = one.iterator();
    final Iterator<R2> two_iter = two.iterator();
    
    return new Iterable<Pair<R1, R2>>() {
      public Iterator<Pair<R1, R2>> iterator() {
        
        return new Iterator<Pair<R1, R2>>() {

          public void remove() {
            throw new UnsupportedOperationException("Cannot remove from zip()");
          }

          public boolean hasNext() {
            final boolean one_has = one_iter.hasNext();
            final boolean two_has = two_iter.hasNext();
            return (
              one_has && two_has ||
              fill1 && two_has ||
              fill2 && one_has
            );
          }

          public Pair<R1, R2> next() {
            if (one_iter.hasNext() && two_iter.hasNext()) return Pair.create(one_iter.next(), two_iter.next());
            if (fill1 && two_iter.hasNext()) return Pair.create(filler1, two_iter.next());
            if (fill2 && one_iter.hasNext()) return Pair.create(one_iter.next(), filler2);
            throw new NoSuchElementException();
          }
        }; 
      }
    };
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


  /**
   * Useful for {@link FP#fold(Lambda2, Iterable, Object) fold}ing into a string. Element's {@code .toString()} is appended to the string builder.
   */
  public static class StringCollector<T> implements FP.Lambda2<StringBuilder, T, StringBuilder>  {
    public StringBuilder apply(StringBuilder builder, T arg2) {
      if (arg2 == null) {
        throw new IllegalArgumentException("Null item in list of strings to concatenate. Text so far: " + builder.toString());
      }
      return builder.append(arg2.toString());
    }
  }

}
