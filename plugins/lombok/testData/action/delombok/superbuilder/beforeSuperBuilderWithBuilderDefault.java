import java.util.ArrayList;
import java.util.List;

@lombok.experimental.SuperBuilder
public class SuperBuilderWithBuilderDefault {
    <caret>
    @lombok.Builder.Default
    private List<String> listItems = new ArrayList<>();

}