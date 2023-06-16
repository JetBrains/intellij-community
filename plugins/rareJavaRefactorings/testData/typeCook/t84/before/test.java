class List<T> {
  T t;
}

class Test {
  List[] foo (){
    List x = null;
    x.t = "";

    return new List[]{x};
  }
}
