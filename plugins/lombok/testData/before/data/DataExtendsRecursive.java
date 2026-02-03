import lombok.Data;

import java.util.Date;

@Data
public class DataExtendsRecursive extends DataExtendsRecursive{
    private int someInt;
    private String someString;
    private Date someDate;
}
