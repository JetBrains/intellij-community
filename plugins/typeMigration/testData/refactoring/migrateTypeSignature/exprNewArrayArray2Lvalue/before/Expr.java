interface FaceParent {}
interface FaceChild extends FaceParent {}

class Expr {
	private FaceChild[][] myArrayOne;
	private FaceChild[][][] myArrayTwo;
	public void meth(FaceChild[] pfc) {
		myArrayOne = new FaceChild[][]{pfc};
		myArrayTwo = new FaceChild[][][]{{pfc}};
	}
}
