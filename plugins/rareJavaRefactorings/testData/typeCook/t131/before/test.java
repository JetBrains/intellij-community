class List<T> {
   T t;
}

class MyList<P> extends List<P>{

}

public class Test {
  MyList[] l;

  List[] f (){
    return l;
  }

  void g(){
    l[0].t = "";
  }
}
