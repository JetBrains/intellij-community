class Map<X,Y>{
  void put (X x, Y y){
  }

  Y get(X x){
    return null;
  }
}

class List<Z>{
  Z z;
}

class Test{
  Map<List, Object> y;

  void foo(){
    List<Integer> li = null;
    y.put(li, "");
  }

  <T> void f (List<T> x, T z){
    y.put(x, z);
  }
}