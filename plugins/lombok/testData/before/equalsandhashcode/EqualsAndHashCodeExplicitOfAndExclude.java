@lombok.EqualsAndHashCode(of = {"x", "z"}, exclude = {"x", "y"})
class EqualsAndHashCode {
  int x;
  boolean[] y;
  Object[] z;
  String a;
  String b;
}

