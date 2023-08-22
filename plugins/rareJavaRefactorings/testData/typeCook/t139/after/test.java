class List<Z>{
  <T extends Z> void put(T t){
  }

  void mut(List<? extends Z> t){
  }
}


class Test{
  void foo(){
    List x = null;

    x.mut(new List<Integer>());
  }
}