/**
* Generate a check from a test.
*
* Useful if you’re going to test many nodes, for example when creating a
* utility where something else passes a compatible test.
*
* The created function is a bit faster because it expects valid input only:
* an `element`, `index`, and `parent`.
*
* @param test
*   A test for a specific element.
* @returns
*   A check.
*/
var convertElement = (
/**
* @param {Test | null | undefined} [test]
* @returns {Check}
*/
function(test) {
	if (test === null || test === void 0) return element;
	if (typeof test === "string") return tagNameFactory(test);
	if (typeof test === "object") return anyFactory(test);
	if (typeof test === "function") return castFactory(test);
	throw new Error("Expected function, string, or array as `test`");
});
/**
* Handle multiple tests.
*
* @param {Array<TestFunction | string>} tests
* @returns {Check}
*/
function anyFactory(tests) {
	/** @type {Array<Check>} */
	const checks = [];
	let index = -1;
	while (++index < tests.length) checks[index] = convertElement(tests[index]);
	return castFactory(any);
	/**
	* @this {unknown}
	* @type {TestFunction}
	*/
	function any(...parameters) {
		let index = -1;
		while (++index < checks.length) if (checks[index].apply(this, parameters)) return true;
		return false;
	}
}
/**
* Turn a string into a test for an element with a certain type.
*
* @param {string} check
* @returns {Check}
*/
function tagNameFactory(check) {
	return castFactory(tagName);
	/**
	* @param {Element} element
	* @returns {boolean}
	*/
	function tagName(element) {
		return element.tagName === check;
	}
}
/**
* Turn a custom test into a test for an element that passes that test.
*
* @param {TestFunction} testFunction
* @returns {Check}
*/
function castFactory(testFunction) {
	return check;
	/**
	* @this {unknown}
	* @type {Check}
	*/
	function check(value, index, parent) {
		return Boolean(looksLikeAnElement(value) && testFunction.call(this, value, typeof index === "number" ? index : void 0, parent || void 0));
	}
}
/**
* Make sure something is an element.
*
* @param {unknown} element
* @returns {element is Element}
*/
function element(element) {
	return Boolean(element && typeof element === "object" && "type" in element && element.type === "element" && "tagName" in element && typeof element.tagName === "string");
}
/**
* @param {unknown} value
* @returns {value is Element}
*/
function looksLikeAnElement(value) {
	return value !== null && typeof value === "object" && "type" in value && "tagName" in value;
}
export { convertElement as t };
