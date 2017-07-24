package slowCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface PropertyFailure<T> {
  @NotNull
  CounterExample<T> getFirstCounterExample();

  @NotNull
  CounterExample<T> getMinimalCounterexample();

  @Nullable
  Throwable getStoppingReason();
  
  int getTotalMinimizationExampleCount();
  
  int getMinimizationStageCount();
  
  int getIterationNumber();
  
  long getIterationSeed();
  
  long getGlobalSeed();
  
  int getSizeHint();

  interface CounterExample<T> {
    T getExampleValue();

    @Nullable
    Throwable getExceptionCause();
  }
  
}
