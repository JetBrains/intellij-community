class List<T> {
}

class Mist extends List<String>{
}

class foo<X extends List>{
  X x;
}

class Test {
  void foo (){
    Mist y = null;
    foo x = null;
    x.x = y;
  }
}
