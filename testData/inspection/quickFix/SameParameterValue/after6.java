// "f 5" "true"
public class Test {
  void foo(<caret>){
    Syste.out.print(d(new int[]{5}));
  }
  int d(int[] d){}
  void bar(){foo();}
  void bar1(){foo();}
}