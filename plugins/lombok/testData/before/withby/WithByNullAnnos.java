//CONF: lombok.addNullAnnotations=checkerframework
import java.util.List;
@lombok.RequiredArgsConstructor
public class WithByNullAnnos {
	@lombok.experimental.WithBy final List<String> test;
}
