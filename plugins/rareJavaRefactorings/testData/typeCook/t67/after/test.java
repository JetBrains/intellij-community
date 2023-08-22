class Map<T, X> {
  T t;
  X x;
}

class Pam<Y> extends Map<Integer, Y> {
}

class Test {

    Map f (Pam x){
      return x;
    }

    void foo() {
      Pam x = new Pam ();

      x.x = "";

      Map y = f (x);
    }
}