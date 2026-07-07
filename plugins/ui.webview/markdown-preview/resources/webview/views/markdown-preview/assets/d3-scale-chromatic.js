//#region node_modules/.bun/d3-scale-chromatic@3.1.0/node_modules/d3-scale-chromatic/src/colors.js
function colors_default(specifier) {
	var n = specifier.length / 6 | 0, colors = new Array(n), i = 0;
	while (i < n) colors[i] = "#" + specifier.slice(i * 6, ++i * 6);
	return colors;
}
//#endregion
//#region node_modules/.bun/d3-scale-chromatic@3.1.0/node_modules/d3-scale-chromatic/src/categorical/Tableau10.js
var Tableau10_default = colors_default("4e79a7f28e2ce1575976b7b259a14fedc949af7aa1ff9da79c755fbab0ab");
//#endregion
export { Tableau10_default as t };
