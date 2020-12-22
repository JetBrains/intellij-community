@lombok.ToString(of = {})
class ToStringExplicitEmptyOf1 {
  int x;
  String name;
}

@lombok.ToString(of = "")
class ToStringExplicitEmptyOf2 {
  int x;
  String name;
}
