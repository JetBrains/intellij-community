// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

class DeduplicationCollector<Result extends MavenDependencyCompletionItem>
  implements
  Collector<Result, Map<String, Result>, List<Result>> {
  static final Set<Characteristics> CHARACTERISTICS
    = Collections.unmodifiableSet(EnumSet.of(Characteristics.UNORDERED));

  private final Function<Result, String> myDeduplicationKey;

  DeduplicationCollector(Function<Result, String> deduplicationKey) {
    myDeduplicationKey = deduplicationKey;
  }

  @Override
  public Supplier<Map<String, Result>> supplier() {
    return () -> new HashMap<>();
  }

  @Override
  public BiConsumer<Map<String, Result>, Result> accumulator() {
    return (m, item) -> {
      if (item != null && m != null) {
          String key = myDeduplicationKey.apply(item);
          Result present = m.get(key);
          if (present == null || present.getType().getWeight() < item.getType().getWeight()) {
            m.put(key, item);
        }
      }
    };
  }

  @Override
  public BinaryOperator<Map<String, Result>> combiner() {
    return (m, l) -> {
      for (Result item : l.values()) {
        String key = myDeduplicationKey.apply(item);
        Result present = m.get(key);
        if (present != null && item.getType() == null) {
          continue;
        }
        if (present == null ||
            present.getType() == null && item.getType() != null ||
            present.getType().getWeight() < item.getType().getWeight()) {
          m.put(key, item);
        }
      }
      return m;
    };
  }

  @Override
  public Function<Map<String, Result>, List<Result>> finisher() {
    return m -> new ArrayList<>(m.values());
  }

  @Override
  public Set<Characteristics> characteristics() {
    return CHARACTERISTICS;
  }
}
