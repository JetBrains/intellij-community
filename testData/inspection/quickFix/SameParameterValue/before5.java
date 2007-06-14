// "f 5" "true"
public class Test {
  void foo(int <caret>f){
    if (f == 5) {
      Syste.out.print(f);
    }
  }
  void bar(){foo(5);}
  void bar1(){foo(5);}
}