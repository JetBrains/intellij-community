@lombok.EqualsAndHashCode
class EqualsAndHashCode {
  int x;
  boolean[] y;
  Object[] z;
  String a;
}

@lombok.EqualsAndHashCode
final class EqualsAndHashCode2 {
  int x;
}

@lombok.EqualsAndHashCode(callSuper = false)
final class EqualsAndHashCode3 extends EqualsAndHashCode {
}

@lombok.EqualsAndHashCode(callSuper = true)
class EqualsAndHashCode4 extends EqualsAndHashCode {
}