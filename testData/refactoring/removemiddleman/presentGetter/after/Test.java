class Test implements I {
  A myField;
  A getMyField(){
    return myField;
  }

    void bar(I i) {
        i.getMyField().foo();
  }
}