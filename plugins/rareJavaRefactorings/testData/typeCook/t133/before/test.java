class List<X>{
  X x;
}

interface I {}

public class Test {
  void foo(){
    List x = null;
    x.x = new I(){
    };
  }
}
