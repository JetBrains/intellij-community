import { t as renderMathInElement } from "./katex.js";
//#region views/markdown-preview/src/markdownLatex.ts
var latexDelimiters = [
	{
		left: "$$",
		right: "$$",
		display: true
	},
	{
		left: "\\[",
		right: "\\]",
		display: true
	},
	{
		left: "\\(",
		right: "\\)",
		display: false
	},
	{
		left: "$",
		right: "$",
		display: false
	},
	{
		left: "\\begin{equation}",
		right: "\\end{equation}",
		display: true
	},
	{
		left: "\\begin{align}",
		right: "\\end{align}",
		display: true
	},
	{
		left: "\\begin{alignat}",
		right: "\\end{alignat}",
		display: true
	},
	{
		left: "\\begin{gather}",
		right: "\\end{gather}",
		display: true
	},
	{
		left: "\\begin{CD}",
		right: "\\end{CD}",
		display: true
	}
];
function renderMarkdownLatex() {
	const contentElement = document.getElementById("content");
	if (!contentElement) return;
	renderMathInElement(contentElement, {
		delimiters: latexDelimiters,
		ignoredClasses: ["katex"],
		throwOnError: false
	});
}
//#endregion
export { renderMarkdownLatex };
