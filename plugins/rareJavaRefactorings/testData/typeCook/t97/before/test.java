class List<T> {
  T t;
}

interface A {}

class Test{
  A foo(){
    List x = null;
    x.t = "";

    return (A) x;
  }
}