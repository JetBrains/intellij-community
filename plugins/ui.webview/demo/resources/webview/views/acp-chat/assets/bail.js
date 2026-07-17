/**
* Throw a given error.
*
* @param {Error|null|undefined} [error]
*   Maybe error.
* @returns {asserts error is null|undefined}
*/
function bail(error) {
	if (error) throw error;
}
export { bail as t };
