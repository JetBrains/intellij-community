interface I<T>{}

class Pair<X extends I<Y>, Y extends I<X>> {
}

public class Test {
  Pair pair;
}