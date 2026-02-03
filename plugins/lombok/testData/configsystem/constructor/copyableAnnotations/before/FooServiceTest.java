import lombok.RequiredArgsConstructor;

@interface javax.inject.Inject {

}

@interface javax.inject.Named {
  String value() default "";
}

@RequiredArgsConstructor(onConstructor_ = {@javax.inject.Inject, @})
public class FooServiceTest {
  @javax.inject.Named("someQualifier")
  private final String someDependencyWithQualifier;
  private final String someDependencyWithoutQualifier;
  //methods go here
}
