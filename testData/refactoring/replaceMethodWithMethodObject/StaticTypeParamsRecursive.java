class Test {
     void bar() {
       foo("");
     }
     static <T> void f<caret>oo(T t){
       if (t != null) foo(t);
     }
}