public class Test {
  public Test(int j, int... i){}
  void foo(){}
  public static void main(String[] args){
    new Builder().setJ(1).setI(2, 3).createTest().foo();
  }
}