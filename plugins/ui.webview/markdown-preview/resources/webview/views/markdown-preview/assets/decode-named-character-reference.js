//#region node_modules/.bun/decode-named-character-reference@1.3.0/node_modules/decode-named-character-reference/index.dom.js
var element = document.createElement("i");
/**
* @param {string} value
* @returns {string | false}
*/
function decodeNamedCharacterReference(value) {
	const characterReference = "&" + value + ";";
	element.innerHTML = characterReference;
	const character = element.textContent;
	if (character.charCodeAt(character.length - 1) === 59 && value !== "semi") return false;
	return character === characterReference ? false : character;
}
//#endregion
export { decodeNamedCharacterReference as t };
