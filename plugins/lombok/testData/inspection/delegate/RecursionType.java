public class RecursionType {
  <error descr="@Delegate does not support recursion (delegating to a type that itself has @Delegate members). Member \"invalidField\" is @Delegate in type \"RecursionType\""><error descr="@Delegate does not support recursion (delegating to a type that itself has @Delegate members). Member \"invalidMethod\" is @Delegate in type \"RecursionType\"">@lombok.Delegate</error></error>
  private RecursionType invalidField;

  <error descr="@Delegate does not support recursion (delegating to a type that itself has @Delegate members). Member \"invalidField\" is @Delegate in type \"RecursionType\""><error descr="@Delegate does not support recursion (delegating to a type that itself has @Delegate members). Member \"invalidMethod\" is @Delegate in type \"RecursionType\"">@lombok.Delegate</error></error>
  private RecursionType invalidMethod() {
    return new RecursionType();
  }
}
