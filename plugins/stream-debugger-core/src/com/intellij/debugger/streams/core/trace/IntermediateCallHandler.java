// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.trace;

import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface IntermediateCallHandler extends TraceHandler, CallTransformer<IntermediateStreamCall> {
  @NotNull
  List<IntermediateStreamCall> additionalCallsBefore();

  /**
   *
   * <p>There are situations in some languages when you can't insert a `peek` call between two intermediate methods, because it'll change
   * the resulting type and break the next call. So we need a way to treat such chains of calls as a single inseparable chain.</p>
   *
   * <p>Example from C#: `[0, 1, 2].OrderBy(x => x).ThenBy(x => -x).ToList()`
   * OrderBy returns IOrderedEnumerable`1 and ThenBy expects IOrderedEnumerable`1, but the peek call(`Select`) will return base
   * interface IEnumerable`1, so we must treat ThenBy call as an inseparable.</p>
   *
   * @return List of methods, that should be called right after the current method and which are "inseparable".
   * It means it is impossible to insert a peek call between them, without breaking type chain.
   */
  @NotNull
  default List<IntermediateStreamCall> additionalInseparableCalls() {
    return Collections.emptyList();
  }

  @NotNull
  List<IntermediateStreamCall> additionalCallsAfter();
}
