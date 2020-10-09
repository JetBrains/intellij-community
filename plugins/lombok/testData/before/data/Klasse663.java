import lombok.Data;

@Data
public class Klasse663 {
  private Object[] objects;

  public void setObjects(Object... objects) {
  }

  public static void main(String[] args) {
    new Klasse663().setObjects(1, 2, 3);
  }
}
