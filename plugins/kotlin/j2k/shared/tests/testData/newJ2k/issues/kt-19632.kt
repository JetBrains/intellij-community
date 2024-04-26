import java.util.function.Predicate

class TestSamInitializedWithLambda {
    val isEmpty: Predicate<String> = Predicate { x: String -> x.length == 0 }
}
