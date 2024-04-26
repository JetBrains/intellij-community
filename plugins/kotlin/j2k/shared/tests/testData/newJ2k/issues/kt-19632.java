// IGNORE_K2

import java.util.function.Predicate;

public class TestSamInitializedWithLambda {
    public final Predicate<String> isEmpty =
            x -> x.length() == 0;
}