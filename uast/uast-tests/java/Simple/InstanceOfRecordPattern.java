public class RecordPattern {
  public static void main(String[] args) {
    Box<Ball> b = new Box<>(new RedBall());
    if (b instanceof Box(RedBall a)) { }
    if (b instanceof Box(GreenBall _)) { }
    if (b instanceof Box(_)) { }
  }
}

record Box<T extends Ball>(T content) { }

sealed abstract class Ball permits RedBall, BlueBall, GreenBall { }
final class RedBall extends Ball { }
final class BlueBall extends Ball { }
final class GreenBall extends Ball { }