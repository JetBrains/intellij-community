package slowCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

/**
 * @author peter
 */
public class Checker {

  public static <T> void forAll(Generator<T> gen, Predicate<T> property) {
    forAll(CheckerSettings.DEFAULT_SETTINGS, gen, property);
  }

  public static <T> void forAll(CheckerSettings settings, Generator<T> gen, Predicate<T> property) {
    Integer seed = settings.randomSeed;
    if (seed == null) {
      seed = new Random().nextInt();
    }
    Random random = new Random(seed);
    for (int i = 0; i < settings.iterationCount; i++) {
      PropertyFailureImpl<T> failure = iteration(gen, property, random);
      if (failure != null) {
        throw new PropertyFalsified(seed, failure, () -> new ReplayDataStructure(failure.getMinimalCounterexample().data));
      }
    }
  }

  @Nullable
  private static <T> PropertyFailureImpl<T> iteration(Generator<T> gen, Predicate<T> property, Random random) {
    StructureNode node = new StructureNode();
    T value = gen.generateUnstructured(new GenerativeDataStructure(random, node));
    CounterExampleImpl<T> failure = CounterExampleImpl.checkProperty(property, value, node);
    return failure != null ? new PropertyFailureImpl<>(failure, gen, property) : null;
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
    boolean success = false;
    Throwable exception = null;
    try {
      success = property.test(value);
    }
    catch (Throwable e) {
      exception = e;
    }
    //noinspection UseOfSystemOutOrSystemErr
    System.out.print(success ? "." : "!");
    return success ? null : new CounterExampleImpl<>(node, value, exception);
  }

}

class PropertyFailureImpl<T> implements PropertyFailure<T> {
  private final CounterExampleImpl<T> initial;
  private CounterExampleImpl<T> minimized;
  private int totalSteps;

  PropertyFailureImpl(@NotNull CounterExampleImpl<T> initial, Generator<T> gen, Predicate<T> property) {
    this.initial = initial;
    this.minimized = initial;
    shrink(gen, property);
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

  @Override
  public int getTotalMinimizationStepCount() {
    return totalSteps;
  }

  private void shrink(Generator<T> gen, Predicate<T> property) {
    while (true) {
      Optional<CounterExampleImpl<T>> shrank = minimized.data.shrink().map(node -> {
        try {
          T value = gen.generateUnstructured(new ReplayDataStructure(node));
          totalSteps++;
          return CounterExampleImpl.checkProperty(property, value, node);
        }
        catch (CannotRestoreValue e) {
          return null;
        }
      }).filter(Objects::nonNull).findFirst();
      if (shrank.isPresent()) {
        minimized = shrank.get();
      } else {
        break;
      }
    }
  }

}