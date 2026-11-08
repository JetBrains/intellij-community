public class TypePattern {
    public static int foo(Number x) {
      Box<Ball> b = new Box<>(null);
      switch (b) {
        case Box(RedBall _), Box(BlueBall _) -> System.out.println("red or blue");
        case Box(GreenBall a) -> System.out.println("green");
        case Box(_) -> System.out.println("null");
      }
    }
}

record Box<T extends Ball>(T content) { }

sealed abstract class Ball permits RedBall, BlueBall, GreenBall { }
final class RedBall extends Ball { }
final class BlueBall extends Ball { }
final class GreenBall extends Ball { }