class Test {
  int myT;
  {
    myT = 0;
  }
  
  public int getMyT() {
    return myT;
  }
  void bar(){
    int i = myT;
  }
}