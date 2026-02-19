import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(staticName="of", access = AccessLevel.PRIVATE)
class AllArgsConstructorStaticNameAccessPrivate {
  final int x;
  String name;
}

@AllArgsConstructor(staticName="of", access = AccessLevel.PROTECTED)
class AllArgsConstructorStaticNameAccessProtected {
  final int x;
  String name;
}

@AllArgsConstructor(staticName="of", access = AccessLevel.PACKAGE)
class AllArgsConstructorStaticNameAccessPackage {
  final int x;
  String name;
}

@AllArgsConstructor(staticName="of", access = AccessLevel.MODULE)
class AllArgsConstructorStaticNameAccessModule {
  final int x;
  String name;
}

@AllArgsConstructor(staticName="of", access = AccessLevel.PUBLIC)
class AllArgsConstructorStaticNameAccessPublic {
  final int x;
  String name;
}

@AllArgsConstructor(staticName="of", access = AccessLevel.NONE)
class AllArgsConstructorStaticNameAccessNone {
  final int x;
  String name;
}
