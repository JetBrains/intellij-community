class A {}
class B {}

class Map<X, Y>{
 Y get (X x){
  return null;
 }
}

interface List<T> {
  void add(T t);
}

class ArrayList<E> implements List<E>{
  public void add(E e){
  }
}

public class Test {
  private static void f(Map requestMap) {
    ArrayList requests = (ArrayList)requestMap.get(new A());
    requests.add(new Object());
    f(new Map<A, ArrayList<B>> ());
  }
}
