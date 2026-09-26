@lombok.ToString(of = {"x", "z"}, exclude = "x")
class ToStringExplicitOfAndExclude {
  int x;
  float y;
  long z;
  String name;
}
