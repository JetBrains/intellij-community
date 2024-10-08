package foo

public sealed class SealedClass
public class Impl1<caret> : SealedClass() {}
public class Impl2 : SealedClass() {}
public class Impl3<caret> : SealedClass() {}