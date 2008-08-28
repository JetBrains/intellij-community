import p1.*;
class Usage {
  Super<String> myField;

  Super<String> xxx(){
    return myField;
  }

  Super<Integer> xxxxx() {
    return null;
  }

  void bar(Super<String> s) {
    s.foo(null);
  }


}