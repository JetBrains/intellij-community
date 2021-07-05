import java.util.List;
class Wrapper<T> {
  List<T> myField;
  Wrapper(List<T> s) {
    myField = s;
  }

  List<T> getMyField() {
    return myField;
  }
}