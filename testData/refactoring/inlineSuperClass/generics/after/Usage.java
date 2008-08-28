import p.Test;

class Usage {
  Test<String> myField;

  Test<String> xxx(){
    return myField;
  }

  Test<Integer> xxxxx() {
    return null;
  }

  void bar(Test<String> s) {
    s.foo(null);
  }


}