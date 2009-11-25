import java.util.Set;

interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Set<? super Subject> myField;

	public void meth() {
		Set<Ancestor> ancestors = null;
		myField = ancestors;
		Set<? super Subject> ancestorExtends = null;
		myField = ancestorExtends;
		Set<? super Ancestor> ancestorSupers = null;
		myField = ancestorSupers;

		Set<Subject> subjects = null;
		myField = subjects;
		Set<? super Subject> subjectExtends = null;
		myField = subjectExtends;
		Set<? super Subject> subjectSupers = null;
		myField = subjectSupers;

		Set<? super Subject> descendants = null;
		myField = descendants;
		Set<? super Subject> descendantExtends = null;
		myField = descendantExtends;
		Set<? super Subject> descendantSupers = null;
		myField = descendantSupers;

		Set set = null;
		myField = set;
	}
}
