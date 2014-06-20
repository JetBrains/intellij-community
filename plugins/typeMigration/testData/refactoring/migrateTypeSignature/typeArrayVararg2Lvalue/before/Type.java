interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Ancestor[] myAncestors;
	private Subject[] mySubjects;
	private Descendant[] myDescendants;

	public void meth(Descendant[] p) {
		myAncestors = p;
		mySubjects = p;
		myDescendants = p;
	}
}
