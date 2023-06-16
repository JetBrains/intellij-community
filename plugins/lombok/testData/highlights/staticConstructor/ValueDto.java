import lombok.Value;

@Value(staticConstructor = "o3f")
public class ValueDto {
    private int someInt;
    public static void main(String[] args) {
        ValueDto.o3f(2);
    }
}
