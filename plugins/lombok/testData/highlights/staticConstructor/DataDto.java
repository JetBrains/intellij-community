import lombok.Data;

@Data(staticConstructor = "o3f")
public class DataDto {
    private final int someInt;
    public static void main(String[] args) {
        DataDto.o3f(2);
    }
}
