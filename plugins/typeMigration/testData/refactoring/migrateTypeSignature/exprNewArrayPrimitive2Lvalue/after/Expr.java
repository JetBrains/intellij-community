class Expr {
	private int[] myArrayOne;
	private int[][] myArrayTwo;
	public void meth(int p) {
		myArrayOne = new int[]{p};
		myArrayTwo = new int[][]{{p}, {!p}};
	}
}
