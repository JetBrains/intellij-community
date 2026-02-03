import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class DataIssueEvent {
    private Integer dataIssueLevel;
    private String whereIsItComingFrom;
    private String message;
    private Exception exceptionNullable;
    private String documentationNoteIdNullable;

    public String toDisplayString() {

        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s][%s] %s",
                getDataIssueLevel(),
                getWhereIsItComingFrom().toLowerCase(),
                message));
        return sb.toString();
    }
}