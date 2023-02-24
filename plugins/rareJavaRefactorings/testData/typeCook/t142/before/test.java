interface I<T>{}

class Pair<X extends I<I<? extends Y>>, Y extends I<I<? super X>>> {
}

public class Test {
  Pair pair;
}