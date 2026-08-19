// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.java.rt.gatherers;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Gatherer;

/// Wraps a [Gatherer] so that every element it pushes downstream is recorded together with
/// the upstream elements it came from, expressed as their "source times" (values of the shared
/// `time` clock). Each push appends zero or more source times to `sourceTimes`.
/// `sourceTimes` stores source times for all outputs consecutively; `sourceOffsets` marks where each
/// output's range starts and ends.
///
/// For example, consider:
///
/// ```java
/// Stream.of("a", "b", "c")
///   .gather(Gatherers.windowSliding(2))
/// ```
///
/// Suppose the inputs `a`, `b`, and `c` have trace times `1`, `2`, and `4`; the emitted windows
/// `[a, b]` and `[b, c]` have trace times `3` and `5`. The tracing state changes as follows:
///
/// - Initially: `pending = []`, `sourceTimes = []`, `sourceOffsets = [0]`.
/// - After `a`: `pending = [1]`; no window is emitted.
/// - After `b`: `pending = [1, 2]`; pushing `[a, b]` appends that range, producing
///   `sourceTimes = [1, 2]` and `sourceOffsets = [0, 2]`.
/// - After `c`: the rolling window becomes `pending = [2, 4]`; pushing `[b, c]` produces
///   `sourceTimes = [1, 2, 2, 4]` and `sourceOffsets = [0, 2, 4]`.
///
/// The interpreter takes `sourceTimes[sourceOffsets[i]..<sourceOffsets[i + 1]]` for output `i`.
/// Thus output time `3` has sources `[1, 2]`, while output time `5` has sources `[2, 4]`, yielding:
///
/// ```text
/// direct:  1 -> 3; 2 -> 3, 5; 4 -> 5
/// reverse: 3 <- 1, 2; 5 <- 2, 4
/// ```
///
/// ### Why it works like that
///
/// The simplest idea, pairing the input and output sequences by position, works for one-to-one operators
/// but not for gatherers: a gatherer may emit an arbitrary number of elements, or reorder them
/// (fold to a single value, group into windows, and `mapConcurrent` even pushes results out
/// of step with the calls that produced them). There is no positional or by-value correspondence to
/// rely on, so instead the link is captured at the one moment it is known for certain: the push.
///   - The wrapper does not change what the gatherer does. The initializer, integrator, combiner and
///     finisher are all delegated.
///   - The integrator keeps its greedy flag ([Gatherer.Integrator.Greedy]) so short-circuiting is unchanged
///   - The wrapped [Gatherer.Downstream] forwards `isRejecting()` so a rejecting downstream still stops
///     the gatherer promptly.
///
/// ### Value matching
///
///     A pushed value is matched to its source elements in one of four ways, chosen automatically on the
///     first `integrate()` call (see [#detectMode]):
///
///     1. [MODE_PER_CALL], the default, used by every regular gatherer.
///     A regular gatherer pushes synchronously, inside `integrate()`, on the same thread that feeds
///     it elements. So whatever is pushed while element `k` is being integrated belongs to `k`:
///     we read the clock when `integrate()` starts and attach that value to
///     every push made during the call. A value emitted later from `finisher()` (for example a
///     half-full buffer) has no current element, so it is recorded with an empty source range.
///
///     We use it as fallback when we can't detect more specific Gatherer whose semantics we know.
///     In this case, we believe that mapping `integrate -> push` is the best we can do.
///
///     2. [MODE_MAP_CONCURRENT], only for `Gatherers.mapConcurrent`.
///     What allows us to do instrumentation is that `mapConcurrent` produces exactly one result per
///     element and keeps encounter order, so we use a FIFO queue: `integrate()` adds the source time
///     to the back, and each push takes one from the front. The first result out matches the first element
///     in, the second matches the second, and so on, no matter when each result is actually pushed
///     (mid-run or from the finisher).
///
///     3. [MODE_WINDOW_FIXED], for `Gatherers.windowFixed`.
///     Source times are accumulated until a window is pushed, then all accumulated times are assigned to it
///     and the buffer is cleared. This also covers a partial final window pushed by the finisher.
///
///     4. [MODE_WINDOW_SLIDING], for `Gatherers.windowSliding`.
///     Source times are kept in a rolling deque. The window size is learned from the first pushed window;
///     subsequent pushes are assigned to the current contents of the deque. If the input is shorter than the
///     requested window size, the single partial window pushed by the finisher is handled in the same way.
///
///     The mode is detected automatically based on the state object type. Window gatherers have recognizable
///     state class names; `mapConcurrent` uses a generic state class declared inside its factory method and is
///     identified through `getEnclosingMethod()`. This works independently of how the gatherer value reached
///     `Stream.gather()`.
///     Every other gatherer, a stateless one (`state == null`), or any reflection failure falls back to per-call mode.
public final class TracingGathererFactory {
  // Sentinel
  private static final int MODE_UNDETERMINED = -1;
  // Each push is associated with the input element currently being integrated
  private static final int MODE_PER_CALL = 0;
  // Ordered 1-to-1 via a FIFO of source times, special case for Gatherers.mapConcurrent.
  private static final int MODE_MAP_CONCURRENT = 1;
  // Every pushed fixed window is associated with all inputs accumulated since the previous push.
  private static final int MODE_WINDOW_FIXED = 2;
  // Every pushed sliding window is associated with the inputs in the current sliding window.
  private static final int MODE_WINDOW_SLIDING = 3;

  // Marks a push that has no current input element, i.e. one made from the finisher.
  private static final int NO_SOURCE_TIME = -1;

  private TracingGathererFactory() { }

  /**
   * @param gatherer      the original gatherer (as an {@code Object}, since it is passed from the debuggee)
   * @param time          the shared element clock used by the surrounding peek trace
   * @param sourceOffsets out-parameter: start offsets of source-time ranges, one more entry than pushed elements
   * @param sourceTimes   out-parameter: source times for all pushed elements, stored consecutively
   * @return a tracing gatherer, or {@code gatherer} unchanged if it is not a {@link Gatherer}
   */
  public static Object wrap(Object gatherer,
                            AtomicInteger time,
                            List<Integer> sourceOffsets,
                            List<Integer> sourceTimes) {
    if (!(gatherer instanceof Gatherer<?, ?, ?> delegate)) {
      return gatherer;
    }
    return wrapTyped(delegate, new TracingState(time, sourceOffsets, sourceTimes));
  }

  private static <T, A, R> Gatherer<T, ?, R> wrapTyped(Gatherer<T, A, R> delegate, TracingState tracingState) {
    Gatherer.Integrator<A, T, R> original = delegate.integrator();
    boolean greedy = original instanceof Gatherer.Integrator.Greedy;

    Gatherer.Integrator<A, T, R> wrappedIntegrator = greedy
      ? Gatherer.Integrator.ofGreedy((state, element, downstream) -> integrate(original, state, element, downstream, tracingState))
      : Gatherer.Integrator.of((state, element, downstream) -> integrate(original, state, element, downstream, tracingState));

    BiConsumer<A, Gatherer.Downstream<? super R>> originalFinisher = delegate.finisher();
    BiConsumer<A, Gatherer.Downstream<? super R>> wrappedFinisher = (state, downstream) ->
      originalFinisher.accept(state, new TracingDownstream<>(downstream, tracingState, NO_SOURCE_TIME));

    // The combiner only merges two partial gatherer states during parallel evaluation.
    // It receives no Downstream and pushes nothing, so there is no push for us to attribute to a source.
    // In any case, the stream debugger forces every traced stream to be sequential before tracing
    // (injecting `.sequential()` at the source and after any `.parallel()` call),
    // so parallel evaluation is eliminated, and the combiner is never invoked.
    // So we delegate it unchanged.
    return Gatherer.of(delegate.initializer(), wrappedIntegrator, delegate.combiner(), wrappedFinisher);
  }

  private static <T, A, R> boolean integrate(Gatherer.Integrator<A, T, R> original,
                                             A state,
                                             T element,
                                             Gatherer.Downstream<? super R> downstream,
                                             TracingState tracingState) {
    int sourceTime = tracingState.onInputElement(state);
    return original.integrate(state, element, new TracingDownstream<>(downstream, tracingState, sourceTime));
  }

  private static final class TracingState {
    /// Out-parameter: start offsets of source-time ranges, one more entry than pushed elements
    private final List<Integer> sourceOffsets;
    /// Out-parameter: source times for all pushed elements, stored consecutively
    private final List<Integer> sourceTimes;
    /// The shared clock used by the surrounding peek trace
    private final AtomicInteger time;
    /// Timestamps of elements that are not being pushed to the downstream yet
    private final Deque<Integer> pendingElementTimes = new ArrayDeque<>();
    /// Detected lazily on the first `integrate()` call, see [#detectMode]
    private int mode = MODE_UNDETERMINED;
    /// The size is detected from the first window emitted by windowSliding
    private int slidingWindowSize = -1;

    private TracingState(AtomicInteger time, List<Integer> sourceOffsets, List<Integer> sourceTimes) {
      this.time = time;
      this.sourceOffsets = sourceOffsets;
      this.sourceTimes = sourceTimes;
      // Record initial offset
      sourceOffsets.add(sourceTimes.size());
    }

    /// Handles the element that is about to be integrated, before the delegate makes any push.
    ///
    /// @return the source time of that element
    private int onInputElement(Object gathererState) {
      // Detect on the first integrate, before the enqueue decision below and before any downstream push
      if (mode == MODE_UNDETERMINED) {
        mode = detectMode(gathererState);
      }
      int sourceTime = time.get();
      // These modes may emit an output for buffered inputs, so retain their source times until push().
      if (mode == MODE_MAP_CONCURRENT || mode == MODE_WINDOW_FIXED || mode == MODE_WINDOW_SLIDING) {
        pendingElementTimes.addLast(sourceTime);
      }
      // Once the window size is known, discard the input that has fallen out of the sliding window.
      if (mode == MODE_WINDOW_SLIDING && slidingWindowSize > 0 && pendingElementTimes.size() > slidingWindowSize) {
        pendingElementTimes.removeFirst();
      }
      return sourceTime;
    }

    /// Records the source times of the element being pushed downstream
    ///
    /// @param sourceTime the time of the element being integrated, or [#NO_SOURCE_TIME] for a push made from the finisher
    private void onOutputElement(int sourceTime) {
      switch (mode) {
        case MODE_MAP_CONCURRENT -> {
          if (!pendingElementTimes.isEmpty()) {
            sourceTimes.add(pendingElementTimes.removeFirst());
          }
        }
        case MODE_WINDOW_FIXED -> {
          sourceTimes.addAll(pendingElementTimes);
          pendingElementTimes.clear();
        }
        case MODE_WINDOW_SLIDING -> {
          if (slidingWindowSize < 0) {
            slidingWindowSize = pendingElementTimes.size();
          }
          sourceTimes.addAll(pendingElementTimes);
        }
        default -> {
          if (sourceTime != NO_SOURCE_TIME) {
            sourceTimes.add(sourceTime);
          }
        }
      }
      sourceOffsets.add(sourceTimes.size());
    }
  }

  private static final class TracingDownstream<R> implements Gatherer.Downstream<R> {
    private final Gatherer.Downstream<? super R> delegate;
    private final TracingState tracingState;
    private final int sourceTime;

    private TracingDownstream(Gatherer.Downstream<? super R> delegate, TracingState tracingState, int sourceTime) {
      this.delegate = delegate;
      this.tracingState = tracingState;
      this.sourceTime = sourceTime;
    }

    @Override
    public boolean push(R element) {
      tracingState.onOutputElement(sourceTime);
      return delegate.push(element);
    }

    @Override
    public boolean isRejecting() {
      return delegate.isRejecting();
    }
  }

  /**
   * Detects supported {@code java.util.stream.Gatherers} implementations by inspecting their state object.
   * <p>
   * Falls back to {@link #MODE_PER_CALL} for any other gatherer, a stateless gatherer ({@code state == null}),
   * or if reflection fails.
   */
  private static int detectMode(Object state) {
    try {
      if (state != null) {
        Class<?> stateClass = state.getClass();
        String stateClassName = stateClass.getName();
        if (stateClassName.startsWith("java.util.stream.Gatherers$")) {
          if (stateClassName.endsWith("FixedWindow")) {
            return MODE_WINDOW_FIXED;
          }

          if (stateClassName.endsWith("SlidingWindow")) {
            return MODE_WINDOW_SLIDING;
          }
        }

        Method enclosing = stateClass.getEnclosingMethod();
        if (enclosing != null
            && "mapConcurrent".equals(enclosing.getName())
            && "java.util.stream.Gatherers".equals(enclosing.getDeclaringClass().getName())) {
          return MODE_MAP_CONCURRENT;
        }
      }
    }
    catch (Throwable ignored) {
      // Fall through to per-call attribution.
    }
    return MODE_PER_CALL;
  }
}
