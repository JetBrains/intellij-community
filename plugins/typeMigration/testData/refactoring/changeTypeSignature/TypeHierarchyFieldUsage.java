import java.util.List;
class A<E> {
  E e;
  List<E> list = new List<E>();
}

class B<T> extends A<T> {}

class C extends B <S<caret>tring> {
   void foo() {
     if (e == null && list != null) {
        //do smth
     }
   }
}