class List<T> {
  T t;
}

class Test{
  class Super {
     void f (List x){
       x = new List<Integer>();
     }
  }

  class Middle extends Super {
    void f (List x){
       x.t = "";
    }

    List g (){
     return new List<Integer>();
   }
  }

  class Sub extends Middle{
     List g (){

    }
  }
}