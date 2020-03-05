import lombok.val;
public class ValClass {
  public void test() {
    <warning descr="'val' already marks variables final.">final</warning> val FIVE = 5;
  }
}
