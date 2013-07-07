import lombok.Data;
@lombok.Data class Data1 {
	final int x;
	String name;
}
@Data class Data2 {
	final int x;
	String name;
}
final @Data class Data3 {
	final int x;
	String name;
}
@Data 
@lombok.EqualsAndHashCode(callSuper=true)
final class Data4 extends java.util.Timer {
	int x;
	Data4() {
		super();
	}
}
@Data
class Data5 {
}
@Data
final class Data6 {
}
