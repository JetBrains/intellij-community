interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Ancestor[] myAncestors;
	private Subject[] mySubjects;
	private Subject[] myDescendants;

	public void meth(Subject... p) {
		myAncestors = p;
		mySubjects = p;
		myDescendants = p;
	}
}
