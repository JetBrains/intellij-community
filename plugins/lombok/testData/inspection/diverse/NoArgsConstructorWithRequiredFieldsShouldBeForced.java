<error descr="Class contains required fields, you have to force NoArgsConstructor.">@lombok.NoArgsConstructor</error>
public class NoArgsConstructorWithRequiredFieldsShouldBeForced {
  <error descr="Variable 'test' might not have been initialized">private final String test</error>;
  <error descr="Variable 'test2' might not have been initialized">private final String test2</error>;
  <error descr="Variable 'test3' might not have been initialized">private final int test3</error>;
}