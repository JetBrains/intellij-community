import lombok.AccessLevel;

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.PRIVATE)
class RequiredArgsConstructorStaticNameAccessPrivate {
  final int x;
  String name;
}

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.PROTECTED)
class RequiredArgsConstructorStaticNameAccessProtected {
  final int x;
  String name;
}

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.PACKAGE)
class RequiredArgsConstructorStaticNameAccessPackage {
  final int x;
  String name;
}

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.MODULE)
class RequiredArgsConstructorStaticNameAccessModule {
  final int x;
  String name;
}

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.PUBLIC)
class RequiredArgsConstructorStaticNameAccessPublic {
  final int x;
  String name;
}

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.NONE)
class RequiredArgsConstructorStaticNameAccessNone {
  final int x;
  String name;
}
