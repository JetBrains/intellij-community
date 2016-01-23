public class ValWithLabel {
	{
		LABEL: for (final java.lang.String x : new String[0]) {
			if (x.toLowerCase() == null) {
				continue LABEL;
			}
		}
	}
}