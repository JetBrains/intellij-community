package slowCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author peter
 */
public class PropertyChecker<T> {
  private static final Predicate<Object> DATA_IS_DIFFERENT = new Predicate<Object>() {
    @Override
    public boolean test(Object o) {
      return false;
    }

    @Override
    public String toString() {
      return ": cannot generate enough sufficiently different values";
    }
  };
  private Generator<T> generator;
  private Predicate<T> property;
  private final Set<Integer> generatedHashes = new HashSet<>();
  private final long seed;
  private final Random random;
  private final StatusNotifier notifier;

  private PropertyChecker(Generator<T> generator, Predicate<T> property, CheckerSettings settings) {
    this.generator = generator;
    this.property = property;

    Long seed = settings.randomSeed;
    if (seed == null) {
      seed = new Random().nextLong();
    }
    this.seed = seed;
    random = new Random(seed);
    notifier = new StatusNotifier(settings.iterationCount, this.seed);
  }

  public static <T> void forAll(Generator<T> gen, Predicate<T> property) {
    forAll(CheckerSettings.DEFAULT_SETTINGS, gen, property);
  }

  public static <T> void forAll(CheckerSettings settings, Generator<T> gen, Predicate<T> property) {
    new PropertyChecker<>(gen, property, settings).forAll(settings.iterationCount);
  }

  private void forAll(int iterationCount) {
    for (int i = 1; i <= iterationCount; i++) {
      notifier.iterationStarted(i);

      CounterExampleImpl<T> example = findCounterExample(i);
      if (example != null) {
        notifier.counterExampleFound();
        PropertyFailureImpl failure = new PropertyFailureImpl(example, i);
        throw new PropertyFalsified(seed, failure, () -> new ReplayDataStructure(failure.getMinimalCounterexample().data, failure.sizeHint));
      }
    }
  }

  @Nullable
  private CounterExampleImpl<T> findCounterExample(int sizeHint) {
    for (int i = 0; i < 100; i++) {
      StructureNode node = new StructureNode();
      T value;
      try {
        value = generator.generateUnstructured(new GenerativeDataStructure(random, node, sizeHint));
      }
      catch (Throwable e) {
        throw new GeneratorException(seed, e);
      }
      if (!generatedHashes.add(node.hashCode())) continue;
      
      return CounterExampleImpl.checkProperty(property, value, node);
    }
    throw new CannotSatisfyCondition(DATA_IS_DIFFERENT);
  }

  private class PropertyFailureImpl implements PropertyFailure<T> {
    private final CounterExampleImpl<T> initial;
    private CounterExampleImpl<T> minimized;
    private int totalSteps;
    private int successfulSteps;
    private int sizeHint;
    private Throwable stoppingReason;

    PropertyFailureImpl(@NotNull CounterExampleImpl<T> initial, int sizeHint) {
      this.initial = initial;
      this.minimized = initial;
      this.sizeHint = sizeHint;
      try {
        shrink();
      }
      catch (Throwable e) {
        stoppingReason = e;
      }
    }

    @NotNull
    @Override
    public CounterExampleImpl<T> getFirstCounterExample() {
      return initial;
    }

    @NotNull
    @Override
    public CounterExampleImpl<T> getMinimalCounterexample() {
      return minimized;
    }

    @Nullable
    @Override
    public Throwable getStoppingReason() {
      return stoppingReason;
    }

    @Override
    public int getTotalMinimizationExampleCount() {
      return totalSteps;
    }

    @Override
    public int getMinimizationStageCount() {
      return successfulSteps;
    }

    private void shrink() {
      ShrinkRunner shrinkRunner = new ShrinkRunner();
      while (true) {
        CounterExampleImpl<T> shrank = shrinkRunner.findShrink(minimized.data, node -> {
          if (!generatedHashes.add(node.hashCode())) return null;

          notifier.shrinkAttempt(this);
          
          try {
            T value = generator.generateUnstructured(new ReplayDataStructure(node, sizeHint));
            totalSteps++;
            return CounterExampleImpl.checkProperty(property, value, node);
          }
          catch (CannotRestoreValue e) {
            return null;
          }
        });
        if (shrank != null) {
          minimized = shrank;
          successfulSteps++;
        } else {
          break;
        }
      }
    }


  }
}

class CounterExampleImpl<T> implements PropertyFailure.CounterExample<T> {
  final StructureNode data;
  private final T value;
  @Nullable private final Throwable exception;

  private CounterExampleImpl(StructureNode data, T value, @Nullable Throwable exception) {
    this.data = data;
    this.value = value;
    this.exception = exception;
  }

  @Override
  public T getExampleValue() {
    return value;
  }

  @Nullable
  @Override
  public Throwable getExceptionCause() {
    return exception;
  }

  static <T> CounterExampleImpl<T> checkProperty(Predicate<T> property, T value, StructureNode node) {
    try {
      if (!property.test(value)) {
        return new CounterExampleImpl<>(node, value, null);
      }
    }
    catch (Throwable e) {
      return new CounterExampleImpl<>(node, value, e);
    }
    return null;
  }

}

