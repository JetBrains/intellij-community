@interface javax.inject.Inject {

}

@interface javax.inject.Named {
  String value() default "";
}

public class FooServiceTest {
  @javax.inject.Named("someQualifier")
  private final String someDependencyWithQualifier;
  private final String someDependencyWithoutQualifier;

  @javax.inject.Inject
  @java.beans.ConstructorProperties({"someDependencyWithQualifier", "someDependencyWithoutQualifier"})
  public FooServiceTest(@javax.inject.Named("someQualifier") String someDependencyWithQualifier,
                    String someDependencyWithoutQualifier) {
    this.someDependencyWithQualifier = someDependencyWithQualifier;
    this.someDependencyWithoutQualifier = someDependencyWithoutQualifier;
  }
}
