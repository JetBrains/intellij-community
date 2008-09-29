import java.util.*;
class Wrapper {
  List<CharSequence> myField;
  Wrapper(List<CharSequence> s) {
    myField = s;
  }

  List<CharSequence> getMyField() {
    return myField;
  }
}