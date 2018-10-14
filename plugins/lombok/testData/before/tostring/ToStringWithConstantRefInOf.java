//skip compare contents
import lombok.ToString;

@ToString(of = ToStringWithConstantRefInOf.FIELD_NAME)
public class ToStringWithConstantRefInOf {
	static final String FIELD_NAME = "id";
	private String id;
	private int whatever;
}

