class List<Z>{
  void <T extends Z> put(T t){
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