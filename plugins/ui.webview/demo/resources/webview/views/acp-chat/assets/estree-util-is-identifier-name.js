//#region node_modules/estree-util-is-identifier-name/lib/index.js
var nameRe = /^[$_\p{ID_Start}][$_\u{200C}\u{200D}\p{ID_Continue}]*$/u;
var nameReJsx = /^[$_\p{ID_Start}][-$_\u{200C}\u{200D}\p{ID_Continue}]*$/u;
/** @type {Options} */
var emptyOptions = {};
/**
* Checks if the given value is a valid identifier name.
*
* @param {string} name
*   Identifier to check.
* @param {Options | null | undefined} [options]
*   Configuration (optional).
* @returns {boolean}
*   Whether `name` can be an identifier.
*/
function name(name, options) {
	return ((options || emptyOptions).jsx ? nameReJsx : nameRe).test(name);
}
//#endregion
export { name as t };
