class List<T> {
  T t;
}

class Test{
  class Middle extends Super {
    List g (){
     return new List<Integer>();
   }
  }

  class Sub extends Middle{
     List g (){
    }
  }
}