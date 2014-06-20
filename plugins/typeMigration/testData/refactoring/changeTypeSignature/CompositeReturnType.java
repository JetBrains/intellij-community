import java.util.List;
class A<T> {
  List<T> getKey(){return null;}
}
public class B extends A<S<caret>tring> {
   List<String> getKey() {
     return new List<String>();
   }
}
