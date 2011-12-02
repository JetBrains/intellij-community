@lombok.RequiredArgsConstructor
class RequiredArgsConstructor1 {
  final int x;
  String name;
}

@lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PROTECTED)
class RequiredArgsConstructorAccess {
  final int x;
  String name;
}

@lombok.RequiredArgsConstructor(staticName = "staticname")
class RequiredArgsConstructorStaticName {
  final int x;
  String name;
}

@lombok.AllArgsConstructor
class AllArgsConstructor1 {
  final int x;
  String name;
}

@lombok.NoArgsConstructor
class NoArgsConstructor1 {
  final int x;
  String name;
}