class List<T> {
  T t;
}

class Mist extends List{

}

class Test {
  void foo (){
    List x = new Mist();

    x.t = "";
  }
}
