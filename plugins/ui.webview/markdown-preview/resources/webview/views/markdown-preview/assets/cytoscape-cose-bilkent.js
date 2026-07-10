import { t as __commonJSMin } from "./rolldown-runtime.js";
import { t as require_cose_base } from "./cose-base.js";
var require_cytoscape_cose_bilkent = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	(function webpackUniversalModuleDefinition(root, factory) {
		if (typeof exports === "object" && typeof module === "object") module.exports = factory(require_cose_base());
		else if (typeof define === "function" && define.amd) define(["cose-base"], factory);
		else if (typeof exports === "object") exports["cytoscapeCoseBilkent"] = factory(require_cose_base());
		else root["cytoscapeCoseBilkent"] = factory(root["coseBase"]);
	})(exports, function(__WEBPACK_EXTERNAL_MODULE_0__) {
		return (function(modules) {
			var installedModules = {};
			function __webpack_require__(moduleId) {
				if (installedModules[moduleId]) return installedModules[moduleId].exports;
				var module$1 = installedModules[moduleId] = {
					i: moduleId,
					l: false,
					exports: {}
				};
				modules[moduleId].call(module$1.exports, module$1, module$1.exports, __webpack_require__);
				module$1.l = true;
				return module$1.exports;
			}
			__webpack_require__.m = modules;
			__webpack_require__.c = installedModules;
			__webpack_require__.i = function(value) {
				return value;
			};
			__webpack_require__.d = function(exports$1, name, getter) {
				if (!__webpack_require__.o(exports$1, name)) Object.defineProperty(exports$1, name, {
					configurable: false,
					enumerable: true,
					get: getter
				});
			};
			__webpack_require__.n = function(module$2) {
				var getter = module$2 && module$2.__esModule ? function getDefault() {
					return module$2["default"];
				} : function getModuleExports() {
					return module$2;
				};
				__webpack_require__.d(getter, "a", getter);
				return getter;
			};
			__webpack_require__.o = function(object, property) {
				return Object.prototype.hasOwnProperty.call(object, property);
			};
			__webpack_require__.p = "";
			return __webpack_require__(__webpack_require__.s = 1);
		})([(function(module$3, exports$2) {
			module$3.exports = __WEBPACK_EXTERNAL_MODULE_0__;
		}), (function(module$4, exports$3, __webpack_require__) {
			"use strict";
			var LayoutConstants = __webpack_require__(0).layoutBase.LayoutConstants;
			var FDLayoutConstants = __webpack_require__(0).layoutBase.FDLayoutConstants;
			var CoSEConstants = __webpack_require__(0).CoSEConstants;
			var CoSELayout = __webpack_require__(0).CoSELayout;
			var CoSENode = __webpack_require__(0).CoSENode;
			var PointD = __webpack_require__(0).layoutBase.PointD;
			var DimensionD = __webpack_require__(0).layoutBase.DimensionD;
			var defaults = {
				ready: function ready() {},
				stop: function stop() {},
				quality: "default",
				nodeDimensionsIncludeLabels: false,
				refresh: 30,
				fit: true,
				padding: 10,
				randomize: true,
				nodeRepulsion: 4500,
				idealEdgeLength: 50,
				edgeElasticity: .45,
				nestingFactor: .1,
				gravity: .25,
				numIter: 2500,
				tile: true,
				animate: "end",
				animationDuration: 500,
				tilingPaddingVertical: 10,
				tilingPaddingHorizontal: 10,
				gravityRangeCompound: 1.5,
				gravityCompound: 1,
				gravityRange: 3.8,
				initialEnergyOnIncremental: .5
			};
			function extend(defaults, options) {
				var obj = {};
				for (var i in defaults) obj[i] = defaults[i];
				for (var i in options) obj[i] = options[i];
				return obj;
			}
			function _CoSELayout(_options) {
				this.options = extend(defaults, _options);
				getUserOptions(this.options);
			}
			var getUserOptions = function getUserOptions(options) {
				if (options.nodeRepulsion != null) CoSEConstants.DEFAULT_REPULSION_STRENGTH = FDLayoutConstants.DEFAULT_REPULSION_STRENGTH = options.nodeRepulsion;
				if (options.idealEdgeLength != null) CoSEConstants.DEFAULT_EDGE_LENGTH = FDLayoutConstants.DEFAULT_EDGE_LENGTH = options.idealEdgeLength;
				if (options.edgeElasticity != null) CoSEConstants.DEFAULT_SPRING_STRENGTH = FDLayoutConstants.DEFAULT_SPRING_STRENGTH = options.edgeElasticity;
				if (options.nestingFactor != null) CoSEConstants.PER_LEVEL_IDEAL_EDGE_LENGTH_FACTOR = FDLayoutConstants.PER_LEVEL_IDEAL_EDGE_LENGTH_FACTOR = options.nestingFactor;
				if (options.gravity != null) CoSEConstants.DEFAULT_GRAVITY_STRENGTH = FDLayoutConstants.DEFAULT_GRAVITY_STRENGTH = options.gravity;
				if (options.numIter != null) CoSEConstants.MAX_ITERATIONS = FDLayoutConstants.MAX_ITERATIONS = options.numIter;
				if (options.gravityRange != null) CoSEConstants.DEFAULT_GRAVITY_RANGE_FACTOR = FDLayoutConstants.DEFAULT_GRAVITY_RANGE_FACTOR = options.gravityRange;
				if (options.gravityCompound != null) CoSEConstants.DEFAULT_COMPOUND_GRAVITY_STRENGTH = FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_STRENGTH = options.gravityCompound;
				if (options.gravityRangeCompound != null) CoSEConstants.DEFAULT_COMPOUND_GRAVITY_RANGE_FACTOR = FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_RANGE_FACTOR = options.gravityRangeCompound;
				if (options.initialEnergyOnIncremental != null) CoSEConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL = FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL = options.initialEnergyOnIncremental;
				if (options.quality == "draft") LayoutConstants.QUALITY = 0;
				else if (options.quality == "proof") LayoutConstants.QUALITY = 2;
				else LayoutConstants.QUALITY = 1;
				CoSEConstants.NODE_DIMENSIONS_INCLUDE_LABELS = FDLayoutConstants.NODE_DIMENSIONS_INCLUDE_LABELS = LayoutConstants.NODE_DIMENSIONS_INCLUDE_LABELS = options.nodeDimensionsIncludeLabels;
				CoSEConstants.DEFAULT_INCREMENTAL = FDLayoutConstants.DEFAULT_INCREMENTAL = LayoutConstants.DEFAULT_INCREMENTAL = !options.randomize;
				CoSEConstants.ANIMATE = FDLayoutConstants.ANIMATE = LayoutConstants.ANIMATE = options.animate;
				CoSEConstants.TILE = options.tile;
				CoSEConstants.TILING_PADDING_VERTICAL = typeof options.tilingPaddingVertical === "function" ? options.tilingPaddingVertical.call() : options.tilingPaddingVertical;
				CoSEConstants.TILING_PADDING_HORIZONTAL = typeof options.tilingPaddingHorizontal === "function" ? options.tilingPaddingHorizontal.call() : options.tilingPaddingHorizontal;
			};
			_CoSELayout.prototype.run = function() {
				var ready;
				var frameId;
				var options = this.options;
				this.idToLNode = {};
				var layout = this.layout = new CoSELayout();
				var self = this;
				self.stopped = false;
				this.cy = this.options.cy;
				this.cy.trigger({
					type: "layoutstart",
					layout: this
				});
				var gm = layout.newGraphManager();
				this.gm = gm;
				var nodes = this.options.eles.nodes();
				var edges = this.options.eles.edges();
				this.root = gm.addRoot();
				this.processChildrenList(this.root, this.getTopMostNodes(nodes), layout);
				for (var i = 0; i < edges.length; i++) {
					var edge = edges[i];
					var sourceNode = this.idToLNode[edge.data("source")];
					var targetNode = this.idToLNode[edge.data("target")];
					if (sourceNode !== targetNode && sourceNode.getEdgesBetween(targetNode).length == 0) {
						var e1 = gm.add(layout.newEdge(), sourceNode, targetNode);
						e1.id = edge.id();
					}
				}
				var getPositions = function getPositions(ele, i) {
					if (typeof ele === "number") ele = i;
					var theId = ele.data("id");
					var lNode = self.idToLNode[theId];
					return {
						x: lNode.getRect().getCenterX(),
						y: lNode.getRect().getCenterY()
					};
				};
				var iterateAnimated = function iterateAnimated() {
					var afterReposition = function afterReposition() {
						if (options.fit) options.cy.fit(options.eles, options.padding);
						if (!ready) {
							ready = true;
							self.cy.one("layoutready", options.ready);
							self.cy.trigger({
								type: "layoutready",
								layout: self
							});
						}
					};
					var ticksPerFrame = self.options.refresh;
					var isDone;
					for (var i = 0; i < ticksPerFrame && !isDone; i++) isDone = self.stopped || self.layout.tick();
					if (isDone) {
						if (layout.checkLayoutSuccess() && !layout.isSubLayout) layout.doPostLayout();
						if (layout.tilingPostLayout) layout.tilingPostLayout();
						layout.isLayoutFinished = true;
						self.options.eles.nodes().positions(getPositions);
						afterReposition();
						self.cy.one("layoutstop", self.options.stop);
						self.cy.trigger({
							type: "layoutstop",
							layout: self
						});
						if (frameId) cancelAnimationFrame(frameId);
						ready = false;
						return;
					}
					var animationData = self.layout.getPositionsData();
					options.eles.nodes().positions(function(ele, i) {
						if (typeof ele === "number") ele = i;
						if (!ele.isParent()) {
							var theId = ele.id();
							var pNode = animationData[theId];
							var temp = ele;
							while (pNode == null) {
								pNode = animationData[temp.data("parent")] || animationData["DummyCompound_" + temp.data("parent")];
								animationData[theId] = pNode;
								temp = temp.parent()[0];
								if (temp == void 0) break;
							}
							if (pNode != null) return {
								x: pNode.x,
								y: pNode.y
							};
							else return {
								x: ele.position("x"),
								y: ele.position("y")
							};
						}
					});
					afterReposition();
					frameId = requestAnimationFrame(iterateAnimated);
				};
				layout.addListener("layoutstarted", function() {
					if (self.options.animate === "during") frameId = requestAnimationFrame(iterateAnimated);
				});
				layout.runLayout();
				if (this.options.animate !== "during") {
					self.options.eles.nodes().not(":parent").layoutPositions(self, self.options, getPositions);
					ready = false;
				}
				return this;
			};
			_CoSELayout.prototype.getTopMostNodes = function(nodes) {
				var nodesMap = {};
				for (var i = 0; i < nodes.length; i++) nodesMap[nodes[i].id()] = true;
				return nodes.filter(function(ele, i) {
					if (typeof ele === "number") ele = i;
					var parent = ele.parent()[0];
					while (parent != null) {
						if (nodesMap[parent.id()]) return false;
						parent = parent.parent()[0];
					}
					return true;
				});
			};
			_CoSELayout.prototype.processChildrenList = function(parent, children, layout) {
				var size = children.length;
				for (var i = 0; i < size; i++) {
					var theChild = children[i];
					var children_of_children = theChild.children();
					var theNode;
					var dimensions = theChild.layoutDimensions({ nodeDimensionsIncludeLabels: this.options.nodeDimensionsIncludeLabels });
					if (theChild.outerWidth() != null && theChild.outerHeight() != null) theNode = parent.add(new CoSENode(layout.graphManager, new PointD(theChild.position("x") - dimensions.w / 2, theChild.position("y") - dimensions.h / 2), new DimensionD(parseFloat(dimensions.w), parseFloat(dimensions.h))));
					else theNode = parent.add(new CoSENode(this.graphManager));
					theNode.id = theChild.data("id");
					theNode.paddingLeft = parseInt(theChild.css("padding"));
					theNode.paddingTop = parseInt(theChild.css("padding"));
					theNode.paddingRight = parseInt(theChild.css("padding"));
					theNode.paddingBottom = parseInt(theChild.css("padding"));
					if (this.options.nodeDimensionsIncludeLabels) {
						if (theChild.isParent()) {
							var labelWidth = theChild.boundingBox({
								includeLabels: true,
								includeNodes: false
							}).w;
							var labelHeight = theChild.boundingBox({
								includeLabels: true,
								includeNodes: false
							}).h;
							var labelPos = theChild.css("text-halign");
							theNode.labelWidth = labelWidth;
							theNode.labelHeight = labelHeight;
							theNode.labelPos = labelPos;
						}
					}
					this.idToLNode[theChild.data("id")] = theNode;
					if (isNaN(theNode.rect.x)) theNode.rect.x = 0;
					if (isNaN(theNode.rect.y)) theNode.rect.y = 0;
					if (children_of_children != null && children_of_children.length > 0) {
						var theNewGraph = layout.getGraphManager().add(layout.newGraph(), theNode);
						this.processChildrenList(theNewGraph, children_of_children, layout);
					}
				}
			};
			/**
			* @brief : called on continuous layouts to stop them before they finish
			*/
			_CoSELayout.prototype.stop = function() {
				this.stopped = true;
				return this;
			};
			var register = function register(cytoscape) {
				cytoscape("layout", "cose-bilkent", _CoSELayout);
			};
			if (typeof cytoscape !== "undefined") register(cytoscape);
			module$4.exports = register;
		})]);
	});
}));
export { require_cytoscape_cose_bilkent as t };
