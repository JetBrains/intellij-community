// "b false" "true"
public class Test {
  void foo(boolean f<caret>){
    if (f) {
      Syste.out.print(f);
    }
  }
  void bar(){foo(false);}
  void bar1(){foo(true);}
}