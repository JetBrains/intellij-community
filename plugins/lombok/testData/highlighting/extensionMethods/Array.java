
import lombok.experimental.ExtensionMethod;
@ExtensionMethod({java.util.Arrays.class})
class ArrayExample {
  void m(int[] array) {
    array.sort();
  }
}
