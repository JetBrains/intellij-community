// !ADD_JAVA_API
// JVM_TARGET: 17
import javaApi.Anon5;
import javaApi.TypeUseAnon1;

public record J(@Anon5(42) int x, @TypeUseAnon1 int y) {
}