import <info descr="Not resolved until the project is fully loaded">lombok</info>.<info descr="Not resolved until the project is fully loaded">Getter</info>;
import <info descr="Not resolved until the project is fully loaded">lombok</info>.<info descr="Not resolved until the project is fully loaded">Setter</info>;
import <info descr="Not resolved until the project is fully loaded">lombok</info>.<info descr="Not resolved until the project is fully loaded">SuperBuilder</info>;

public final class LombokBasicsWithExplicitImport {
  public static void main(String[] args) {
    UserDao userDao = UserDao.<info descr="Not resolved until the project is fully loaded">builder</info>()
      .<info descr="Not resolved until the project is fully loaded">info</info>("1")
      .<info descr="Not resolved until the project is fully loaded">build</info>();
    String name = userDao.<info descr="Not resolved until the project is fully loaded">name</info>();
    UserChain userChain = new UserChain();
    String name1 = userChain.<info descr="Not resolved until the project is fully loaded">getName</info>();
  }
}

@<info descr="Not resolved until the project is fully loaded">Getter</info>
@<info descr="Not resolved until the project is fully loaded">SuperBuilder</info>
class UserDao extends UserId {
  <info descr="Not resolved until the project is fully loaded">private final String name;</info>
  <info descr="Not resolved until the project is fully loaded">private final String surname;</info>
  <info descr="Not resolved until the project is fully loaded">private final String email;</info>
}

@<info descr="Not resolved until the project is fully loaded">SuperBuilder</info>
abstract class UserId {
  <info descr="Not resolved until the project is fully loaded">private final long id;</info>
  <info descr="Not resolved until the project is fully loaded">private final String info;</info>
}

class UserChain {
  @<info descr="Not resolved until the project is fully loaded">Getter</info>
  @<info descr="Not resolved until the project is fully loaded">Setter</info>
  private String name;
}