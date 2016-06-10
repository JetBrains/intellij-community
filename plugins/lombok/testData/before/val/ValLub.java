class ValLub {
	public void easyLub() {
		java.util.Map<String, Number> m = java.util.Collections.emptyMap();

		lombok.val foo = (System.currentTimeMillis() > 0) ? m : java.util.Collections.<String, Number>emptyMap();
	}

	public void sillyLubWithUnboxingThatProducesErrorThatVarIsPrimitive() {
		Integer i = 20;
		Double d = 20.0;

		lombok.val thisShouldBePrimitiveDouble = (System.currentTimeMillis() > 0) ? i : d;
	}

	public void hardLub() {
		java.util.List<String> list = new java.util.ArrayList<String>();
		java.util.Set<String> set = new java.util.HashSet<String>();

		lombok.val thisShouldBeCollection = (System.currentTimeMillis() > 0) ? list : set;
		thisShouldBeCollection.add("");
		String foo = thisShouldBeCollection.iterator().next();
	}
}
