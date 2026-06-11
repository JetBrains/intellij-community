import { t as __commonJSMin } from "./rolldown-runtime.js";
//#region node_modules/cytoscape-fcose/node_modules/cose-base/node_modules/layout-base/layout-base.js
var require_layout_base$1 = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	(function webpackUniversalModuleDefinition(root, factory) {
		if (typeof exports === "object" && typeof module === "object") module.exports = factory();
		else if (typeof define === "function" && define.amd) define([], factory);
		else if (typeof exports === "object") exports["layoutBase"] = factory();
		else root["layoutBase"] = factory();
	})(exports, function() {
		return (function(modules) {
			var installedModules = {};
			function __webpack_require__(moduleId) {
				if (installedModules[moduleId]) return installedModules[moduleId].exports;
				var module$51 = installedModules[moduleId] = {
					i: moduleId,
					l: false,
					exports: {}
				};
				modules[moduleId].call(module$51.exports, module$51, module$51.exports, __webpack_require__);
				module$51.l = true;
				return module$51.exports;
			}
			__webpack_require__.m = modules;
			__webpack_require__.c = installedModules;
			__webpack_require__.i = function(value) {
				return value;
			};
			__webpack_require__.d = function(exports$39, name, getter) {
				if (!__webpack_require__.o(exports$39, name)) Object.defineProperty(exports$39, name, {
					configurable: false,
					enumerable: true,
					get: getter
				});
			};
			__webpack_require__.n = function(module$52) {
				var getter = module$52 && module$52.__esModule ? function getDefault() {
					return module$52["default"];
				} : function getModuleExports() {
					return module$52;
				};
				__webpack_require__.d(getter, "a", getter);
				return getter;
			};
			__webpack_require__.o = function(object, property) {
				return Object.prototype.hasOwnProperty.call(object, property);
			};
			__webpack_require__.p = "";
			return __webpack_require__(__webpack_require__.s = 28);
		})([
			(function(module$53, exports$40, __webpack_require__) {
				"use strict";
				function LayoutConstants() {}
				/**
				* Layout Quality: 0:draft, 1:default, 2:proof
				*/
				LayoutConstants.QUALITY = 1;
				/**
				* Default parameters
				*/
				LayoutConstants.DEFAULT_CREATE_BENDS_AS_NEEDED = false;
				LayoutConstants.DEFAULT_INCREMENTAL = false;
				LayoutConstants.DEFAULT_ANIMATION_ON_LAYOUT = true;
				LayoutConstants.DEFAULT_ANIMATION_DURING_LAYOUT = false;
				LayoutConstants.DEFAULT_ANIMATION_PERIOD = 50;
				LayoutConstants.DEFAULT_UNIFORM_LEAF_NODE_SIZES = false;
				LayoutConstants.DEFAULT_GRAPH_MARGIN = 15;
				LayoutConstants.NODE_DIMENSIONS_INCLUDE_LABELS = false;
				LayoutConstants.SIMPLE_NODE_SIZE = 40;
				LayoutConstants.SIMPLE_NODE_HALF_SIZE = LayoutConstants.SIMPLE_NODE_SIZE / 2;
				LayoutConstants.EMPTY_COMPOUND_NODE_SIZE = 40;
				LayoutConstants.MIN_EDGE_LENGTH = 1;
				LayoutConstants.WORLD_BOUNDARY = 1e6;
				LayoutConstants.INITIAL_WORLD_BOUNDARY = LayoutConstants.WORLD_BOUNDARY / 1e3;
				LayoutConstants.WORLD_CENTER_X = 1200;
				LayoutConstants.WORLD_CENTER_Y = 900;
				module$53.exports = LayoutConstants;
			}),
			(function(module$54, exports$41, __webpack_require__) {
				"use strict";
				var LGraphObject = __webpack_require__(2);
				var IGeometry = __webpack_require__(8);
				var IMath = __webpack_require__(9);
				function LEdge(source, target, vEdge) {
					LGraphObject.call(this, vEdge);
					this.isOverlapingSourceAndTarget = false;
					this.vGraphObject = vEdge;
					this.bendpoints = [];
					this.source = source;
					this.target = target;
				}
				LEdge.prototype = Object.create(LGraphObject.prototype);
				for (var prop in LGraphObject) LEdge[prop] = LGraphObject[prop];
				LEdge.prototype.getSource = function() {
					return this.source;
				};
				LEdge.prototype.getTarget = function() {
					return this.target;
				};
				LEdge.prototype.isInterGraph = function() {
					return this.isInterGraph;
				};
				LEdge.prototype.getLength = function() {
					return this.length;
				};
				LEdge.prototype.isOverlapingSourceAndTarget = function() {
					return this.isOverlapingSourceAndTarget;
				};
				LEdge.prototype.getBendpoints = function() {
					return this.bendpoints;
				};
				LEdge.prototype.getLca = function() {
					return this.lca;
				};
				LEdge.prototype.getSourceInLca = function() {
					return this.sourceInLca;
				};
				LEdge.prototype.getTargetInLca = function() {
					return this.targetInLca;
				};
				LEdge.prototype.getOtherEnd = function(node) {
					if (this.source === node) return this.target;
					else if (this.target === node) return this.source;
					else throw "Node is not incident with this edge";
				};
				LEdge.prototype.getOtherEndInGraph = function(node, graph) {
					var otherEnd = this.getOtherEnd(node);
					var root = graph.getGraphManager().getRoot();
					while (true) {
						if (otherEnd.getOwner() == graph) return otherEnd;
						if (otherEnd.getOwner() == root) break;
						otherEnd = otherEnd.getOwner().getParent();
					}
					return null;
				};
				LEdge.prototype.updateLength = function() {
					var clipPointCoordinates = new Array(4);
					this.isOverlapingSourceAndTarget = IGeometry.getIntersection(this.target.getRect(), this.source.getRect(), clipPointCoordinates);
					if (!this.isOverlapingSourceAndTarget) {
						this.lengthX = clipPointCoordinates[0] - clipPointCoordinates[2];
						this.lengthY = clipPointCoordinates[1] - clipPointCoordinates[3];
						if (Math.abs(this.lengthX) < 1) this.lengthX = IMath.sign(this.lengthX);
						if (Math.abs(this.lengthY) < 1) this.lengthY = IMath.sign(this.lengthY);
						this.length = Math.sqrt(this.lengthX * this.lengthX + this.lengthY * this.lengthY);
					}
				};
				LEdge.prototype.updateLengthSimple = function() {
					this.lengthX = this.target.getCenterX() - this.source.getCenterX();
					this.lengthY = this.target.getCenterY() - this.source.getCenterY();
					if (Math.abs(this.lengthX) < 1) this.lengthX = IMath.sign(this.lengthX);
					if (Math.abs(this.lengthY) < 1) this.lengthY = IMath.sign(this.lengthY);
					this.length = Math.sqrt(this.lengthX * this.lengthX + this.lengthY * this.lengthY);
				};
				module$54.exports = LEdge;
			}),
			(function(module$55, exports$42, __webpack_require__) {
				"use strict";
				function LGraphObject(vGraphObject) {
					this.vGraphObject = vGraphObject;
				}
				module$55.exports = LGraphObject;
			}),
			(function(module$56, exports$43, __webpack_require__) {
				"use strict";
				var LGraphObject = __webpack_require__(2);
				var Integer = __webpack_require__(10);
				var RectangleD = __webpack_require__(13);
				var LayoutConstants = __webpack_require__(0);
				var RandomSeed = __webpack_require__(16);
				var PointD = __webpack_require__(5);
				function LNode(gm, loc, size, vNode) {
					if (size == null && vNode == null) vNode = loc;
					LGraphObject.call(this, vNode);
					if (gm.graphManager != null) gm = gm.graphManager;
					this.estimatedSize = Integer.MIN_VALUE;
					this.inclusionTreeDepth = Integer.MAX_VALUE;
					this.vGraphObject = vNode;
					this.edges = [];
					this.graphManager = gm;
					if (size != null && loc != null) this.rect = new RectangleD(loc.x, loc.y, size.width, size.height);
					else this.rect = new RectangleD();
				}
				LNode.prototype = Object.create(LGraphObject.prototype);
				for (var prop in LGraphObject) LNode[prop] = LGraphObject[prop];
				LNode.prototype.getEdges = function() {
					return this.edges;
				};
				LNode.prototype.getChild = function() {
					return this.child;
				};
				LNode.prototype.getOwner = function() {
					return this.owner;
				};
				LNode.prototype.getWidth = function() {
					return this.rect.width;
				};
				LNode.prototype.setWidth = function(width) {
					this.rect.width = width;
				};
				LNode.prototype.getHeight = function() {
					return this.rect.height;
				};
				LNode.prototype.setHeight = function(height) {
					this.rect.height = height;
				};
				LNode.prototype.getCenterX = function() {
					return this.rect.x + this.rect.width / 2;
				};
				LNode.prototype.getCenterY = function() {
					return this.rect.y + this.rect.height / 2;
				};
				LNode.prototype.getCenter = function() {
					return new PointD(this.rect.x + this.rect.width / 2, this.rect.y + this.rect.height / 2);
				};
				LNode.prototype.getLocation = function() {
					return new PointD(this.rect.x, this.rect.y);
				};
				LNode.prototype.getRect = function() {
					return this.rect;
				};
				LNode.prototype.getDiagonal = function() {
					return Math.sqrt(this.rect.width * this.rect.width + this.rect.height * this.rect.height);
				};
				/**
				* This method returns half the diagonal length of this node.
				*/
				LNode.prototype.getHalfTheDiagonal = function() {
					return Math.sqrt(this.rect.height * this.rect.height + this.rect.width * this.rect.width) / 2;
				};
				LNode.prototype.setRect = function(upperLeft, dimension) {
					this.rect.x = upperLeft.x;
					this.rect.y = upperLeft.y;
					this.rect.width = dimension.width;
					this.rect.height = dimension.height;
				};
				LNode.prototype.setCenter = function(cx, cy) {
					this.rect.x = cx - this.rect.width / 2;
					this.rect.y = cy - this.rect.height / 2;
				};
				LNode.prototype.setLocation = function(x, y) {
					this.rect.x = x;
					this.rect.y = y;
				};
				LNode.prototype.moveBy = function(dx, dy) {
					this.rect.x += dx;
					this.rect.y += dy;
				};
				LNode.prototype.getEdgeListToNode = function(to) {
					var edgeList = [];
					var self = this;
					self.edges.forEach(function(edge) {
						if (edge.target == to) {
							if (edge.source != self) throw "Incorrect edge source!";
							edgeList.push(edge);
						}
					});
					return edgeList;
				};
				LNode.prototype.getEdgesBetween = function(other) {
					var edgeList = [];
					var self = this;
					self.edges.forEach(function(edge) {
						if (!(edge.source == self || edge.target == self)) throw "Incorrect edge source and/or target";
						if (edge.target == other || edge.source == other) edgeList.push(edge);
					});
					return edgeList;
				};
				LNode.prototype.getNeighborsList = function() {
					var neighbors = /* @__PURE__ */ new Set();
					var self = this;
					self.edges.forEach(function(edge) {
						if (edge.source == self) neighbors.add(edge.target);
						else {
							if (edge.target != self) throw "Incorrect incidency!";
							neighbors.add(edge.source);
						}
					});
					return neighbors;
				};
				LNode.prototype.withChildren = function() {
					var withNeighborsList = /* @__PURE__ */ new Set();
					var childNode;
					var children;
					withNeighborsList.add(this);
					if (this.child != null) {
						var nodes = this.child.getNodes();
						for (var i = 0; i < nodes.length; i++) {
							childNode = nodes[i];
							children = childNode.withChildren();
							children.forEach(function(node) {
								withNeighborsList.add(node);
							});
						}
					}
					return withNeighborsList;
				};
				LNode.prototype.getNoOfChildren = function() {
					var noOfChildren = 0;
					var childNode;
					if (this.child == null) noOfChildren = 1;
					else {
						var nodes = this.child.getNodes();
						for (var i = 0; i < nodes.length; i++) {
							childNode = nodes[i];
							noOfChildren += childNode.getNoOfChildren();
						}
					}
					if (noOfChildren == 0) noOfChildren = 1;
					return noOfChildren;
				};
				LNode.prototype.getEstimatedSize = function() {
					if (this.estimatedSize == Integer.MIN_VALUE) throw "assert failed";
					return this.estimatedSize;
				};
				LNode.prototype.calcEstimatedSize = function() {
					if (this.child == null) return this.estimatedSize = (this.rect.width + this.rect.height) / 2;
					else {
						this.estimatedSize = this.child.calcEstimatedSize();
						this.rect.width = this.estimatedSize;
						this.rect.height = this.estimatedSize;
						return this.estimatedSize;
					}
				};
				LNode.prototype.scatter = function() {
					var randomCenterX;
					var randomCenterY;
					var minX = -LayoutConstants.INITIAL_WORLD_BOUNDARY;
					var maxX = LayoutConstants.INITIAL_WORLD_BOUNDARY;
					randomCenterX = LayoutConstants.WORLD_CENTER_X + RandomSeed.nextDouble() * (maxX - minX) + minX;
					var minY = -LayoutConstants.INITIAL_WORLD_BOUNDARY;
					var maxY = LayoutConstants.INITIAL_WORLD_BOUNDARY;
					randomCenterY = LayoutConstants.WORLD_CENTER_Y + RandomSeed.nextDouble() * (maxY - minY) + minY;
					this.rect.x = randomCenterX;
					this.rect.y = randomCenterY;
				};
				LNode.prototype.updateBounds = function() {
					if (this.getChild() == null) throw "assert failed";
					if (this.getChild().getNodes().length != 0) {
						var childGraph = this.getChild();
						childGraph.updateBounds(true);
						this.rect.x = childGraph.getLeft();
						this.rect.y = childGraph.getTop();
						this.setWidth(childGraph.getRight() - childGraph.getLeft());
						this.setHeight(childGraph.getBottom() - childGraph.getTop());
						if (LayoutConstants.NODE_DIMENSIONS_INCLUDE_LABELS) {
							var width = childGraph.getRight() - childGraph.getLeft();
							var height = childGraph.getBottom() - childGraph.getTop();
							if (this.labelWidth) {
								if (this.labelPosHorizontal == "left") {
									this.rect.x -= this.labelWidth;
									this.setWidth(width + this.labelWidth);
								} else if (this.labelPosHorizontal == "center" && this.labelWidth > width) {
									this.rect.x -= (this.labelWidth - width) / 2;
									this.setWidth(this.labelWidth);
								} else if (this.labelPosHorizontal == "right") this.setWidth(width + this.labelWidth);
							}
							if (this.labelHeight) {
								if (this.labelPosVertical == "top") {
									this.rect.y -= this.labelHeight;
									this.setHeight(height + this.labelHeight);
								} else if (this.labelPosVertical == "center" && this.labelHeight > height) {
									this.rect.y -= (this.labelHeight - height) / 2;
									this.setHeight(this.labelHeight);
								} else if (this.labelPosVertical == "bottom") this.setHeight(height + this.labelHeight);
							}
						}
					}
				};
				LNode.prototype.getInclusionTreeDepth = function() {
					if (this.inclusionTreeDepth == Integer.MAX_VALUE) throw "assert failed";
					return this.inclusionTreeDepth;
				};
				LNode.prototype.transform = function(trans) {
					var left = this.rect.x;
					if (left > LayoutConstants.WORLD_BOUNDARY) left = LayoutConstants.WORLD_BOUNDARY;
					else if (left < -LayoutConstants.WORLD_BOUNDARY) left = -LayoutConstants.WORLD_BOUNDARY;
					var top = this.rect.y;
					if (top > LayoutConstants.WORLD_BOUNDARY) top = LayoutConstants.WORLD_BOUNDARY;
					else if (top < -LayoutConstants.WORLD_BOUNDARY) top = -LayoutConstants.WORLD_BOUNDARY;
					var leftTop = new PointD(left, top);
					var vLeftTop = trans.inverseTransformPoint(leftTop);
					this.setLocation(vLeftTop.x, vLeftTop.y);
				};
				LNode.prototype.getLeft = function() {
					return this.rect.x;
				};
				LNode.prototype.getRight = function() {
					return this.rect.x + this.rect.width;
				};
				LNode.prototype.getTop = function() {
					return this.rect.y;
				};
				LNode.prototype.getBottom = function() {
					return this.rect.y + this.rect.height;
				};
				LNode.prototype.getParent = function() {
					if (this.owner == null) return null;
					return this.owner.getParent();
				};
				module$56.exports = LNode;
			}),
			(function(module$57, exports$44, __webpack_require__) {
				"use strict";
				var LayoutConstants = __webpack_require__(0);
				function FDLayoutConstants() {}
				for (var prop in LayoutConstants) FDLayoutConstants[prop] = LayoutConstants[prop];
				FDLayoutConstants.MAX_ITERATIONS = 2500;
				FDLayoutConstants.DEFAULT_EDGE_LENGTH = 50;
				FDLayoutConstants.DEFAULT_SPRING_STRENGTH = .45;
				FDLayoutConstants.DEFAULT_REPULSION_STRENGTH = 4500;
				FDLayoutConstants.DEFAULT_GRAVITY_STRENGTH = .4;
				FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_STRENGTH = 1;
				FDLayoutConstants.DEFAULT_GRAVITY_RANGE_FACTOR = 3.8;
				FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_RANGE_FACTOR = 1.5;
				FDLayoutConstants.DEFAULT_USE_SMART_IDEAL_EDGE_LENGTH_CALCULATION = true;
				FDLayoutConstants.DEFAULT_USE_SMART_REPULSION_RANGE_CALCULATION = true;
				FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL = .3;
				FDLayoutConstants.COOLING_ADAPTATION_FACTOR = .33;
				FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT = 1e3;
				FDLayoutConstants.ADAPTATION_UPPER_NODE_LIMIT = 5e3;
				FDLayoutConstants.MAX_NODE_DISPLACEMENT_INCREMENTAL = 100;
				FDLayoutConstants.MAX_NODE_DISPLACEMENT = FDLayoutConstants.MAX_NODE_DISPLACEMENT_INCREMENTAL * 3;
				FDLayoutConstants.MIN_REPULSION_DIST = FDLayoutConstants.DEFAULT_EDGE_LENGTH / 10;
				FDLayoutConstants.CONVERGENCE_CHECK_PERIOD = 100;
				FDLayoutConstants.PER_LEVEL_IDEAL_EDGE_LENGTH_FACTOR = .1;
				FDLayoutConstants.MIN_EDGE_LENGTH = 1;
				FDLayoutConstants.GRID_CALCULATION_CHECK_PERIOD = 10;
				module$57.exports = FDLayoutConstants;
			}),
			(function(module$58, exports$45, __webpack_require__) {
				"use strict";
				function PointD(x, y) {
					if (x == null && y == null) {
						this.x = 0;
						this.y = 0;
					} else {
						this.x = x;
						this.y = y;
					}
				}
				PointD.prototype.getX = function() {
					return this.x;
				};
				PointD.prototype.getY = function() {
					return this.y;
				};
				PointD.prototype.setX = function(x) {
					this.x = x;
				};
				PointD.prototype.setY = function(y) {
					this.y = y;
				};
				PointD.prototype.getDifference = function(pt) {
					return new DimensionD(this.x - pt.x, this.y - pt.y);
				};
				PointD.prototype.getCopy = function() {
					return new PointD(this.x, this.y);
				};
				PointD.prototype.translate = function(dim) {
					this.x += dim.width;
					this.y += dim.height;
					return this;
				};
				module$58.exports = PointD;
			}),
			(function(module$59, exports$46, __webpack_require__) {
				"use strict";
				var LGraphObject = __webpack_require__(2);
				var Integer = __webpack_require__(10);
				var LayoutConstants = __webpack_require__(0);
				var LGraphManager = __webpack_require__(7);
				var LNode = __webpack_require__(3);
				var LEdge = __webpack_require__(1);
				var RectangleD = __webpack_require__(13);
				var Point = __webpack_require__(12);
				var LinkedList = __webpack_require__(11);
				function LGraph(parent, obj2, vGraph) {
					LGraphObject.call(this, vGraph);
					this.estimatedSize = Integer.MIN_VALUE;
					this.margin = LayoutConstants.DEFAULT_GRAPH_MARGIN;
					this.edges = [];
					this.nodes = [];
					this.isConnected = false;
					this.parent = parent;
					if (obj2 != null && obj2 instanceof LGraphManager) this.graphManager = obj2;
					else if (obj2 != null && obj2 instanceof Layout) this.graphManager = obj2.graphManager;
				}
				LGraph.prototype = Object.create(LGraphObject.prototype);
				for (var prop in LGraphObject) LGraph[prop] = LGraphObject[prop];
				LGraph.prototype.getNodes = function() {
					return this.nodes;
				};
				LGraph.prototype.getEdges = function() {
					return this.edges;
				};
				LGraph.prototype.getGraphManager = function() {
					return this.graphManager;
				};
				LGraph.prototype.getParent = function() {
					return this.parent;
				};
				LGraph.prototype.getLeft = function() {
					return this.left;
				};
				LGraph.prototype.getRight = function() {
					return this.right;
				};
				LGraph.prototype.getTop = function() {
					return this.top;
				};
				LGraph.prototype.getBottom = function() {
					return this.bottom;
				};
				LGraph.prototype.isConnected = function() {
					return this.isConnected;
				};
				LGraph.prototype.add = function(obj1, sourceNode, targetNode) {
					if (sourceNode == null && targetNode == null) {
						var newNode = obj1;
						if (this.graphManager == null) throw "Graph has no graph mgr!";
						if (this.getNodes().indexOf(newNode) > -1) throw "Node already in graph!";
						newNode.owner = this;
						this.getNodes().push(newNode);
						return newNode;
					} else {
						var newEdge = obj1;
						if (!(this.getNodes().indexOf(sourceNode) > -1 && this.getNodes().indexOf(targetNode) > -1)) throw "Source or target not in graph!";
						if (!(sourceNode.owner == targetNode.owner && sourceNode.owner == this)) throw "Both owners must be this graph!";
						if (sourceNode.owner != targetNode.owner) return null;
						newEdge.source = sourceNode;
						newEdge.target = targetNode;
						newEdge.isInterGraph = false;
						this.getEdges().push(newEdge);
						sourceNode.edges.push(newEdge);
						if (targetNode != sourceNode) targetNode.edges.push(newEdge);
						return newEdge;
					}
				};
				LGraph.prototype.remove = function(obj) {
					var node = obj;
					if (obj instanceof LNode) {
						if (node == null) throw "Node is null!";
						if (!(node.owner != null && node.owner == this)) throw "Owner graph is invalid!";
						if (this.graphManager == null) throw "Owner graph manager is invalid!";
						var edgesToBeRemoved = node.edges.slice();
						var edge;
						var s = edgesToBeRemoved.length;
						for (var i = 0; i < s; i++) {
							edge = edgesToBeRemoved[i];
							if (edge.isInterGraph) this.graphManager.remove(edge);
							else edge.source.owner.remove(edge);
						}
						var index = this.nodes.indexOf(node);
						if (index == -1) throw "Node not in owner node list!";
						this.nodes.splice(index, 1);
					} else if (obj instanceof LEdge) {
						var edge = obj;
						if (edge == null) throw "Edge is null!";
						if (!(edge.source != null && edge.target != null)) throw "Source and/or target is null!";
						if (!(edge.source.owner != null && edge.target.owner != null && edge.source.owner == this && edge.target.owner == this)) throw "Source and/or target owner is invalid!";
						var sourceIndex = edge.source.edges.indexOf(edge);
						var targetIndex = edge.target.edges.indexOf(edge);
						if (!(sourceIndex > -1 && targetIndex > -1)) throw "Source and/or target doesn't know this edge!";
						edge.source.edges.splice(sourceIndex, 1);
						if (edge.target != edge.source) edge.target.edges.splice(targetIndex, 1);
						var index = edge.source.owner.getEdges().indexOf(edge);
						if (index == -1) throw "Not in owner's edge list!";
						edge.source.owner.getEdges().splice(index, 1);
					}
				};
				LGraph.prototype.updateLeftTop = function() {
					var top = Integer.MAX_VALUE;
					var left = Integer.MAX_VALUE;
					var nodeTop;
					var nodeLeft;
					var margin;
					var nodes = this.getNodes();
					var s = nodes.length;
					for (var i = 0; i < s; i++) {
						var lNode = nodes[i];
						nodeTop = lNode.getTop();
						nodeLeft = lNode.getLeft();
						if (top > nodeTop) top = nodeTop;
						if (left > nodeLeft) left = nodeLeft;
					}
					if (top == Integer.MAX_VALUE) return null;
					if (nodes[0].getParent().paddingLeft != void 0) margin = nodes[0].getParent().paddingLeft;
					else margin = this.margin;
					this.left = left - margin;
					this.top = top - margin;
					return new Point(this.left, this.top);
				};
				LGraph.prototype.updateBounds = function(recursive) {
					var left = Integer.MAX_VALUE;
					var right = -Integer.MAX_VALUE;
					var top = Integer.MAX_VALUE;
					var bottom = -Integer.MAX_VALUE;
					var nodeLeft;
					var nodeRight;
					var nodeTop;
					var nodeBottom;
					var margin;
					var nodes = this.nodes;
					var s = nodes.length;
					for (var i = 0; i < s; i++) {
						var lNode = nodes[i];
						if (recursive && lNode.child != null) lNode.updateBounds();
						nodeLeft = lNode.getLeft();
						nodeRight = lNode.getRight();
						nodeTop = lNode.getTop();
						nodeBottom = lNode.getBottom();
						if (left > nodeLeft) left = nodeLeft;
						if (right < nodeRight) right = nodeRight;
						if (top > nodeTop) top = nodeTop;
						if (bottom < nodeBottom) bottom = nodeBottom;
					}
					var boundingRect = new RectangleD(left, top, right - left, bottom - top);
					if (left == Integer.MAX_VALUE) {
						this.left = this.parent.getLeft();
						this.right = this.parent.getRight();
						this.top = this.parent.getTop();
						this.bottom = this.parent.getBottom();
					}
					if (nodes[0].getParent().paddingLeft != void 0) margin = nodes[0].getParent().paddingLeft;
					else margin = this.margin;
					this.left = boundingRect.x - margin;
					this.right = boundingRect.x + boundingRect.width + margin;
					this.top = boundingRect.y - margin;
					this.bottom = boundingRect.y + boundingRect.height + margin;
				};
				LGraph.calculateBounds = function(nodes) {
					var left = Integer.MAX_VALUE;
					var right = -Integer.MAX_VALUE;
					var top = Integer.MAX_VALUE;
					var bottom = -Integer.MAX_VALUE;
					var nodeLeft;
					var nodeRight;
					var nodeTop;
					var nodeBottom;
					var s = nodes.length;
					for (var i = 0; i < s; i++) {
						var lNode = nodes[i];
						nodeLeft = lNode.getLeft();
						nodeRight = lNode.getRight();
						nodeTop = lNode.getTop();
						nodeBottom = lNode.getBottom();
						if (left > nodeLeft) left = nodeLeft;
						if (right < nodeRight) right = nodeRight;
						if (top > nodeTop) top = nodeTop;
						if (bottom < nodeBottom) bottom = nodeBottom;
					}
					return new RectangleD(left, top, right - left, bottom - top);
				};
				LGraph.prototype.getInclusionTreeDepth = function() {
					if (this == this.graphManager.getRoot()) return 1;
					else return this.parent.getInclusionTreeDepth();
				};
				LGraph.prototype.getEstimatedSize = function() {
					if (this.estimatedSize == Integer.MIN_VALUE) throw "assert failed";
					return this.estimatedSize;
				};
				LGraph.prototype.calcEstimatedSize = function() {
					var size = 0;
					var nodes = this.nodes;
					var s = nodes.length;
					for (var i = 0; i < s; i++) {
						var lNode = nodes[i];
						size += lNode.calcEstimatedSize();
					}
					if (size == 0) this.estimatedSize = LayoutConstants.EMPTY_COMPOUND_NODE_SIZE;
					else this.estimatedSize = size / Math.sqrt(this.nodes.length);
					return this.estimatedSize;
				};
				LGraph.prototype.updateConnected = function() {
					var self = this;
					if (this.nodes.length == 0) {
						this.isConnected = true;
						return;
					}
					var queue = new LinkedList();
					var visited = /* @__PURE__ */ new Set();
					var currentNode = this.nodes[0];
					var neighborEdges;
					var currentNeighbor;
					currentNode.withChildren().forEach(function(node) {
						queue.push(node);
						visited.add(node);
					});
					while (queue.length !== 0) {
						currentNode = queue.shift();
						neighborEdges = currentNode.getEdges();
						var size = neighborEdges.length;
						for (var i = 0; i < size; i++) {
							currentNeighbor = neighborEdges[i].getOtherEndInGraph(currentNode, this);
							if (currentNeighbor != null && !visited.has(currentNeighbor)) currentNeighbor.withChildren().forEach(function(node) {
								queue.push(node);
								visited.add(node);
							});
						}
					}
					this.isConnected = false;
					if (visited.size >= this.nodes.length) {
						var noOfVisitedInThisGraph = 0;
						visited.forEach(function(visitedNode) {
							if (visitedNode.owner == self) noOfVisitedInThisGraph++;
						});
						if (noOfVisitedInThisGraph == this.nodes.length) this.isConnected = true;
					}
				};
				module$59.exports = LGraph;
			}),
			(function(module$60, exports$47, __webpack_require__) {
				"use strict";
				var LGraph;
				var LEdge = __webpack_require__(1);
				function LGraphManager(layout) {
					LGraph = __webpack_require__(6);
					this.layout = layout;
					this.graphs = [];
					this.edges = [];
				}
				LGraphManager.prototype.addRoot = function() {
					var ngraph = this.layout.newGraph();
					var nnode = this.layout.newNode(null);
					var root = this.add(ngraph, nnode);
					this.setRootGraph(root);
					return this.rootGraph;
				};
				LGraphManager.prototype.add = function(newGraph, parentNode, newEdge, sourceNode, targetNode) {
					if (newEdge == null && sourceNode == null && targetNode == null) {
						if (newGraph == null) throw "Graph is null!";
						if (parentNode == null) throw "Parent node is null!";
						if (this.graphs.indexOf(newGraph) > -1) throw "Graph already in this graph mgr!";
						this.graphs.push(newGraph);
						if (newGraph.parent != null) throw "Already has a parent!";
						if (parentNode.child != null) throw "Already has a child!";
						newGraph.parent = parentNode;
						parentNode.child = newGraph;
						return newGraph;
					} else {
						targetNode = newEdge;
						sourceNode = parentNode;
						newEdge = newGraph;
						var sourceGraph = sourceNode.getOwner();
						var targetGraph = targetNode.getOwner();
						if (!(sourceGraph != null && sourceGraph.getGraphManager() == this)) throw "Source not in this graph mgr!";
						if (!(targetGraph != null && targetGraph.getGraphManager() == this)) throw "Target not in this graph mgr!";
						if (sourceGraph == targetGraph) {
							newEdge.isInterGraph = false;
							return sourceGraph.add(newEdge, sourceNode, targetNode);
						} else {
							newEdge.isInterGraph = true;
							newEdge.source = sourceNode;
							newEdge.target = targetNode;
							if (this.edges.indexOf(newEdge) > -1) throw "Edge already in inter-graph edge list!";
							this.edges.push(newEdge);
							if (!(newEdge.source != null && newEdge.target != null)) throw "Edge source and/or target is null!";
							if (!(newEdge.source.edges.indexOf(newEdge) == -1 && newEdge.target.edges.indexOf(newEdge) == -1)) throw "Edge already in source and/or target incidency list!";
							newEdge.source.edges.push(newEdge);
							newEdge.target.edges.push(newEdge);
							return newEdge;
						}
					}
				};
				LGraphManager.prototype.remove = function(lObj) {
					if (lObj instanceof LGraph) {
						var graph = lObj;
						if (graph.getGraphManager() != this) throw "Graph not in this graph mgr";
						if (!(graph == this.rootGraph || graph.parent != null && graph.parent.graphManager == this)) throw "Invalid parent node!";
						var edgesToBeRemoved = [];
						edgesToBeRemoved = edgesToBeRemoved.concat(graph.getEdges());
						var edge;
						var s = edgesToBeRemoved.length;
						for (var i = 0; i < s; i++) {
							edge = edgesToBeRemoved[i];
							graph.remove(edge);
						}
						var nodesToBeRemoved = [];
						nodesToBeRemoved = nodesToBeRemoved.concat(graph.getNodes());
						var node;
						s = nodesToBeRemoved.length;
						for (var i = 0; i < s; i++) {
							node = nodesToBeRemoved[i];
							graph.remove(node);
						}
						if (graph == this.rootGraph) this.setRootGraph(null);
						var index = this.graphs.indexOf(graph);
						this.graphs.splice(index, 1);
						graph.parent = null;
					} else if (lObj instanceof LEdge) {
						edge = lObj;
						if (edge == null) throw "Edge is null!";
						if (!edge.isInterGraph) throw "Not an inter-graph edge!";
						if (!(edge.source != null && edge.target != null)) throw "Source and/or target is null!";
						if (!(edge.source.edges.indexOf(edge) != -1 && edge.target.edges.indexOf(edge) != -1)) throw "Source and/or target doesn't know this edge!";
						var index = edge.source.edges.indexOf(edge);
						edge.source.edges.splice(index, 1);
						index = edge.target.edges.indexOf(edge);
						edge.target.edges.splice(index, 1);
						if (!(edge.source.owner != null && edge.source.owner.getGraphManager() != null)) throw "Edge owner graph or owner graph manager is null!";
						if (edge.source.owner.getGraphManager().edges.indexOf(edge) == -1) throw "Not in owner graph manager's edge list!";
						var index = edge.source.owner.getGraphManager().edges.indexOf(edge);
						edge.source.owner.getGraphManager().edges.splice(index, 1);
					}
				};
				LGraphManager.prototype.updateBounds = function() {
					this.rootGraph.updateBounds(true);
				};
				LGraphManager.prototype.getGraphs = function() {
					return this.graphs;
				};
				LGraphManager.prototype.getAllNodes = function() {
					if (this.allNodes == null) {
						var nodeList = [];
						var graphs = this.getGraphs();
						var s = graphs.length;
						for (var i = 0; i < s; i++) nodeList = nodeList.concat(graphs[i].getNodes());
						this.allNodes = nodeList;
					}
					return this.allNodes;
				};
				LGraphManager.prototype.resetAllNodes = function() {
					this.allNodes = null;
				};
				LGraphManager.prototype.resetAllEdges = function() {
					this.allEdges = null;
				};
				LGraphManager.prototype.resetAllNodesToApplyGravitation = function() {
					this.allNodesToApplyGravitation = null;
				};
				LGraphManager.prototype.getAllEdges = function() {
					if (this.allEdges == null) {
						var edgeList = [];
						var graphs = this.getGraphs();
						graphs.length;
						for (var i = 0; i < graphs.length; i++) edgeList = edgeList.concat(graphs[i].getEdges());
						edgeList = edgeList.concat(this.edges);
						this.allEdges = edgeList;
					}
					return this.allEdges;
				};
				LGraphManager.prototype.getAllNodesToApplyGravitation = function() {
					return this.allNodesToApplyGravitation;
				};
				LGraphManager.prototype.setAllNodesToApplyGravitation = function(nodeList) {
					if (this.allNodesToApplyGravitation != null) throw "assert failed";
					this.allNodesToApplyGravitation = nodeList;
				};
				LGraphManager.prototype.getRoot = function() {
					return this.rootGraph;
				};
				LGraphManager.prototype.setRootGraph = function(graph) {
					if (graph.getGraphManager() != this) throw "Root not in this graph mgr!";
					this.rootGraph = graph;
					if (graph.parent == null) graph.parent = this.layout.newNode("Root node");
				};
				LGraphManager.prototype.getLayout = function() {
					return this.layout;
				};
				LGraphManager.prototype.isOneAncestorOfOther = function(firstNode, secondNode) {
					if (!(firstNode != null && secondNode != null)) throw "assert failed";
					if (firstNode == secondNode) return true;
					var ownerGraph = firstNode.getOwner();
					var parentNode;
					do {
						parentNode = ownerGraph.getParent();
						if (parentNode == null) break;
						if (parentNode == secondNode) return true;
						ownerGraph = parentNode.getOwner();
						if (ownerGraph == null) break;
					} while (true);
					ownerGraph = secondNode.getOwner();
					do {
						parentNode = ownerGraph.getParent();
						if (parentNode == null) break;
						if (parentNode == firstNode) return true;
						ownerGraph = parentNode.getOwner();
						if (ownerGraph == null) break;
					} while (true);
					return false;
				};
				LGraphManager.prototype.calcLowestCommonAncestors = function() {
					var edge;
					var sourceNode;
					var targetNode;
					var sourceAncestorGraph;
					var targetAncestorGraph;
					var edges = this.getAllEdges();
					var s = edges.length;
					for (var i = 0; i < s; i++) {
						edge = edges[i];
						sourceNode = edge.source;
						targetNode = edge.target;
						edge.lca = null;
						edge.sourceInLca = sourceNode;
						edge.targetInLca = targetNode;
						if (sourceNode == targetNode) {
							edge.lca = sourceNode.getOwner();
							continue;
						}
						sourceAncestorGraph = sourceNode.getOwner();
						while (edge.lca == null) {
							edge.targetInLca = targetNode;
							targetAncestorGraph = targetNode.getOwner();
							while (edge.lca == null) {
								if (targetAncestorGraph == sourceAncestorGraph) {
									edge.lca = targetAncestorGraph;
									break;
								}
								if (targetAncestorGraph == this.rootGraph) break;
								if (edge.lca != null) throw "assert failed";
								edge.targetInLca = targetAncestorGraph.getParent();
								targetAncestorGraph = edge.targetInLca.getOwner();
							}
							if (sourceAncestorGraph == this.rootGraph) break;
							if (edge.lca == null) {
								edge.sourceInLca = sourceAncestorGraph.getParent();
								sourceAncestorGraph = edge.sourceInLca.getOwner();
							}
						}
						if (edge.lca == null) throw "assert failed";
					}
				};
				LGraphManager.prototype.calcLowestCommonAncestor = function(firstNode, secondNode) {
					if (firstNode == secondNode) return firstNode.getOwner();
					var firstOwnerGraph = firstNode.getOwner();
					do {
						if (firstOwnerGraph == null) break;
						var secondOwnerGraph = secondNode.getOwner();
						do {
							if (secondOwnerGraph == null) break;
							if (secondOwnerGraph == firstOwnerGraph) return secondOwnerGraph;
							secondOwnerGraph = secondOwnerGraph.getParent().getOwner();
						} while (true);
						firstOwnerGraph = firstOwnerGraph.getParent().getOwner();
					} while (true);
					return firstOwnerGraph;
				};
				LGraphManager.prototype.calcInclusionTreeDepths = function(graph, depth) {
					if (graph == null && depth == null) {
						graph = this.rootGraph;
						depth = 1;
					}
					var node;
					var nodes = graph.getNodes();
					var s = nodes.length;
					for (var i = 0; i < s; i++) {
						node = nodes[i];
						node.inclusionTreeDepth = depth;
						if (node.child != null) this.calcInclusionTreeDepths(node.child, depth + 1);
					}
				};
				LGraphManager.prototype.includesInvalidEdge = function() {
					var edge;
					var edgesToRemove = [];
					var s = this.edges.length;
					for (var i = 0; i < s; i++) {
						edge = this.edges[i];
						if (this.isOneAncestorOfOther(edge.source, edge.target)) edgesToRemove.push(edge);
					}
					for (var i = 0; i < edgesToRemove.length; i++) this.remove(edgesToRemove[i]);
					return false;
				};
				module$60.exports = LGraphManager;
			}),
			(function(module$61, exports$48, __webpack_require__) {
				"use strict";
				/**
				* This class maintains a list of static geometry related utility methods.
				*
				*
				* Copyright: i-Vis Research Group, Bilkent University, 2007 - present
				*/
				var Point = __webpack_require__(12);
				function IGeometry() {}
				/**
				* This method calculates *half* the amount in x and y directions of the two
				* input rectangles needed to separate them keeping their respective
				* positioning, and returns the result in the input array. An input
				* separation buffer added to the amount in both directions. We assume that
				* the two rectangles do intersect.
				*/
				IGeometry.calcSeparationAmount = function(rectA, rectB, overlapAmount, separationBuffer) {
					if (!rectA.intersects(rectB)) throw "assert failed";
					var directions = new Array(2);
					this.decideDirectionsForOverlappingNodes(rectA, rectB, directions);
					overlapAmount[0] = Math.min(rectA.getRight(), rectB.getRight()) - Math.max(rectA.x, rectB.x);
					overlapAmount[1] = Math.min(rectA.getBottom(), rectB.getBottom()) - Math.max(rectA.y, rectB.y);
					if (rectA.getX() <= rectB.getX() && rectA.getRight() >= rectB.getRight()) overlapAmount[0] += Math.min(rectB.getX() - rectA.getX(), rectA.getRight() - rectB.getRight());
					else if (rectB.getX() <= rectA.getX() && rectB.getRight() >= rectA.getRight()) overlapAmount[0] += Math.min(rectA.getX() - rectB.getX(), rectB.getRight() - rectA.getRight());
					if (rectA.getY() <= rectB.getY() && rectA.getBottom() >= rectB.getBottom()) overlapAmount[1] += Math.min(rectB.getY() - rectA.getY(), rectA.getBottom() - rectB.getBottom());
					else if (rectB.getY() <= rectA.getY() && rectB.getBottom() >= rectA.getBottom()) overlapAmount[1] += Math.min(rectA.getY() - rectB.getY(), rectB.getBottom() - rectA.getBottom());
					var slope = Math.abs((rectB.getCenterY() - rectA.getCenterY()) / (rectB.getCenterX() - rectA.getCenterX()));
					if (rectB.getCenterY() === rectA.getCenterY() && rectB.getCenterX() === rectA.getCenterX()) slope = 1;
					var moveByY = slope * overlapAmount[0];
					var moveByX = overlapAmount[1] / slope;
					if (overlapAmount[0] < moveByX) moveByX = overlapAmount[0];
					else moveByY = overlapAmount[1];
					overlapAmount[0] = -1 * directions[0] * (moveByX / 2 + separationBuffer);
					overlapAmount[1] = -1 * directions[1] * (moveByY / 2 + separationBuffer);
				};
				/**
				* This method decides the separation direction of overlapping nodes
				*
				* if directions[0] = -1, then rectA goes left
				* if directions[0] = 1,  then rectA goes right
				* if directions[1] = -1, then rectA goes up
				* if directions[1] = 1,  then rectA goes down
				*/
				IGeometry.decideDirectionsForOverlappingNodes = function(rectA, rectB, directions) {
					if (rectA.getCenterX() < rectB.getCenterX()) directions[0] = -1;
					else directions[0] = 1;
					if (rectA.getCenterY() < rectB.getCenterY()) directions[1] = -1;
					else directions[1] = 1;
				};
				/**
				* This method calculates the intersection (clipping) points of the two
				* input rectangles with line segment defined by the centers of these two
				* rectangles. The clipping points are saved in the input double array and
				* whether or not the two rectangles overlap is returned.
				*/
				IGeometry.getIntersection2 = function(rectA, rectB, result) {
					var p1x = rectA.getCenterX();
					var p1y = rectA.getCenterY();
					var p2x = rectB.getCenterX();
					var p2y = rectB.getCenterY();
					if (rectA.intersects(rectB)) {
						result[0] = p1x;
						result[1] = p1y;
						result[2] = p2x;
						result[3] = p2y;
						return true;
					}
					var topLeftAx = rectA.getX();
					var topLeftAy = rectA.getY();
					var topRightAx = rectA.getRight();
					var bottomLeftAx = rectA.getX();
					var bottomLeftAy = rectA.getBottom();
					var bottomRightAx = rectA.getRight();
					var halfWidthA = rectA.getWidthHalf();
					var halfHeightA = rectA.getHeightHalf();
					var topLeftBx = rectB.getX();
					var topLeftBy = rectB.getY();
					var topRightBx = rectB.getRight();
					var bottomLeftBx = rectB.getX();
					var bottomLeftBy = rectB.getBottom();
					var bottomRightBx = rectB.getRight();
					var halfWidthB = rectB.getWidthHalf();
					var halfHeightB = rectB.getHeightHalf();
					var clipPointAFound = false;
					var clipPointBFound = false;
					if (p1x === p2x) {
						if (p1y > p2y) {
							result[0] = p1x;
							result[1] = topLeftAy;
							result[2] = p2x;
							result[3] = bottomLeftBy;
							return false;
						} else if (p1y < p2y) {
							result[0] = p1x;
							result[1] = bottomLeftAy;
							result[2] = p2x;
							result[3] = topLeftBy;
							return false;
						}
					} else if (p1y === p2y) {
						if (p1x > p2x) {
							result[0] = topLeftAx;
							result[1] = p1y;
							result[2] = topRightBx;
							result[3] = p2y;
							return false;
						} else if (p1x < p2x) {
							result[0] = topRightAx;
							result[1] = p1y;
							result[2] = topLeftBx;
							result[3] = p2y;
							return false;
						}
					} else {
						var slopeA = rectA.height / rectA.width;
						var slopeB = rectB.height / rectB.width;
						var slopePrime = (p2y - p1y) / (p2x - p1x);
						var cardinalDirectionA = void 0;
						var cardinalDirectionB = void 0;
						var tempPointAx = void 0;
						var tempPointAy = void 0;
						var tempPointBx = void 0;
						var tempPointBy = void 0;
						if (-slopeA === slopePrime) if (p1x > p2x) {
							result[0] = bottomLeftAx;
							result[1] = bottomLeftAy;
							clipPointAFound = true;
						} else {
							result[0] = topRightAx;
							result[1] = topLeftAy;
							clipPointAFound = true;
						}
						else if (slopeA === slopePrime) if (p1x > p2x) {
							result[0] = topLeftAx;
							result[1] = topLeftAy;
							clipPointAFound = true;
						} else {
							result[0] = bottomRightAx;
							result[1] = bottomLeftAy;
							clipPointAFound = true;
						}
						if (-slopeB === slopePrime) if (p2x > p1x) {
							result[2] = bottomLeftBx;
							result[3] = bottomLeftBy;
							clipPointBFound = true;
						} else {
							result[2] = topRightBx;
							result[3] = topLeftBy;
							clipPointBFound = true;
						}
						else if (slopeB === slopePrime) if (p2x > p1x) {
							result[2] = topLeftBx;
							result[3] = topLeftBy;
							clipPointBFound = true;
						} else {
							result[2] = bottomRightBx;
							result[3] = bottomLeftBy;
							clipPointBFound = true;
						}
						if (clipPointAFound && clipPointBFound) return false;
						if (p1x > p2x) if (p1y > p2y) {
							cardinalDirectionA = this.getCardinalDirection(slopeA, slopePrime, 4);
							cardinalDirectionB = this.getCardinalDirection(slopeB, slopePrime, 2);
						} else {
							cardinalDirectionA = this.getCardinalDirection(-slopeA, slopePrime, 3);
							cardinalDirectionB = this.getCardinalDirection(-slopeB, slopePrime, 1);
						}
						else if (p1y > p2y) {
							cardinalDirectionA = this.getCardinalDirection(-slopeA, slopePrime, 1);
							cardinalDirectionB = this.getCardinalDirection(-slopeB, slopePrime, 3);
						} else {
							cardinalDirectionA = this.getCardinalDirection(slopeA, slopePrime, 2);
							cardinalDirectionB = this.getCardinalDirection(slopeB, slopePrime, 4);
						}
						if (!clipPointAFound) switch (cardinalDirectionA) {
							case 1:
								tempPointAy = topLeftAy;
								tempPointAx = p1x + -halfHeightA / slopePrime;
								result[0] = tempPointAx;
								result[1] = tempPointAy;
								break;
							case 2:
								tempPointAx = bottomRightAx;
								tempPointAy = p1y + halfWidthA * slopePrime;
								result[0] = tempPointAx;
								result[1] = tempPointAy;
								break;
							case 3:
								tempPointAy = bottomLeftAy;
								tempPointAx = p1x + halfHeightA / slopePrime;
								result[0] = tempPointAx;
								result[1] = tempPointAy;
								break;
							case 4:
								tempPointAx = bottomLeftAx;
								tempPointAy = p1y + -halfWidthA * slopePrime;
								result[0] = tempPointAx;
								result[1] = tempPointAy;
								break;
						}
						if (!clipPointBFound) switch (cardinalDirectionB) {
							case 1:
								tempPointBy = topLeftBy;
								tempPointBx = p2x + -halfHeightB / slopePrime;
								result[2] = tempPointBx;
								result[3] = tempPointBy;
								break;
							case 2:
								tempPointBx = bottomRightBx;
								tempPointBy = p2y + halfWidthB * slopePrime;
								result[2] = tempPointBx;
								result[3] = tempPointBy;
								break;
							case 3:
								tempPointBy = bottomLeftBy;
								tempPointBx = p2x + halfHeightB / slopePrime;
								result[2] = tempPointBx;
								result[3] = tempPointBy;
								break;
							case 4:
								tempPointBx = bottomLeftBx;
								tempPointBy = p2y + -halfWidthB * slopePrime;
								result[2] = tempPointBx;
								result[3] = tempPointBy;
								break;
						}
					}
					return false;
				};
				/**
				* This method returns in which cardinal direction does input point stays
				* 1: North
				* 2: East
				* 3: South
				* 4: West
				*/
				IGeometry.getCardinalDirection = function(slope, slopePrime, line) {
					if (slope > slopePrime) return line;
					else return 1 + line % 4;
				};
				/**
				* This method calculates the intersection of the two lines defined by
				* point pairs (s1,s2) and (f1,f2).
				*/
				IGeometry.getIntersection = function(s1, s2, f1, f2) {
					if (f2 == null) return this.getIntersection2(s1, s2, f1);
					var x1 = s1.x;
					var y1 = s1.y;
					var x2 = s2.x;
					var y2 = s2.y;
					var x3 = f1.x;
					var y3 = f1.y;
					var x4 = f2.x;
					var y4 = f2.y;
					var x = void 0, y = void 0;
					var a1 = void 0, a2 = void 0, b1 = void 0, b2 = void 0, c1 = void 0, c2 = void 0;
					var denom = void 0;
					a1 = y2 - y1;
					b1 = x1 - x2;
					c1 = x2 * y1 - x1 * y2;
					a2 = y4 - y3;
					b2 = x3 - x4;
					c2 = x4 * y3 - x3 * y4;
					denom = a1 * b2 - a2 * b1;
					if (denom === 0) return null;
					x = (b1 * c2 - b2 * c1) / denom;
					y = (a2 * c1 - a1 * c2) / denom;
					return new Point(x, y);
				};
				/**
				* This method finds and returns the angle of the vector from the + x-axis
				* in clockwise direction (compatible w/ Java coordinate system!).
				*/
				IGeometry.angleOfVector = function(Cx, Cy, Nx, Ny) {
					var C_angle = void 0;
					if (Cx !== Nx) {
						C_angle = Math.atan((Ny - Cy) / (Nx - Cx));
						if (Nx < Cx) C_angle += Math.PI;
						else if (Ny < Cy) C_angle += this.TWO_PI;
					} else if (Ny < Cy) C_angle = this.ONE_AND_HALF_PI;
					else C_angle = this.HALF_PI;
					return C_angle;
				};
				/**
				* This method checks whether the given two line segments (one with point
				* p1 and p2, the other with point p3 and p4) intersect at a point other
				* than these points.
				*/
				IGeometry.doIntersect = function(p1, p2, p3, p4) {
					var a = p1.x;
					var b = p1.y;
					var c = p2.x;
					var d = p2.y;
					var p = p3.x;
					var q = p3.y;
					var r = p4.x;
					var s = p4.y;
					var det = (c - a) * (s - q) - (r - p) * (d - b);
					if (det === 0) return false;
					else {
						var lambda = ((s - q) * (r - a) + (p - r) * (s - b)) / det;
						var gamma = ((b - d) * (r - a) + (c - a) * (s - b)) / det;
						return 0 < lambda && lambda < 1 && 0 < gamma && gamma < 1;
					}
				};
				/**
				* This method checks and calculates the intersection of 
				* a line segment and a circle.
				*/
				IGeometry.findCircleLineIntersections = function(Ex, Ey, Lx, Ly, Cx, Cy, r) {
					var a = (Lx - Ex) * (Lx - Ex) + (Ly - Ey) * (Ly - Ey);
					var b = 2 * ((Ex - Cx) * (Lx - Ex) + (Ey - Cy) * (Ly - Ey));
					var c = (Ex - Cx) * (Ex - Cx) + (Ey - Cy) * (Ey - Cy) - r * r;
					if (b * b - 4 * a * c >= 0) {
						var t1 = (-b + Math.sqrt(b * b - 4 * a * c)) / (2 * a);
						var t2 = (-b - Math.sqrt(b * b - 4 * a * c)) / (2 * a);
						var intersections = null;
						if (t1 >= 0 && t1 <= 1) return [t1];
						if (t2 >= 0 && t2 <= 1) return [t2];
						return intersections;
					} else return null;
				};
				/**
				* Some useful pre-calculated constants
				*/
				IGeometry.HALF_PI = .5 * Math.PI;
				IGeometry.ONE_AND_HALF_PI = 1.5 * Math.PI;
				IGeometry.TWO_PI = 2 * Math.PI;
				IGeometry.THREE_PI = 3 * Math.PI;
				module$61.exports = IGeometry;
			}),
			(function(module$62, exports$49, __webpack_require__) {
				"use strict";
				function IMath() {}
				/**
				* This method returns the sign of the input value.
				*/
				IMath.sign = function(value) {
					if (value > 0) return 1;
					else if (value < 0) return -1;
					else return 0;
				};
				IMath.floor = function(value) {
					return value < 0 ? Math.ceil(value) : Math.floor(value);
				};
				IMath.ceil = function(value) {
					return value < 0 ? Math.floor(value) : Math.ceil(value);
				};
				module$62.exports = IMath;
			}),
			(function(module$63, exports$50, __webpack_require__) {
				"use strict";
				function Integer() {}
				Integer.MAX_VALUE = 2147483647;
				Integer.MIN_VALUE = -2147483648;
				module$63.exports = Integer;
			}),
			(function(module$64, exports$51, __webpack_require__) {
				"use strict";
				var _createClass = function() {
					function defineProperties(target, props) {
						for (var i = 0; i < props.length; i++) {
							var descriptor = props[i];
							descriptor.enumerable = descriptor.enumerable || false;
							descriptor.configurable = true;
							if ("value" in descriptor) descriptor.writable = true;
							Object.defineProperty(target, descriptor.key, descriptor);
						}
					}
					return function(Constructor, protoProps, staticProps) {
						if (protoProps) defineProperties(Constructor.prototype, protoProps);
						if (staticProps) defineProperties(Constructor, staticProps);
						return Constructor;
					};
				}();
				function _classCallCheck(instance, Constructor) {
					if (!(instance instanceof Constructor)) throw new TypeError("Cannot call a class as a function");
				}
				var nodeFrom = function nodeFrom(value) {
					return {
						value,
						next: null,
						prev: null
					};
				};
				var add = function add(prev, node, next, list) {
					if (prev !== null) prev.next = node;
					else list.head = node;
					if (next !== null) next.prev = node;
					else list.tail = node;
					node.prev = prev;
					node.next = next;
					list.length++;
					return node;
				};
				var _remove = function _remove(node, list) {
					var prev = node.prev, next = node.next;
					if (prev !== null) prev.next = next;
					else list.head = next;
					if (next !== null) next.prev = prev;
					else list.tail = prev;
					node.prev = node.next = null;
					list.length--;
					return node;
				};
				module$64.exports = function() {
					function LinkedList(vals) {
						var _this = this;
						_classCallCheck(this, LinkedList);
						this.length = 0;
						this.head = null;
						this.tail = null;
						if (vals != null) vals.forEach(function(v) {
							return _this.push(v);
						});
					}
					_createClass(LinkedList, [
						{
							key: "size",
							value: function size() {
								return this.length;
							}
						},
						{
							key: "insertBefore",
							value: function insertBefore(val, otherNode) {
								return add(otherNode.prev, nodeFrom(val), otherNode, this);
							}
						},
						{
							key: "insertAfter",
							value: function insertAfter(val, otherNode) {
								return add(otherNode, nodeFrom(val), otherNode.next, this);
							}
						},
						{
							key: "insertNodeBefore",
							value: function insertNodeBefore(newNode, otherNode) {
								return add(otherNode.prev, newNode, otherNode, this);
							}
						},
						{
							key: "insertNodeAfter",
							value: function insertNodeAfter(newNode, otherNode) {
								return add(otherNode, newNode, otherNode.next, this);
							}
						},
						{
							key: "push",
							value: function push(val) {
								return add(this.tail, nodeFrom(val), null, this);
							}
						},
						{
							key: "unshift",
							value: function unshift(val) {
								return add(null, nodeFrom(val), this.head, this);
							}
						},
						{
							key: "remove",
							value: function remove(node) {
								return _remove(node, this);
							}
						},
						{
							key: "pop",
							value: function pop() {
								return _remove(this.tail, this).value;
							}
						},
						{
							key: "popNode",
							value: function popNode() {
								return _remove(this.tail, this);
							}
						},
						{
							key: "shift",
							value: function shift() {
								return _remove(this.head, this).value;
							}
						},
						{
							key: "shiftNode",
							value: function shiftNode() {
								return _remove(this.head, this);
							}
						},
						{
							key: "get_object_at",
							value: function get_object_at(index) {
								if (index <= this.length()) {
									var i = 1;
									var current = this.head;
									while (i < index) {
										current = current.next;
										i++;
									}
									return current.value;
								}
							}
						},
						{
							key: "set_object_at",
							value: function set_object_at(index, value) {
								if (index <= this.length()) {
									var i = 1;
									var current = this.head;
									while (i < index) {
										current = current.next;
										i++;
									}
									current.value = value;
								}
							}
						}
					]);
					return LinkedList;
				}();
			}),
			(function(module$65, exports$52, __webpack_require__) {
				"use strict";
				function Point(x, y, p) {
					this.x = null;
					this.y = null;
					if (x == null && y == null && p == null) {
						this.x = 0;
						this.y = 0;
					} else if (typeof x == "number" && typeof y == "number" && p == null) {
						this.x = x;
						this.y = y;
					} else if (x.constructor.name == "Point" && y == null && p == null) {
						p = x;
						this.x = p.x;
						this.y = p.y;
					}
				}
				Point.prototype.getX = function() {
					return this.x;
				};
				Point.prototype.getY = function() {
					return this.y;
				};
				Point.prototype.getLocation = function() {
					return new Point(this.x, this.y);
				};
				Point.prototype.setLocation = function(x, y, p) {
					if (x.constructor.name == "Point" && y == null && p == null) {
						p = x;
						this.setLocation(p.x, p.y);
					} else if (typeof x == "number" && typeof y == "number" && p == null) if (parseInt(x) == x && parseInt(y) == y) this.move(x, y);
					else {
						this.x = Math.floor(x + .5);
						this.y = Math.floor(y + .5);
					}
				};
				Point.prototype.move = function(x, y) {
					this.x = x;
					this.y = y;
				};
				Point.prototype.translate = function(dx, dy) {
					this.x += dx;
					this.y += dy;
				};
				Point.prototype.equals = function(obj) {
					if (obj.constructor.name == "Point") {
						var pt = obj;
						return this.x == pt.x && this.y == pt.y;
					}
					return this == obj;
				};
				Point.prototype.toString = function() {
					return new Point().constructor.name + "[x=" + this.x + ",y=" + this.y + "]";
				};
				module$65.exports = Point;
			}),
			(function(module$66, exports$53, __webpack_require__) {
				"use strict";
				function RectangleD(x, y, width, height) {
					this.x = 0;
					this.y = 0;
					this.width = 0;
					this.height = 0;
					if (x != null && y != null && width != null && height != null) {
						this.x = x;
						this.y = y;
						this.width = width;
						this.height = height;
					}
				}
				RectangleD.prototype.getX = function() {
					return this.x;
				};
				RectangleD.prototype.setX = function(x) {
					this.x = x;
				};
				RectangleD.prototype.getY = function() {
					return this.y;
				};
				RectangleD.prototype.setY = function(y) {
					this.y = y;
				};
				RectangleD.prototype.getWidth = function() {
					return this.width;
				};
				RectangleD.prototype.setWidth = function(width) {
					this.width = width;
				};
				RectangleD.prototype.getHeight = function() {
					return this.height;
				};
				RectangleD.prototype.setHeight = function(height) {
					this.height = height;
				};
				RectangleD.prototype.getRight = function() {
					return this.x + this.width;
				};
				RectangleD.prototype.getBottom = function() {
					return this.y + this.height;
				};
				RectangleD.prototype.intersects = function(a) {
					if (this.getRight() < a.x) return false;
					if (this.getBottom() < a.y) return false;
					if (a.getRight() < this.x) return false;
					if (a.getBottom() < this.y) return false;
					return true;
				};
				RectangleD.prototype.getCenterX = function() {
					return this.x + this.width / 2;
				};
				RectangleD.prototype.getMinX = function() {
					return this.getX();
				};
				RectangleD.prototype.getMaxX = function() {
					return this.getX() + this.width;
				};
				RectangleD.prototype.getCenterY = function() {
					return this.y + this.height / 2;
				};
				RectangleD.prototype.getMinY = function() {
					return this.getY();
				};
				RectangleD.prototype.getMaxY = function() {
					return this.getY() + this.height;
				};
				RectangleD.prototype.getWidthHalf = function() {
					return this.width / 2;
				};
				RectangleD.prototype.getHeightHalf = function() {
					return this.height / 2;
				};
				module$66.exports = RectangleD;
			}),
			(function(module$67, exports$54, __webpack_require__) {
				"use strict";
				var _typeof = typeof Symbol === "function" && typeof Symbol.iterator === "symbol" ? function(obj) {
					return typeof obj;
				} : function(obj) {
					return obj && typeof Symbol === "function" && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj;
				};
				function UniqueIDGeneretor() {}
				UniqueIDGeneretor.lastID = 0;
				UniqueIDGeneretor.createID = function(obj) {
					if (UniqueIDGeneretor.isPrimitive(obj)) return obj;
					if (obj.uniqueID != null) return obj.uniqueID;
					obj.uniqueID = UniqueIDGeneretor.getString();
					UniqueIDGeneretor.lastID++;
					return obj.uniqueID;
				};
				UniqueIDGeneretor.getString = function(id) {
					if (id == null) id = UniqueIDGeneretor.lastID;
					return "Object#" + id;
				};
				UniqueIDGeneretor.isPrimitive = function(arg) {
					var type = typeof arg === "undefined" ? "undefined" : _typeof(arg);
					return arg == null || type != "object" && type != "function";
				};
				module$67.exports = UniqueIDGeneretor;
			}),
			(function(module$68, exports$55, __webpack_require__) {
				"use strict";
				function _toConsumableArray(arr) {
					if (Array.isArray(arr)) {
						for (var i = 0, arr2 = Array(arr.length); i < arr.length; i++) arr2[i] = arr[i];
						return arr2;
					} else return Array.from(arr);
				}
				var LayoutConstants = __webpack_require__(0);
				var LGraphManager = __webpack_require__(7);
				var LNode = __webpack_require__(3);
				var LEdge = __webpack_require__(1);
				var LGraph = __webpack_require__(6);
				var PointD = __webpack_require__(5);
				var Transform = __webpack_require__(17);
				var Emitter = __webpack_require__(29);
				function Layout(isRemoteUse) {
					Emitter.call(this);
					this.layoutQuality = LayoutConstants.QUALITY;
					this.createBendsAsNeeded = LayoutConstants.DEFAULT_CREATE_BENDS_AS_NEEDED;
					this.incremental = LayoutConstants.DEFAULT_INCREMENTAL;
					this.animationOnLayout = LayoutConstants.DEFAULT_ANIMATION_ON_LAYOUT;
					this.animationDuringLayout = LayoutConstants.DEFAULT_ANIMATION_DURING_LAYOUT;
					this.animationPeriod = LayoutConstants.DEFAULT_ANIMATION_PERIOD;
					/**
					* Whether or not leaf nodes (non-compound nodes) are of uniform sizes. When
					* they are, both spring and repulsion forces between two leaf nodes can be
					* calculated without the expensive clipping point calculations, resulting
					* in major speed-up.
					*/
					this.uniformLeafNodeSizes = LayoutConstants.DEFAULT_UNIFORM_LEAF_NODE_SIZES;
					/**
					* This is used for creation of bendpoints by using dummy nodes and edges.
					* Maps an LEdge to its dummy bendpoint path.
					*/
					this.edgeToDummyNodes = /* @__PURE__ */ new Map();
					this.graphManager = new LGraphManager(this);
					this.isLayoutFinished = false;
					this.isSubLayout = false;
					this.isRemoteUse = false;
					if (isRemoteUse != null) this.isRemoteUse = isRemoteUse;
				}
				Layout.RANDOM_SEED = 1;
				Layout.prototype = Object.create(Emitter.prototype);
				Layout.prototype.getGraphManager = function() {
					return this.graphManager;
				};
				Layout.prototype.getAllNodes = function() {
					return this.graphManager.getAllNodes();
				};
				Layout.prototype.getAllEdges = function() {
					return this.graphManager.getAllEdges();
				};
				Layout.prototype.getAllNodesToApplyGravitation = function() {
					return this.graphManager.getAllNodesToApplyGravitation();
				};
				Layout.prototype.newGraphManager = function() {
					var gm = new LGraphManager(this);
					this.graphManager = gm;
					return gm;
				};
				Layout.prototype.newGraph = function(vGraph) {
					return new LGraph(null, this.graphManager, vGraph);
				};
				Layout.prototype.newNode = function(vNode) {
					return new LNode(this.graphManager, vNode);
				};
				Layout.prototype.newEdge = function(vEdge) {
					return new LEdge(null, null, vEdge);
				};
				Layout.prototype.checkLayoutSuccess = function() {
					return this.graphManager.getRoot() == null || this.graphManager.getRoot().getNodes().length == 0 || this.graphManager.includesInvalidEdge();
				};
				Layout.prototype.runLayout = function() {
					this.isLayoutFinished = false;
					if (this.tilingPreLayout) this.tilingPreLayout();
					this.initParameters();
					var isLayoutSuccessfull;
					if (this.checkLayoutSuccess()) isLayoutSuccessfull = false;
					else isLayoutSuccessfull = this.layout();
					if (LayoutConstants.ANIMATE === "during") return false;
					if (isLayoutSuccessfull) {
						if (!this.isSubLayout) this.doPostLayout();
					}
					if (this.tilingPostLayout) this.tilingPostLayout();
					this.isLayoutFinished = true;
					return isLayoutSuccessfull;
				};
				/**
				* This method performs the operations required after layout.
				*/
				Layout.prototype.doPostLayout = function() {
					if (!this.incremental) this.transform();
					this.update();
				};
				/**
				* This method updates the geometry of the target graph according to
				* calculated layout.
				*/
				Layout.prototype.update2 = function() {
					if (this.createBendsAsNeeded) {
						this.createBendpointsFromDummyNodes();
						this.graphManager.resetAllEdges();
					}
					if (!this.isRemoteUse) {
						var allEdges = this.graphManager.getAllEdges();
						for (var i = 0; i < allEdges.length; i++) allEdges[i];
						var nodes = this.graphManager.getRoot().getNodes();
						for (var i = 0; i < nodes.length; i++) nodes[i];
						this.update(this.graphManager.getRoot());
					}
				};
				Layout.prototype.update = function(obj) {
					if (obj == null) this.update2();
					else if (obj instanceof LNode) {
						var node = obj;
						if (node.getChild() != null) {
							var nodes = node.getChild().getNodes();
							for (var i = 0; i < nodes.length; i++) update(nodes[i]);
						}
						if (node.vGraphObject != null) node.vGraphObject.update(node);
					} else if (obj instanceof LEdge) {
						var edge = obj;
						if (edge.vGraphObject != null) edge.vGraphObject.update(edge);
					} else if (obj instanceof LGraph) {
						var graph = obj;
						if (graph.vGraphObject != null) graph.vGraphObject.update(graph);
					}
				};
				/**
				* This method is used to set all layout parameters to default values
				* determined at compile time.
				*/
				Layout.prototype.initParameters = function() {
					if (!this.isSubLayout) {
						this.layoutQuality = LayoutConstants.QUALITY;
						this.animationDuringLayout = LayoutConstants.DEFAULT_ANIMATION_DURING_LAYOUT;
						this.animationPeriod = LayoutConstants.DEFAULT_ANIMATION_PERIOD;
						this.animationOnLayout = LayoutConstants.DEFAULT_ANIMATION_ON_LAYOUT;
						this.incremental = LayoutConstants.DEFAULT_INCREMENTAL;
						this.createBendsAsNeeded = LayoutConstants.DEFAULT_CREATE_BENDS_AS_NEEDED;
						this.uniformLeafNodeSizes = LayoutConstants.DEFAULT_UNIFORM_LEAF_NODE_SIZES;
					}
					if (this.animationDuringLayout) this.animationOnLayout = false;
				};
				Layout.prototype.transform = function(newLeftTop) {
					if (newLeftTop == void 0) this.transform(new PointD(0, 0));
					else {
						var trans = new Transform();
						var leftTop = this.graphManager.getRoot().updateLeftTop();
						if (leftTop != null) {
							trans.setWorldOrgX(newLeftTop.x);
							trans.setWorldOrgY(newLeftTop.y);
							trans.setDeviceOrgX(leftTop.x);
							trans.setDeviceOrgY(leftTop.y);
							var nodes = this.getAllNodes();
							var node;
							for (var i = 0; i < nodes.length; i++) {
								node = nodes[i];
								node.transform(trans);
							}
						}
					}
				};
				Layout.prototype.positionNodesRandomly = function(graph) {
					if (graph == void 0) {
						this.positionNodesRandomly(this.getGraphManager().getRoot());
						this.getGraphManager().getRoot().updateBounds(true);
					} else {
						var lNode;
						var childGraph;
						var nodes = graph.getNodes();
						for (var i = 0; i < nodes.length; i++) {
							lNode = nodes[i];
							childGraph = lNode.getChild();
							if (childGraph == null) lNode.scatter();
							else if (childGraph.getNodes().length == 0) lNode.scatter();
							else {
								this.positionNodesRandomly(childGraph);
								lNode.updateBounds();
							}
						}
					}
				};
				/**
				* This method returns a list of trees where each tree is represented as a
				* list of l-nodes. The method returns a list of size 0 when:
				* - The graph is not flat or
				* - One of the component(s) of the graph is not a tree.
				*/
				Layout.prototype.getFlatForest = function() {
					var flatForest = [];
					var isForest = true;
					var allNodes = this.graphManager.getRoot().getNodes();
					var isFlat = true;
					for (var i = 0; i < allNodes.length; i++) if (allNodes[i].getChild() != null) isFlat = false;
					if (!isFlat) return flatForest;
					var visited = /* @__PURE__ */ new Set();
					var toBeVisited = [];
					var parents = /* @__PURE__ */ new Map();
					var unProcessedNodes = [];
					unProcessedNodes = unProcessedNodes.concat(allNodes);
					while (unProcessedNodes.length > 0 && isForest) {
						toBeVisited.push(unProcessedNodes[0]);
						while (toBeVisited.length > 0 && isForest) {
							var currentNode = toBeVisited[0];
							toBeVisited.splice(0, 1);
							visited.add(currentNode);
							var neighborEdges = currentNode.getEdges();
							for (var i = 0; i < neighborEdges.length; i++) {
								var currentNeighbor = neighborEdges[i].getOtherEnd(currentNode);
								if (parents.get(currentNode) != currentNeighbor) if (!visited.has(currentNeighbor)) {
									toBeVisited.push(currentNeighbor);
									parents.set(currentNeighbor, currentNode);
								} else {
									isForest = false;
									break;
								}
							}
						}
						if (!isForest) flatForest = [];
						else {
							var temp = [].concat(_toConsumableArray(visited));
							flatForest.push(temp);
							for (var i = 0; i < temp.length; i++) {
								var value = temp[i];
								var index = unProcessedNodes.indexOf(value);
								if (index > -1) unProcessedNodes.splice(index, 1);
							}
							visited = /* @__PURE__ */ new Set();
							parents = /* @__PURE__ */ new Map();
						}
					}
					return flatForest;
				};
				/**
				* This method creates dummy nodes (an l-level node with minimal dimensions)
				* for the given edge (one per bendpoint). The existing l-level structure
				* is updated accordingly.
				*/
				Layout.prototype.createDummyNodesForBendpoints = function(edge) {
					var dummyNodes = [];
					var prev = edge.source;
					var graph = this.graphManager.calcLowestCommonAncestor(edge.source, edge.target);
					for (var i = 0; i < edge.bendpoints.length; i++) {
						var dummyNode = this.newNode(null);
						dummyNode.setRect(new Point(0, 0), new Dimension(1, 1));
						graph.add(dummyNode);
						var dummyEdge = this.newEdge(null);
						this.graphManager.add(dummyEdge, prev, dummyNode);
						dummyNodes.add(dummyNode);
						prev = dummyNode;
					}
					var dummyEdge = this.newEdge(null);
					this.graphManager.add(dummyEdge, prev, edge.target);
					this.edgeToDummyNodes.set(edge, dummyNodes);
					if (edge.isInterGraph()) this.graphManager.remove(edge);
					else graph.remove(edge);
					return dummyNodes;
				};
				/**
				* This method creates bendpoints for edges from the dummy nodes
				* at l-level.
				*/
				Layout.prototype.createBendpointsFromDummyNodes = function() {
					var edges = [];
					edges = edges.concat(this.graphManager.getAllEdges());
					edges = [].concat(_toConsumableArray(this.edgeToDummyNodes.keys())).concat(edges);
					for (var k = 0; k < edges.length; k++) {
						var lEdge = edges[k];
						if (lEdge.bendpoints.length > 0) {
							var path = this.edgeToDummyNodes.get(lEdge);
							for (var i = 0; i < path.length; i++) {
								var dummyNode = path[i];
								var p = new PointD(dummyNode.getCenterX(), dummyNode.getCenterY());
								var ebp = lEdge.bendpoints.get(i);
								ebp.x = p.x;
								ebp.y = p.y;
								dummyNode.getOwner().remove(dummyNode);
							}
							this.graphManager.add(lEdge, lEdge.source, lEdge.target);
						}
					}
				};
				Layout.transform = function(sliderValue, defaultValue, minDiv, maxMul) {
					if (minDiv != void 0 && maxMul != void 0) {
						var value = defaultValue;
						if (sliderValue <= 50) {
							var minValue = defaultValue / minDiv;
							value -= (defaultValue - minValue) / 50 * (50 - sliderValue);
						} else {
							var maxValue = defaultValue * maxMul;
							value += (maxValue - defaultValue) / 50 * (sliderValue - 50);
						}
						return value;
					} else {
						var a, b;
						if (sliderValue <= 50) {
							a = 9 * defaultValue / 500;
							b = defaultValue / 10;
						} else {
							a = 9 * defaultValue / 50;
							b = -8 * defaultValue;
						}
						return a * sliderValue + b;
					}
				};
				/**
				* This method finds and returns the center of the given nodes, assuming
				* that the given nodes form a tree in themselves.
				*/
				Layout.findCenterOfTree = function(nodes) {
					var list = [];
					list = list.concat(nodes);
					var removedNodes = [];
					var remainingDegrees = /* @__PURE__ */ new Map();
					var foundCenter = false;
					var centerNode = null;
					if (list.length == 1 || list.length == 2) {
						foundCenter = true;
						centerNode = list[0];
					}
					for (var i = 0; i < list.length; i++) {
						var node = list[i];
						var degree = node.getNeighborsList().size;
						remainingDegrees.set(node, node.getNeighborsList().size);
						if (degree == 1) removedNodes.push(node);
					}
					var tempList = [];
					tempList = tempList.concat(removedNodes);
					while (!foundCenter) {
						var tempList2 = [];
						tempList2 = tempList2.concat(tempList);
						tempList = [];
						for (var i = 0; i < list.length; i++) {
							var node = list[i];
							var index = list.indexOf(node);
							if (index >= 0) list.splice(index, 1);
							node.getNeighborsList().forEach(function(neighbour) {
								if (removedNodes.indexOf(neighbour) < 0) {
									var newDegree = remainingDegrees.get(neighbour) - 1;
									if (newDegree == 1) tempList.push(neighbour);
									remainingDegrees.set(neighbour, newDegree);
								}
							});
						}
						removedNodes = removedNodes.concat(tempList);
						if (list.length == 1 || list.length == 2) {
							foundCenter = true;
							centerNode = list[0];
						}
					}
					return centerNode;
				};
				/**
				* During the coarsening process, this layout may be referenced by two graph managers
				* this setter function grants access to change the currently being used graph manager
				*/
				Layout.prototype.setGraphManager = function(gm) {
					this.graphManager = gm;
				};
				module$68.exports = Layout;
			}),
			(function(module$69, exports$56, __webpack_require__) {
				"use strict";
				function RandomSeed() {}
				RandomSeed.seed = 1;
				RandomSeed.x = 0;
				RandomSeed.nextDouble = function() {
					RandomSeed.x = Math.sin(RandomSeed.seed++) * 1e4;
					return RandomSeed.x - Math.floor(RandomSeed.x);
				};
				module$69.exports = RandomSeed;
			}),
			(function(module$70, exports$57, __webpack_require__) {
				"use strict";
				var PointD = __webpack_require__(5);
				function Transform(x, y) {
					this.lworldOrgX = 0;
					this.lworldOrgY = 0;
					this.ldeviceOrgX = 0;
					this.ldeviceOrgY = 0;
					this.lworldExtX = 1;
					this.lworldExtY = 1;
					this.ldeviceExtX = 1;
					this.ldeviceExtY = 1;
				}
				Transform.prototype.getWorldOrgX = function() {
					return this.lworldOrgX;
				};
				Transform.prototype.setWorldOrgX = function(wox) {
					this.lworldOrgX = wox;
				};
				Transform.prototype.getWorldOrgY = function() {
					return this.lworldOrgY;
				};
				Transform.prototype.setWorldOrgY = function(woy) {
					this.lworldOrgY = woy;
				};
				Transform.prototype.getWorldExtX = function() {
					return this.lworldExtX;
				};
				Transform.prototype.setWorldExtX = function(wex) {
					this.lworldExtX = wex;
				};
				Transform.prototype.getWorldExtY = function() {
					return this.lworldExtY;
				};
				Transform.prototype.setWorldExtY = function(wey) {
					this.lworldExtY = wey;
				};
				Transform.prototype.getDeviceOrgX = function() {
					return this.ldeviceOrgX;
				};
				Transform.prototype.setDeviceOrgX = function(dox) {
					this.ldeviceOrgX = dox;
				};
				Transform.prototype.getDeviceOrgY = function() {
					return this.ldeviceOrgY;
				};
				Transform.prototype.setDeviceOrgY = function(doy) {
					this.ldeviceOrgY = doy;
				};
				Transform.prototype.getDeviceExtX = function() {
					return this.ldeviceExtX;
				};
				Transform.prototype.setDeviceExtX = function(dex) {
					this.ldeviceExtX = dex;
				};
				Transform.prototype.getDeviceExtY = function() {
					return this.ldeviceExtY;
				};
				Transform.prototype.setDeviceExtY = function(dey) {
					this.ldeviceExtY = dey;
				};
				Transform.prototype.transformX = function(x) {
					var xDevice = 0;
					var worldExtX = this.lworldExtX;
					if (worldExtX != 0) xDevice = this.ldeviceOrgX + (x - this.lworldOrgX) * this.ldeviceExtX / worldExtX;
					return xDevice;
				};
				Transform.prototype.transformY = function(y) {
					var yDevice = 0;
					var worldExtY = this.lworldExtY;
					if (worldExtY != 0) yDevice = this.ldeviceOrgY + (y - this.lworldOrgY) * this.ldeviceExtY / worldExtY;
					return yDevice;
				};
				Transform.prototype.inverseTransformX = function(x) {
					var xWorld = 0;
					var deviceExtX = this.ldeviceExtX;
					if (deviceExtX != 0) xWorld = this.lworldOrgX + (x - this.ldeviceOrgX) * this.lworldExtX / deviceExtX;
					return xWorld;
				};
				Transform.prototype.inverseTransformY = function(y) {
					var yWorld = 0;
					var deviceExtY = this.ldeviceExtY;
					if (deviceExtY != 0) yWorld = this.lworldOrgY + (y - this.ldeviceOrgY) * this.lworldExtY / deviceExtY;
					return yWorld;
				};
				Transform.prototype.inverseTransformPoint = function(inPoint) {
					return new PointD(this.inverseTransformX(inPoint.x), this.inverseTransformY(inPoint.y));
				};
				module$70.exports = Transform;
			}),
			(function(module$71, exports$58, __webpack_require__) {
				"use strict";
				function _toConsumableArray(arr) {
					if (Array.isArray(arr)) {
						for (var i = 0, arr2 = Array(arr.length); i < arr.length; i++) arr2[i] = arr[i];
						return arr2;
					} else return Array.from(arr);
				}
				var Layout = __webpack_require__(15);
				var FDLayoutConstants = __webpack_require__(4);
				var LayoutConstants = __webpack_require__(0);
				var IGeometry = __webpack_require__(8);
				var IMath = __webpack_require__(9);
				function FDLayout() {
					Layout.call(this);
					this.useSmartIdealEdgeLengthCalculation = FDLayoutConstants.DEFAULT_USE_SMART_IDEAL_EDGE_LENGTH_CALCULATION;
					this.gravityConstant = FDLayoutConstants.DEFAULT_GRAVITY_STRENGTH;
					this.compoundGravityConstant = FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_STRENGTH;
					this.gravityRangeFactor = FDLayoutConstants.DEFAULT_GRAVITY_RANGE_FACTOR;
					this.compoundGravityRangeFactor = FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_RANGE_FACTOR;
					this.displacementThresholdPerNode = 3 * FDLayoutConstants.DEFAULT_EDGE_LENGTH / 100;
					this.coolingFactor = FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL;
					this.initialCoolingFactor = FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL;
					this.totalDisplacement = 0;
					this.oldTotalDisplacement = 0;
					this.maxIterations = FDLayoutConstants.MAX_ITERATIONS;
				}
				FDLayout.prototype = Object.create(Layout.prototype);
				for (var prop in Layout) FDLayout[prop] = Layout[prop];
				FDLayout.prototype.initParameters = function() {
					Layout.prototype.initParameters.call(this, arguments);
					this.totalIterations = 0;
					this.notAnimatedIterations = 0;
					this.useFRGridVariant = FDLayoutConstants.DEFAULT_USE_SMART_REPULSION_RANGE_CALCULATION;
					this.grid = [];
				};
				FDLayout.prototype.calcIdealEdgeLengths = function() {
					var edge;
					var originalIdealLength;
					var lcaDepth;
					var source;
					var target;
					var sizeOfSourceInLca;
					var sizeOfTargetInLca;
					var allEdges = this.getGraphManager().getAllEdges();
					for (var i = 0; i < allEdges.length; i++) {
						edge = allEdges[i];
						originalIdealLength = edge.idealLength;
						if (edge.isInterGraph) {
							source = edge.getSource();
							target = edge.getTarget();
							sizeOfSourceInLca = edge.getSourceInLca().getEstimatedSize();
							sizeOfTargetInLca = edge.getTargetInLca().getEstimatedSize();
							if (this.useSmartIdealEdgeLengthCalculation) edge.idealLength += sizeOfSourceInLca + sizeOfTargetInLca - 2 * LayoutConstants.SIMPLE_NODE_SIZE;
							lcaDepth = edge.getLca().getInclusionTreeDepth();
							edge.idealLength += originalIdealLength * FDLayoutConstants.PER_LEVEL_IDEAL_EDGE_LENGTH_FACTOR * (source.getInclusionTreeDepth() + target.getInclusionTreeDepth() - 2 * lcaDepth);
						}
					}
				};
				FDLayout.prototype.initSpringEmbedder = function() {
					var s = this.getAllNodes().length;
					if (this.incremental) {
						if (s > FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) this.coolingFactor = Math.max(this.coolingFactor * FDLayoutConstants.COOLING_ADAPTATION_FACTOR, this.coolingFactor - (s - FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) / (FDLayoutConstants.ADAPTATION_UPPER_NODE_LIMIT - FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) * this.coolingFactor * (1 - FDLayoutConstants.COOLING_ADAPTATION_FACTOR));
						this.maxNodeDisplacement = FDLayoutConstants.MAX_NODE_DISPLACEMENT_INCREMENTAL;
					} else {
						if (s > FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) this.coolingFactor = Math.max(FDLayoutConstants.COOLING_ADAPTATION_FACTOR, 1 - (s - FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) / (FDLayoutConstants.ADAPTATION_UPPER_NODE_LIMIT - FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) * (1 - FDLayoutConstants.COOLING_ADAPTATION_FACTOR));
						else this.coolingFactor = 1;
						this.initialCoolingFactor = this.coolingFactor;
						this.maxNodeDisplacement = FDLayoutConstants.MAX_NODE_DISPLACEMENT;
					}
					this.maxIterations = Math.max(this.getAllNodes().length * 5, this.maxIterations);
					this.displacementThresholdPerNode = 3 * FDLayoutConstants.DEFAULT_EDGE_LENGTH / 100;
					this.totalDisplacementThreshold = this.displacementThresholdPerNode * this.getAllNodes().length;
					this.repulsionRange = this.calcRepulsionRange();
				};
				FDLayout.prototype.calcSpringForces = function() {
					var lEdges = this.getAllEdges();
					var edge;
					for (var i = 0; i < lEdges.length; i++) {
						edge = lEdges[i];
						this.calcSpringForce(edge, edge.idealLength);
					}
				};
				FDLayout.prototype.calcRepulsionForces = function() {
					var gridUpdateAllowed = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : true;
					var forceToNodeSurroundingUpdate = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : false;
					var i, j;
					var nodeA, nodeB;
					var lNodes = this.getAllNodes();
					var processedNodeSet;
					if (this.useFRGridVariant) {
						if (this.totalIterations % FDLayoutConstants.GRID_CALCULATION_CHECK_PERIOD == 1 && gridUpdateAllowed) this.updateGrid();
						processedNodeSet = /* @__PURE__ */ new Set();
						for (i = 0; i < lNodes.length; i++) {
							nodeA = lNodes[i];
							this.calculateRepulsionForceOfANode(nodeA, processedNodeSet, gridUpdateAllowed, forceToNodeSurroundingUpdate);
							processedNodeSet.add(nodeA);
						}
					} else for (i = 0; i < lNodes.length; i++) {
						nodeA = lNodes[i];
						for (j = i + 1; j < lNodes.length; j++) {
							nodeB = lNodes[j];
							if (nodeA.getOwner() != nodeB.getOwner()) continue;
							this.calcRepulsionForce(nodeA, nodeB);
						}
					}
				};
				FDLayout.prototype.calcGravitationalForces = function() {
					var node;
					var lNodes = this.getAllNodesToApplyGravitation();
					for (var i = 0; i < lNodes.length; i++) {
						node = lNodes[i];
						this.calcGravitationalForce(node);
					}
				};
				FDLayout.prototype.moveNodes = function() {
					var lNodes = this.getAllNodes();
					var node;
					for (var i = 0; i < lNodes.length; i++) {
						node = lNodes[i];
						node.move();
					}
				};
				FDLayout.prototype.calcSpringForce = function(edge, idealLength) {
					var sourceNode = edge.getSource();
					var targetNode = edge.getTarget();
					var length;
					var springForce;
					var springForceX;
					var springForceY;
					if (this.uniformLeafNodeSizes && sourceNode.getChild() == null && targetNode.getChild() == null) edge.updateLengthSimple();
					else {
						edge.updateLength();
						if (edge.isOverlapingSourceAndTarget) return;
					}
					length = edge.getLength();
					if (length == 0) return;
					springForce = edge.edgeElasticity * (length - idealLength);
					springForceX = springForce * (edge.lengthX / length);
					springForceY = springForce * (edge.lengthY / length);
					sourceNode.springForceX += springForceX;
					sourceNode.springForceY += springForceY;
					targetNode.springForceX -= springForceX;
					targetNode.springForceY -= springForceY;
				};
				FDLayout.prototype.calcRepulsionForce = function(nodeA, nodeB) {
					var rectA = nodeA.getRect();
					var rectB = nodeB.getRect();
					var overlapAmount = new Array(2);
					var clipPoints = new Array(4);
					var distanceX;
					var distanceY;
					var distanceSquared;
					var distance;
					var repulsionForce;
					var repulsionForceX;
					var repulsionForceY;
					if (rectA.intersects(rectB)) {
						IGeometry.calcSeparationAmount(rectA, rectB, overlapAmount, FDLayoutConstants.DEFAULT_EDGE_LENGTH / 2);
						repulsionForceX = 2 * overlapAmount[0];
						repulsionForceY = 2 * overlapAmount[1];
						var childrenConstant = nodeA.noOfChildren * nodeB.noOfChildren / (nodeA.noOfChildren + nodeB.noOfChildren);
						nodeA.repulsionForceX -= childrenConstant * repulsionForceX;
						nodeA.repulsionForceY -= childrenConstant * repulsionForceY;
						nodeB.repulsionForceX += childrenConstant * repulsionForceX;
						nodeB.repulsionForceY += childrenConstant * repulsionForceY;
					} else {
						if (this.uniformLeafNodeSizes && nodeA.getChild() == null && nodeB.getChild() == null) {
							distanceX = rectB.getCenterX() - rectA.getCenterX();
							distanceY = rectB.getCenterY() - rectA.getCenterY();
						} else {
							IGeometry.getIntersection(rectA, rectB, clipPoints);
							distanceX = clipPoints[2] - clipPoints[0];
							distanceY = clipPoints[3] - clipPoints[1];
						}
						if (Math.abs(distanceX) < FDLayoutConstants.MIN_REPULSION_DIST) distanceX = IMath.sign(distanceX) * FDLayoutConstants.MIN_REPULSION_DIST;
						if (Math.abs(distanceY) < FDLayoutConstants.MIN_REPULSION_DIST) distanceY = IMath.sign(distanceY) * FDLayoutConstants.MIN_REPULSION_DIST;
						distanceSquared = distanceX * distanceX + distanceY * distanceY;
						distance = Math.sqrt(distanceSquared);
						repulsionForce = (nodeA.nodeRepulsion / 2 + nodeB.nodeRepulsion / 2) * nodeA.noOfChildren * nodeB.noOfChildren / distanceSquared;
						repulsionForceX = repulsionForce * distanceX / distance;
						repulsionForceY = repulsionForce * distanceY / distance;
						nodeA.repulsionForceX -= repulsionForceX;
						nodeA.repulsionForceY -= repulsionForceY;
						nodeB.repulsionForceX += repulsionForceX;
						nodeB.repulsionForceY += repulsionForceY;
					}
				};
				FDLayout.prototype.calcGravitationalForce = function(node) {
					var ownerGraph;
					var ownerCenterX;
					var ownerCenterY;
					var distanceX;
					var distanceY;
					var absDistanceX;
					var absDistanceY;
					var estimatedSize;
					ownerGraph = node.getOwner();
					ownerCenterX = (ownerGraph.getRight() + ownerGraph.getLeft()) / 2;
					ownerCenterY = (ownerGraph.getTop() + ownerGraph.getBottom()) / 2;
					distanceX = node.getCenterX() - ownerCenterX;
					distanceY = node.getCenterY() - ownerCenterY;
					absDistanceX = Math.abs(distanceX) + node.getWidth() / 2;
					absDistanceY = Math.abs(distanceY) + node.getHeight() / 2;
					if (node.getOwner() == this.graphManager.getRoot()) {
						estimatedSize = ownerGraph.getEstimatedSize() * this.gravityRangeFactor;
						if (absDistanceX > estimatedSize || absDistanceY > estimatedSize) {
							node.gravitationForceX = -this.gravityConstant * distanceX;
							node.gravitationForceY = -this.gravityConstant * distanceY;
						}
					} else {
						estimatedSize = ownerGraph.getEstimatedSize() * this.compoundGravityRangeFactor;
						if (absDistanceX > estimatedSize || absDistanceY > estimatedSize) {
							node.gravitationForceX = -this.gravityConstant * distanceX * this.compoundGravityConstant;
							node.gravitationForceY = -this.gravityConstant * distanceY * this.compoundGravityConstant;
						}
					}
				};
				FDLayout.prototype.isConverged = function() {
					var converged;
					var oscilating = false;
					if (this.totalIterations > this.maxIterations / 3) oscilating = Math.abs(this.totalDisplacement - this.oldTotalDisplacement) < 2;
					converged = this.totalDisplacement < this.totalDisplacementThreshold;
					this.oldTotalDisplacement = this.totalDisplacement;
					return converged || oscilating;
				};
				FDLayout.prototype.animate = function() {
					if (this.animationDuringLayout && !this.isSubLayout) if (this.notAnimatedIterations == this.animationPeriod) {
						this.update();
						this.notAnimatedIterations = 0;
					} else this.notAnimatedIterations++;
				};
				FDLayout.prototype.calcNoOfChildrenForAllNodes = function() {
					var node;
					var allNodes = this.graphManager.getAllNodes();
					for (var i = 0; i < allNodes.length; i++) {
						node = allNodes[i];
						node.noOfChildren = node.getNoOfChildren();
					}
				};
				FDLayout.prototype.calcGrid = function(graph) {
					var sizeX = 0;
					var sizeY = 0;
					sizeX = parseInt(Math.ceil((graph.getRight() - graph.getLeft()) / this.repulsionRange));
					sizeY = parseInt(Math.ceil((graph.getBottom() - graph.getTop()) / this.repulsionRange));
					var grid = new Array(sizeX);
					for (var i = 0; i < sizeX; i++) grid[i] = new Array(sizeY);
					for (var i = 0; i < sizeX; i++) for (var j = 0; j < sizeY; j++) grid[i][j] = new Array();
					return grid;
				};
				FDLayout.prototype.addNodeToGrid = function(v, left, top) {
					var startX = 0;
					var finishX = 0;
					var startY = 0;
					var finishY = 0;
					startX = parseInt(Math.floor((v.getRect().x - left) / this.repulsionRange));
					finishX = parseInt(Math.floor((v.getRect().width + v.getRect().x - left) / this.repulsionRange));
					startY = parseInt(Math.floor((v.getRect().y - top) / this.repulsionRange));
					finishY = parseInt(Math.floor((v.getRect().height + v.getRect().y - top) / this.repulsionRange));
					for (var i = startX; i <= finishX; i++) for (var j = startY; j <= finishY; j++) {
						this.grid[i][j].push(v);
						v.setGridCoordinates(startX, finishX, startY, finishY);
					}
				};
				FDLayout.prototype.updateGrid = function() {
					var i;
					var nodeA;
					var lNodes = this.getAllNodes();
					this.grid = this.calcGrid(this.graphManager.getRoot());
					for (i = 0; i < lNodes.length; i++) {
						nodeA = lNodes[i];
						this.addNodeToGrid(nodeA, this.graphManager.getRoot().getLeft(), this.graphManager.getRoot().getTop());
					}
				};
				FDLayout.prototype.calculateRepulsionForceOfANode = function(nodeA, processedNodeSet, gridUpdateAllowed, forceToNodeSurroundingUpdate) {
					if (this.totalIterations % FDLayoutConstants.GRID_CALCULATION_CHECK_PERIOD == 1 && gridUpdateAllowed || forceToNodeSurroundingUpdate) {
						var surrounding = /* @__PURE__ */ new Set();
						nodeA.surrounding = new Array();
						var nodeB;
						var grid = this.grid;
						for (var i = nodeA.startX - 1; i < nodeA.finishX + 2; i++) for (var j = nodeA.startY - 1; j < nodeA.finishY + 2; j++) if (!(i < 0 || j < 0 || i >= grid.length || j >= grid[0].length)) for (var k = 0; k < grid[i][j].length; k++) {
							nodeB = grid[i][j][k];
							if (nodeA.getOwner() != nodeB.getOwner() || nodeA == nodeB) continue;
							if (!processedNodeSet.has(nodeB) && !surrounding.has(nodeB)) {
								var distanceX = Math.abs(nodeA.getCenterX() - nodeB.getCenterX()) - (nodeA.getWidth() / 2 + nodeB.getWidth() / 2);
								var distanceY = Math.abs(nodeA.getCenterY() - nodeB.getCenterY()) - (nodeA.getHeight() / 2 + nodeB.getHeight() / 2);
								if (distanceX <= this.repulsionRange && distanceY <= this.repulsionRange) surrounding.add(nodeB);
							}
						}
						nodeA.surrounding = [].concat(_toConsumableArray(surrounding));
					}
					for (i = 0; i < nodeA.surrounding.length; i++) this.calcRepulsionForce(nodeA, nodeA.surrounding[i]);
				};
				FDLayout.prototype.calcRepulsionRange = function() {
					return 0;
				};
				module$71.exports = FDLayout;
			}),
			(function(module$72, exports$59, __webpack_require__) {
				"use strict";
				var LEdge = __webpack_require__(1);
				var FDLayoutConstants = __webpack_require__(4);
				function FDLayoutEdge(source, target, vEdge) {
					LEdge.call(this, source, target, vEdge);
					this.idealLength = FDLayoutConstants.DEFAULT_EDGE_LENGTH;
					this.edgeElasticity = FDLayoutConstants.DEFAULT_SPRING_STRENGTH;
				}
				FDLayoutEdge.prototype = Object.create(LEdge.prototype);
				for (var prop in LEdge) FDLayoutEdge[prop] = LEdge[prop];
				module$72.exports = FDLayoutEdge;
			}),
			(function(module$73, exports$60, __webpack_require__) {
				"use strict";
				var LNode = __webpack_require__(3);
				var FDLayoutConstants = __webpack_require__(4);
				function FDLayoutNode(gm, loc, size, vNode) {
					LNode.call(this, gm, loc, size, vNode);
					this.nodeRepulsion = FDLayoutConstants.DEFAULT_REPULSION_STRENGTH;
					this.springForceX = 0;
					this.springForceY = 0;
					this.repulsionForceX = 0;
					this.repulsionForceY = 0;
					this.gravitationForceX = 0;
					this.gravitationForceY = 0;
					this.displacementX = 0;
					this.displacementY = 0;
					this.startX = 0;
					this.finishX = 0;
					this.startY = 0;
					this.finishY = 0;
					this.surrounding = [];
				}
				FDLayoutNode.prototype = Object.create(LNode.prototype);
				for (var prop in LNode) FDLayoutNode[prop] = LNode[prop];
				FDLayoutNode.prototype.setGridCoordinates = function(_startX, _finishX, _startY, _finishY) {
					this.startX = _startX;
					this.finishX = _finishX;
					this.startY = _startY;
					this.finishY = _finishY;
				};
				module$73.exports = FDLayoutNode;
			}),
			(function(module$74, exports$61, __webpack_require__) {
				"use strict";
				function DimensionD(width, height) {
					this.width = 0;
					this.height = 0;
					if (width !== null && height !== null) {
						this.height = height;
						this.width = width;
					}
				}
				DimensionD.prototype.getWidth = function() {
					return this.width;
				};
				DimensionD.prototype.setWidth = function(width) {
					this.width = width;
				};
				DimensionD.prototype.getHeight = function() {
					return this.height;
				};
				DimensionD.prototype.setHeight = function(height) {
					this.height = height;
				};
				module$74.exports = DimensionD;
			}),
			(function(module$75, exports$62, __webpack_require__) {
				"use strict";
				var UniqueIDGeneretor = __webpack_require__(14);
				function HashMap() {
					this.map = {};
					this.keys = [];
				}
				HashMap.prototype.put = function(key, value) {
					var theId = UniqueIDGeneretor.createID(key);
					if (!this.contains(theId)) {
						this.map[theId] = value;
						this.keys.push(key);
					}
				};
				HashMap.prototype.contains = function(key) {
					UniqueIDGeneretor.createID(key);
					return this.map[key] != null;
				};
				HashMap.prototype.get = function(key) {
					var theId = UniqueIDGeneretor.createID(key);
					return this.map[theId];
				};
				HashMap.prototype.keySet = function() {
					return this.keys;
				};
				module$75.exports = HashMap;
			}),
			(function(module$76, exports$63, __webpack_require__) {
				"use strict";
				var UniqueIDGeneretor = __webpack_require__(14);
				function HashSet() {
					this.set = {};
				}
				HashSet.prototype.add = function(obj) {
					var theId = UniqueIDGeneretor.createID(obj);
					if (!this.contains(theId)) this.set[theId] = obj;
				};
				HashSet.prototype.remove = function(obj) {
					delete this.set[UniqueIDGeneretor.createID(obj)];
				};
				HashSet.prototype.clear = function() {
					this.set = {};
				};
				HashSet.prototype.contains = function(obj) {
					return this.set[UniqueIDGeneretor.createID(obj)] == obj;
				};
				HashSet.prototype.isEmpty = function() {
					return this.size() === 0;
				};
				HashSet.prototype.size = function() {
					return Object.keys(this.set).length;
				};
				HashSet.prototype.addAllTo = function(list) {
					var keys = Object.keys(this.set);
					var length = keys.length;
					for (var i = 0; i < length; i++) list.push(this.set[keys[i]]);
				};
				HashSet.prototype.size = function() {
					return Object.keys(this.set).length;
				};
				HashSet.prototype.addAll = function(list) {
					var s = list.length;
					for (var i = 0; i < s; i++) {
						var v = list[i];
						this.add(v);
					}
				};
				module$76.exports = HashSet;
			}),
			(function(module$77, exports$64, __webpack_require__) {
				"use strict";
				function Matrix() {}
				/**
				* matrix multiplication
				* array1, array2 and result are 2d arrays
				*/
				Matrix.multMat = function(array1, array2) {
					var result = [];
					for (var i = 0; i < array1.length; i++) {
						result[i] = [];
						for (var j = 0; j < array2[0].length; j++) {
							result[i][j] = 0;
							for (var k = 0; k < array1[0].length; k++) result[i][j] += array1[i][k] * array2[k][j];
						}
					}
					return result;
				};
				/**
				* matrix transpose
				* array and result are 2d arrays
				*/
				Matrix.transpose = function(array) {
					var result = [];
					for (var i = 0; i < array[0].length; i++) {
						result[i] = [];
						for (var j = 0; j < array.length; j++) result[i][j] = array[j][i];
					}
					return result;
				};
				/**
				* multiply array with constant
				* array and result are 1d arrays
				*/
				Matrix.multCons = function(array, constant) {
					var result = [];
					for (var i = 0; i < array.length; i++) result[i] = array[i] * constant;
					return result;
				};
				/**
				* substract two arrays
				* array1, array2 and result are 1d arrays
				*/
				Matrix.minusOp = function(array1, array2) {
					var result = [];
					for (var i = 0; i < array1.length; i++) result[i] = array1[i] - array2[i];
					return result;
				};
				/**
				* dot product of two arrays with same size
				* array1 and array2 are 1d arrays
				*/
				Matrix.dotProduct = function(array1, array2) {
					var product = 0;
					for (var i = 0; i < array1.length; i++) product += array1[i] * array2[i];
					return product;
				};
				/**
				* magnitude of an array
				* array is 1d array
				*/
				Matrix.mag = function(array) {
					return Math.sqrt(this.dotProduct(array, array));
				};
				/**
				* normalization of an array
				* array and result are 1d array
				*/
				Matrix.normalize = function(array) {
					var result = [];
					var magnitude = this.mag(array);
					for (var i = 0; i < array.length; i++) result[i] = array[i] / magnitude;
					return result;
				};
				/**
				* multiply an array with centering matrix
				* array and result are 1d array
				*/
				Matrix.multGamma = function(array) {
					var result = [];
					var sum = 0;
					for (var i = 0; i < array.length; i++) sum += array[i];
					sum *= -1 / array.length;
					for (var _i = 0; _i < array.length; _i++) result[_i] = sum + array[_i];
					return result;
				};
				/**
				* a special matrix multiplication
				* result = 0.5 * C * INV * C^T * array
				* array and result are 1d, C and INV are 2d arrays
				*/
				Matrix.multL = function(array, C, INV) {
					var result = [];
					var temp1 = [];
					var temp2 = [];
					for (var i = 0; i < C[0].length; i++) {
						var sum = 0;
						for (var j = 0; j < C.length; j++) sum += -.5 * C[j][i] * array[j];
						temp1[i] = sum;
					}
					for (var _i2 = 0; _i2 < INV.length; _i2++) {
						var _sum = 0;
						for (var _j = 0; _j < INV.length; _j++) _sum += INV[_i2][_j] * temp1[_j];
						temp2[_i2] = _sum;
					}
					for (var _i3 = 0; _i3 < C.length; _i3++) {
						var _sum2 = 0;
						for (var _j2 = 0; _j2 < C[0].length; _j2++) _sum2 += C[_i3][_j2] * temp2[_j2];
						result[_i3] = _sum2;
					}
					return result;
				};
				module$77.exports = Matrix;
			}),
			(function(module$78, exports$65, __webpack_require__) {
				"use strict";
				var _createClass = function() {
					function defineProperties(target, props) {
						for (var i = 0; i < props.length; i++) {
							var descriptor = props[i];
							descriptor.enumerable = descriptor.enumerable || false;
							descriptor.configurable = true;
							if ("value" in descriptor) descriptor.writable = true;
							Object.defineProperty(target, descriptor.key, descriptor);
						}
					}
					return function(Constructor, protoProps, staticProps) {
						if (protoProps) defineProperties(Constructor.prototype, protoProps);
						if (staticProps) defineProperties(Constructor, staticProps);
						return Constructor;
					};
				}();
				function _classCallCheck(instance, Constructor) {
					if (!(instance instanceof Constructor)) throw new TypeError("Cannot call a class as a function");
				}
				/**
				* A classic Quicksort algorithm with Hoare's partition
				* - Works also on LinkedList objects
				*
				* Copyright: i-Vis Research Group, Bilkent University, 2007 - present
				*/
				var LinkedList = __webpack_require__(11);
				module$78.exports = function() {
					function Quicksort(A, compareFunction) {
						_classCallCheck(this, Quicksort);
						if (compareFunction !== null || compareFunction !== void 0) this.compareFunction = this._defaultCompareFunction;
						var length = void 0;
						if (A instanceof LinkedList) length = A.size();
						else length = A.length;
						this._quicksort(A, 0, length - 1);
					}
					_createClass(Quicksort, [
						{
							key: "_quicksort",
							value: function _quicksort(A, p, r) {
								if (p < r) {
									var q = this._partition(A, p, r);
									this._quicksort(A, p, q);
									this._quicksort(A, q + 1, r);
								}
							}
						},
						{
							key: "_partition",
							value: function _partition(A, p, r) {
								var x = this._get(A, p);
								var i = p;
								var j = r;
								while (true) {
									while (this.compareFunction(x, this._get(A, j))) j--;
									while (this.compareFunction(this._get(A, i), x)) i++;
									if (i < j) {
										this._swap(A, i, j);
										i++;
										j--;
									} else return j;
								}
							}
						},
						{
							key: "_get",
							value: function _get(object, index) {
								if (object instanceof LinkedList) return object.get_object_at(index);
								else return object[index];
							}
						},
						{
							key: "_set",
							value: function _set(object, index, value) {
								if (object instanceof LinkedList) object.set_object_at(index, value);
								else object[index] = value;
							}
						},
						{
							key: "_swap",
							value: function _swap(A, i, j) {
								var temp = this._get(A, i);
								this._set(A, i, this._get(A, j));
								this._set(A, j, temp);
							}
						},
						{
							key: "_defaultCompareFunction",
							value: function _defaultCompareFunction(a, b) {
								return b > a;
							}
						}
					]);
					return Quicksort;
				}();
			}),
			(function(module$79, exports$66, __webpack_require__) {
				"use strict";
				function SVD() {}
				SVD.svd = function(A) {
					this.U = null;
					this.V = null;
					this.s = null;
					this.m = 0;
					this.n = 0;
					this.m = A.length;
					this.n = A[0].length;
					var nu = Math.min(this.m, this.n);
					this.s = function(s) {
						var a = [];
						while (s-- > 0) a.push(0);
						return a;
					}(Math.min(this.m + 1, this.n));
					this.U = function(dims) {
						return function allocate(dims) {
							if (dims.length == 0) return 0;
							else {
								var array = [];
								for (var i = 0; i < dims[0]; i++) array.push(allocate(dims.slice(1)));
								return array;
							}
						}(dims);
					}([this.m, nu]);
					this.V = function(dims) {
						return function allocate(dims) {
							if (dims.length == 0) return 0;
							else {
								var array = [];
								for (var i = 0; i < dims[0]; i++) array.push(allocate(dims.slice(1)));
								return array;
							}
						}(dims);
					}([this.n, this.n]);
					var e = function(s) {
						var a = [];
						while (s-- > 0) a.push(0);
						return a;
					}(this.n);
					var work = function(s) {
						var a = [];
						while (s-- > 0) a.push(0);
						return a;
					}(this.m);
					var wantu = true;
					var wantv = true;
					var nct = Math.min(this.m - 1, this.n);
					var nrt = Math.max(0, Math.min(this.n - 2, this.m));
					for (var k = 0; k < Math.max(nct, nrt); k++) {
						if (k < nct) {
							this.s[k] = 0;
							for (var i = k; i < this.m; i++) this.s[k] = SVD.hypot(this.s[k], A[i][k]);
							if (this.s[k] !== 0) {
								if (A[k][k] < 0) this.s[k] = -this.s[k];
								for (var _i = k; _i < this.m; _i++) A[_i][k] /= this.s[k];
								A[k][k] += 1;
							}
							this.s[k] = -this.s[k];
						}
						for (var j = k + 1; j < this.n; j++) {
							if (function(lhs, rhs) {
								return lhs && rhs;
							}(k < nct, this.s[k] !== 0)) {
								var t = 0;
								for (var _i2 = k; _i2 < this.m; _i2++) t += A[_i2][k] * A[_i2][j];
								t = -t / A[k][k];
								for (var _i3 = k; _i3 < this.m; _i3++) A[_i3][j] += t * A[_i3][k];
							}
							e[j] = A[k][j];
						}
						if (function(lhs, rhs) {
							return lhs && rhs;
						}(wantu, k < nct)) for (var _i4 = k; _i4 < this.m; _i4++) this.U[_i4][k] = A[_i4][k];
						if (k < nrt) {
							e[k] = 0;
							for (var _i5 = k + 1; _i5 < this.n; _i5++) e[k] = SVD.hypot(e[k], e[_i5]);
							if (e[k] !== 0) {
								if (e[k + 1] < 0) e[k] = -e[k];
								for (var _i6 = k + 1; _i6 < this.n; _i6++) e[_i6] /= e[k];
								e[k + 1] += 1;
							}
							e[k] = -e[k];
							if (function(lhs, rhs) {
								return lhs && rhs;
							}(k + 1 < this.m, e[k] !== 0)) {
								for (var _i7 = k + 1; _i7 < this.m; _i7++) work[_i7] = 0;
								for (var _j = k + 1; _j < this.n; _j++) for (var _i8 = k + 1; _i8 < this.m; _i8++) work[_i8] += e[_j] * A[_i8][_j];
								for (var _j2 = k + 1; _j2 < this.n; _j2++) {
									var _t = -e[_j2] / e[k + 1];
									for (var _i9 = k + 1; _i9 < this.m; _i9++) A[_i9][_j2] += _t * work[_i9];
								}
							}
							if (wantv) for (var _i10 = k + 1; _i10 < this.n; _i10++) this.V[_i10][k] = e[_i10];
						}
					}
					var p = Math.min(this.n, this.m + 1);
					if (nct < this.n) this.s[nct] = A[nct][nct];
					if (this.m < p) this.s[p - 1] = 0;
					if (nrt + 1 < p) e[nrt] = A[nrt][p - 1];
					e[p - 1] = 0;
					if (wantu) {
						for (var _j3 = nct; _j3 < nu; _j3++) {
							for (var _i11 = 0; _i11 < this.m; _i11++) this.U[_i11][_j3] = 0;
							this.U[_j3][_j3] = 1;
						}
						for (var _k = nct - 1; _k >= 0; _k--) if (this.s[_k] !== 0) {
							for (var _j4 = _k + 1; _j4 < nu; _j4++) {
								var _t2 = 0;
								for (var _i12 = _k; _i12 < this.m; _i12++) _t2 += this.U[_i12][_k] * this.U[_i12][_j4];
								_t2 = -_t2 / this.U[_k][_k];
								for (var _i13 = _k; _i13 < this.m; _i13++) this.U[_i13][_j4] += _t2 * this.U[_i13][_k];
							}
							for (var _i14 = _k; _i14 < this.m; _i14++) this.U[_i14][_k] = -this.U[_i14][_k];
							this.U[_k][_k] = 1 + this.U[_k][_k];
							for (var _i15 = 0; _i15 < _k - 1; _i15++) this.U[_i15][_k] = 0;
						} else {
							for (var _i16 = 0; _i16 < this.m; _i16++) this.U[_i16][_k] = 0;
							this.U[_k][_k] = 1;
						}
					}
					if (wantv) for (var _k2 = this.n - 1; _k2 >= 0; _k2--) {
						if (function(lhs, rhs) {
							return lhs && rhs;
						}(_k2 < nrt, e[_k2] !== 0)) for (var _j5 = _k2 + 1; _j5 < nu; _j5++) {
							var _t3 = 0;
							for (var _i17 = _k2 + 1; _i17 < this.n; _i17++) _t3 += this.V[_i17][_k2] * this.V[_i17][_j5];
							_t3 = -_t3 / this.V[_k2 + 1][_k2];
							for (var _i18 = _k2 + 1; _i18 < this.n; _i18++) this.V[_i18][_j5] += _t3 * this.V[_i18][_k2];
						}
						for (var _i19 = 0; _i19 < this.n; _i19++) this.V[_i19][_k2] = 0;
						this.V[_k2][_k2] = 1;
					}
					var pp = p - 1;
					var iter = 0;
					var eps = Math.pow(2, -52);
					var tiny = Math.pow(2, -966);
					while (p > 0) {
						var _k3 = void 0;
						var kase = void 0;
						for (_k3 = p - 2; _k3 >= -1; _k3--) {
							if (_k3 === -1) break;
							if (Math.abs(e[_k3]) <= tiny + eps * (Math.abs(this.s[_k3]) + Math.abs(this.s[_k3 + 1]))) {
								e[_k3] = 0;
								break;
							}
						}
						if (_k3 === p - 2) kase = 4;
						else {
							var ks = void 0;
							for (ks = p - 1; ks >= _k3; ks--) {
								if (ks === _k3) break;
								var _t4 = (ks !== p ? Math.abs(e[ks]) : 0) + (ks !== _k3 + 1 ? Math.abs(e[ks - 1]) : 0);
								if (Math.abs(this.s[ks]) <= tiny + eps * _t4) {
									this.s[ks] = 0;
									break;
								}
							}
							if (ks === _k3) kase = 3;
							else if (ks === p - 1) kase = 1;
							else {
								kase = 2;
								_k3 = ks;
							}
						}
						_k3++;
						switch (kase) {
							case 1:
								var f = e[p - 2];
								e[p - 2] = 0;
								for (var _j6 = p - 2; _j6 >= _k3; _j6--) {
									var _t5 = SVD.hypot(this.s[_j6], f);
									var cs = this.s[_j6] / _t5;
									var sn = f / _t5;
									this.s[_j6] = _t5;
									if (_j6 !== _k3) {
										f = -sn * e[_j6 - 1];
										e[_j6 - 1] = cs * e[_j6 - 1];
									}
									if (wantv) for (var _i20 = 0; _i20 < this.n; _i20++) {
										_t5 = cs * this.V[_i20][_j6] + sn * this.V[_i20][p - 1];
										this.V[_i20][p - 1] = -sn * this.V[_i20][_j6] + cs * this.V[_i20][p - 1];
										this.V[_i20][_j6] = _t5;
									}
								}
								break;
							case 2:
								var _f = e[_k3 - 1];
								e[_k3 - 1] = 0;
								for (var _j7 = _k3; _j7 < p; _j7++) {
									var _t6 = SVD.hypot(this.s[_j7], _f);
									var _cs = this.s[_j7] / _t6;
									var _sn = _f / _t6;
									this.s[_j7] = _t6;
									_f = -_sn * e[_j7];
									e[_j7] = _cs * e[_j7];
									if (wantu) for (var _i21 = 0; _i21 < this.m; _i21++) {
										_t6 = _cs * this.U[_i21][_j7] + _sn * this.U[_i21][_k3 - 1];
										this.U[_i21][_k3 - 1] = -_sn * this.U[_i21][_j7] + _cs * this.U[_i21][_k3 - 1];
										this.U[_i21][_j7] = _t6;
									}
								}
								break;
							case 3:
								var scale = Math.max(Math.max(Math.max(Math.max(Math.abs(this.s[p - 1]), Math.abs(this.s[p - 2])), Math.abs(e[p - 2])), Math.abs(this.s[_k3])), Math.abs(e[_k3]));
								var sp = this.s[p - 1] / scale;
								var spm1 = this.s[p - 2] / scale;
								var epm1 = e[p - 2] / scale;
								var sk = this.s[_k3] / scale;
								var ek = e[_k3] / scale;
								var b = ((spm1 + sp) * (spm1 - sp) + epm1 * epm1) / 2;
								var c = sp * epm1 * (sp * epm1);
								var shift = 0;
								if (function(lhs, rhs) {
									return lhs || rhs;
								}(b !== 0, c !== 0)) {
									shift = Math.sqrt(b * b + c);
									if (b < 0) shift = -shift;
									shift = c / (b + shift);
								}
								var _f2 = (sk + sp) * (sk - sp) + shift;
								var g = sk * ek;
								for (var _j8 = _k3; _j8 < p - 1; _j8++) {
									var _t7 = SVD.hypot(_f2, g);
									var _cs2 = _f2 / _t7;
									var _sn2 = g / _t7;
									if (_j8 !== _k3) e[_j8 - 1] = _t7;
									_f2 = _cs2 * this.s[_j8] + _sn2 * e[_j8];
									e[_j8] = _cs2 * e[_j8] - _sn2 * this.s[_j8];
									g = _sn2 * this.s[_j8 + 1];
									this.s[_j8 + 1] = _cs2 * this.s[_j8 + 1];
									if (wantv) for (var _i22 = 0; _i22 < this.n; _i22++) {
										_t7 = _cs2 * this.V[_i22][_j8] + _sn2 * this.V[_i22][_j8 + 1];
										this.V[_i22][_j8 + 1] = -_sn2 * this.V[_i22][_j8] + _cs2 * this.V[_i22][_j8 + 1];
										this.V[_i22][_j8] = _t7;
									}
									_t7 = SVD.hypot(_f2, g);
									_cs2 = _f2 / _t7;
									_sn2 = g / _t7;
									this.s[_j8] = _t7;
									_f2 = _cs2 * e[_j8] + _sn2 * this.s[_j8 + 1];
									this.s[_j8 + 1] = -_sn2 * e[_j8] + _cs2 * this.s[_j8 + 1];
									g = _sn2 * e[_j8 + 1];
									e[_j8 + 1] = _cs2 * e[_j8 + 1];
									if (wantu && _j8 < this.m - 1) for (var _i23 = 0; _i23 < this.m; _i23++) {
										_t7 = _cs2 * this.U[_i23][_j8] + _sn2 * this.U[_i23][_j8 + 1];
										this.U[_i23][_j8 + 1] = -_sn2 * this.U[_i23][_j8] + _cs2 * this.U[_i23][_j8 + 1];
										this.U[_i23][_j8] = _t7;
									}
								}
								e[p - 2] = _f2;
								iter = iter + 1;
								break;
							case 4:
								if (this.s[_k3] <= 0) {
									this.s[_k3] = this.s[_k3] < 0 ? -this.s[_k3] : 0;
									if (wantv) for (var _i24 = 0; _i24 <= pp; _i24++) this.V[_i24][_k3] = -this.V[_i24][_k3];
								}
								while (_k3 < pp) {
									if (this.s[_k3] >= this.s[_k3 + 1]) break;
									var _t8 = this.s[_k3];
									this.s[_k3] = this.s[_k3 + 1];
									this.s[_k3 + 1] = _t8;
									if (wantv && _k3 < this.n - 1) for (var _i25 = 0; _i25 < this.n; _i25++) {
										_t8 = this.V[_i25][_k3 + 1];
										this.V[_i25][_k3 + 1] = this.V[_i25][_k3];
										this.V[_i25][_k3] = _t8;
									}
									if (wantu && _k3 < this.m - 1) for (var _i26 = 0; _i26 < this.m; _i26++) {
										_t8 = this.U[_i26][_k3 + 1];
										this.U[_i26][_k3 + 1] = this.U[_i26][_k3];
										this.U[_i26][_k3] = _t8;
									}
									_k3++;
								}
								iter = 0;
								p--;
								break;
						}
					}
					return {
						U: this.U,
						V: this.V,
						S: this.s
					};
				};
				SVD.hypot = function(a, b) {
					var r = void 0;
					if (Math.abs(a) > Math.abs(b)) {
						r = b / a;
						r = Math.abs(a) * Math.sqrt(1 + r * r);
					} else if (b != 0) {
						r = a / b;
						r = Math.abs(b) * Math.sqrt(1 + r * r);
					} else r = 0;
					return r;
				};
				module$79.exports = SVD;
			}),
			(function(module$80, exports$67, __webpack_require__) {
				"use strict";
				var _createClass = function() {
					function defineProperties(target, props) {
						for (var i = 0; i < props.length; i++) {
							var descriptor = props[i];
							descriptor.enumerable = descriptor.enumerable || false;
							descriptor.configurable = true;
							if ("value" in descriptor) descriptor.writable = true;
							Object.defineProperty(target, descriptor.key, descriptor);
						}
					}
					return function(Constructor, protoProps, staticProps) {
						if (protoProps) defineProperties(Constructor.prototype, protoProps);
						if (staticProps) defineProperties(Constructor, staticProps);
						return Constructor;
					};
				}();
				function _classCallCheck(instance, Constructor) {
					if (!(instance instanceof Constructor)) throw new TypeError("Cannot call a class as a function");
				}
				module$80.exports = function() {
					function NeedlemanWunsch(sequence1, sequence2) {
						var match_score = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : 1;
						var mismatch_penalty = arguments.length > 3 && arguments[3] !== void 0 ? arguments[3] : -1;
						var gap_penalty = arguments.length > 4 && arguments[4] !== void 0 ? arguments[4] : -1;
						_classCallCheck(this, NeedlemanWunsch);
						this.sequence1 = sequence1;
						this.sequence2 = sequence2;
						this.match_score = match_score;
						this.mismatch_penalty = mismatch_penalty;
						this.gap_penalty = gap_penalty;
						this.iMax = sequence1.length + 1;
						this.jMax = sequence2.length + 1;
						this.grid = new Array(this.iMax);
						for (var i = 0; i < this.iMax; i++) {
							this.grid[i] = new Array(this.jMax);
							for (var j = 0; j < this.jMax; j++) this.grid[i][j] = 0;
						}
						this.tracebackGrid = new Array(this.iMax);
						for (var _i = 0; _i < this.iMax; _i++) {
							this.tracebackGrid[_i] = new Array(this.jMax);
							for (var _j = 0; _j < this.jMax; _j++) this.tracebackGrid[_i][_j] = [
								null,
								null,
								null
							];
						}
						this.alignments = [];
						this.score = -1;
						this.computeGrids();
					}
					_createClass(NeedlemanWunsch, [
						{
							key: "getScore",
							value: function getScore() {
								return this.score;
							}
						},
						{
							key: "getAlignments",
							value: function getAlignments() {
								return this.alignments;
							}
						},
						{
							key: "computeGrids",
							value: function computeGrids() {
								for (var j = 1; j < this.jMax; j++) {
									this.grid[0][j] = this.grid[0][j - 1] + this.gap_penalty;
									this.tracebackGrid[0][j] = [
										false,
										false,
										true
									];
								}
								for (var i = 1; i < this.iMax; i++) {
									this.grid[i][0] = this.grid[i - 1][0] + this.gap_penalty;
									this.tracebackGrid[i][0] = [
										false,
										true,
										false
									];
								}
								for (var _i2 = 1; _i2 < this.iMax; _i2++) for (var _j2 = 1; _j2 < this.jMax; _j2++) {
									var diag = void 0;
									if (this.sequence1[_i2 - 1] === this.sequence2[_j2 - 1]) diag = this.grid[_i2 - 1][_j2 - 1] + this.match_score;
									else diag = this.grid[_i2 - 1][_j2 - 1] + this.mismatch_penalty;
									var up = this.grid[_i2 - 1][_j2] + this.gap_penalty;
									var left = this.grid[_i2][_j2 - 1] + this.gap_penalty;
									var maxOf = [
										diag,
										up,
										left
									];
									var indices = this.arrayAllMaxIndexes(maxOf);
									this.grid[_i2][_j2] = maxOf[indices[0]];
									this.tracebackGrid[_i2][_j2] = [
										indices.includes(0),
										indices.includes(1),
										indices.includes(2)
									];
								}
								this.score = this.grid[this.iMax - 1][this.jMax - 1];
							}
						},
						{
							key: "alignmentTraceback",
							value: function alignmentTraceback() {
								var inProcessAlignments = [];
								inProcessAlignments.push({
									pos: [this.sequence1.length, this.sequence2.length],
									seq1: "",
									seq2: ""
								});
								while (inProcessAlignments[0]) {
									var current = inProcessAlignments[0];
									var directions = this.tracebackGrid[current.pos[0]][current.pos[1]];
									if (directions[0]) inProcessAlignments.push({
										pos: [current.pos[0] - 1, current.pos[1] - 1],
										seq1: this.sequence1[current.pos[0] - 1] + current.seq1,
										seq2: this.sequence2[current.pos[1] - 1] + current.seq2
									});
									if (directions[1]) inProcessAlignments.push({
										pos: [current.pos[0] - 1, current.pos[1]],
										seq1: this.sequence1[current.pos[0] - 1] + current.seq1,
										seq2: "-" + current.seq2
									});
									if (directions[2]) inProcessAlignments.push({
										pos: [current.pos[0], current.pos[1] - 1],
										seq1: "-" + current.seq1,
										seq2: this.sequence2[current.pos[1] - 1] + current.seq2
									});
									if (current.pos[0] === 0 && current.pos[1] === 0) this.alignments.push({
										sequence1: current.seq1,
										sequence2: current.seq2
									});
									inProcessAlignments.shift();
								}
								return this.alignments;
							}
						},
						{
							key: "getAllIndexes",
							value: function getAllIndexes(arr, val) {
								var indexes = [], i = -1;
								while ((i = arr.indexOf(val, i + 1)) !== -1) indexes.push(i);
								return indexes;
							}
						},
						{
							key: "arrayAllMaxIndexes",
							value: function arrayAllMaxIndexes(array) {
								return this.getAllIndexes(array, Math.max.apply(null, array));
							}
						}
					]);
					return NeedlemanWunsch;
				}();
			}),
			(function(module$81, exports$68, __webpack_require__) {
				"use strict";
				var layoutBase = function layoutBase() {};
				layoutBase.FDLayout = __webpack_require__(18);
				layoutBase.FDLayoutConstants = __webpack_require__(4);
				layoutBase.FDLayoutEdge = __webpack_require__(19);
				layoutBase.FDLayoutNode = __webpack_require__(20);
				layoutBase.DimensionD = __webpack_require__(21);
				layoutBase.HashMap = __webpack_require__(22);
				layoutBase.HashSet = __webpack_require__(23);
				layoutBase.IGeometry = __webpack_require__(8);
				layoutBase.IMath = __webpack_require__(9);
				layoutBase.Integer = __webpack_require__(10);
				layoutBase.Point = __webpack_require__(12);
				layoutBase.PointD = __webpack_require__(5);
				layoutBase.RandomSeed = __webpack_require__(16);
				layoutBase.RectangleD = __webpack_require__(13);
				layoutBase.Transform = __webpack_require__(17);
				layoutBase.UniqueIDGeneretor = __webpack_require__(14);
				layoutBase.Quicksort = __webpack_require__(25);
				layoutBase.LinkedList = __webpack_require__(11);
				layoutBase.LGraphObject = __webpack_require__(2);
				layoutBase.LGraph = __webpack_require__(6);
				layoutBase.LEdge = __webpack_require__(1);
				layoutBase.LGraphManager = __webpack_require__(7);
				layoutBase.LNode = __webpack_require__(3);
				layoutBase.Layout = __webpack_require__(15);
				layoutBase.LayoutConstants = __webpack_require__(0);
				layoutBase.NeedlemanWunsch = __webpack_require__(27);
				layoutBase.Matrix = __webpack_require__(24);
				layoutBase.SVD = __webpack_require__(26);
				module$81.exports = layoutBase;
			}),
			(function(module$82, exports$69, __webpack_require__) {
				"use strict";
				function Emitter() {
					this.listeners = [];
				}
				var p = Emitter.prototype;
				p.addListener = function(event, callback) {
					this.listeners.push({
						event,
						callback
					});
				};
				p.removeListener = function(event, callback) {
					for (var i = this.listeners.length; i >= 0; i--) {
						var l = this.listeners[i];
						if (l.event === event && l.callback === callback) this.listeners.splice(i, 1);
					}
				};
				p.emit = function(event, data) {
					for (var i = 0; i < this.listeners.length; i++) {
						var l = this.listeners[i];
						if (event === l.event) l.callback(data);
					}
				};
				module$82.exports = Emitter;
			})
		]);
	});
}));
//#endregion
//#region node_modules/cytoscape-fcose/node_modules/cose-base/cose-base.js
var require_cose_base$1 = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	(function webpackUniversalModuleDefinition(root, factory) {
		if (typeof exports === "object" && typeof module === "object") module.exports = factory(require_layout_base$1());
		else if (typeof define === "function" && define.amd) define(["layout-base"], factory);
		else if (typeof exports === "object") exports["coseBase"] = factory(require_layout_base$1());
		else root["coseBase"] = factory(root["layoutBase"]);
	})(exports, function(__WEBPACK_EXTERNAL_MODULE__551__) {
		return (() => {
			"use strict";
			var __webpack_modules__ = {
				45: ((module$41, __unused_webpack_exports, __webpack_require__) => {
					var coseBase = {};
					coseBase.layoutBase = __webpack_require__(551);
					coseBase.CoSEConstants = __webpack_require__(806);
					coseBase.CoSEEdge = __webpack_require__(767);
					coseBase.CoSEGraph = __webpack_require__(880);
					coseBase.CoSEGraphManager = __webpack_require__(578);
					coseBase.CoSELayout = __webpack_require__(765);
					coseBase.CoSENode = __webpack_require__(991);
					coseBase.ConstraintHandler = __webpack_require__(902);
					module$41.exports = coseBase;
				}),
				806: ((module$42, __unused_webpack_exports, __webpack_require__) => {
					var FDLayoutConstants = __webpack_require__(551).FDLayoutConstants;
					function CoSEConstants() {}
					for (var prop in FDLayoutConstants) CoSEConstants[prop] = FDLayoutConstants[prop];
					CoSEConstants.DEFAULT_USE_MULTI_LEVEL_SCALING = false;
					CoSEConstants.DEFAULT_RADIAL_SEPARATION = FDLayoutConstants.DEFAULT_EDGE_LENGTH;
					CoSEConstants.DEFAULT_COMPONENT_SEPERATION = 60;
					CoSEConstants.TILE = true;
					CoSEConstants.TILING_PADDING_VERTICAL = 10;
					CoSEConstants.TILING_PADDING_HORIZONTAL = 10;
					CoSEConstants.TRANSFORM_ON_CONSTRAINT_HANDLING = true;
					CoSEConstants.ENFORCE_CONSTRAINTS = true;
					CoSEConstants.APPLY_LAYOUT = true;
					CoSEConstants.RELAX_MOVEMENT_ON_CONSTRAINTS = true;
					CoSEConstants.TREE_REDUCTION_ON_INCREMENTAL = true;
					CoSEConstants.PURE_INCREMENTAL = CoSEConstants.DEFAULT_INCREMENTAL;
					module$42.exports = CoSEConstants;
				}),
				767: ((module$43, __unused_webpack_exports, __webpack_require__) => {
					var FDLayoutEdge = __webpack_require__(551).FDLayoutEdge;
					function CoSEEdge(source, target, vEdge) {
						FDLayoutEdge.call(this, source, target, vEdge);
					}
					CoSEEdge.prototype = Object.create(FDLayoutEdge.prototype);
					for (var prop in FDLayoutEdge) CoSEEdge[prop] = FDLayoutEdge[prop];
					module$43.exports = CoSEEdge;
				}),
				880: ((module$44, __unused_webpack_exports, __webpack_require__) => {
					var LGraph = __webpack_require__(551).LGraph;
					function CoSEGraph(parent, graphMgr, vGraph) {
						LGraph.call(this, parent, graphMgr, vGraph);
					}
					CoSEGraph.prototype = Object.create(LGraph.prototype);
					for (var prop in LGraph) CoSEGraph[prop] = LGraph[prop];
					module$44.exports = CoSEGraph;
				}),
				578: ((module$45, __unused_webpack_exports, __webpack_require__) => {
					var LGraphManager = __webpack_require__(551).LGraphManager;
					function CoSEGraphManager(layout) {
						LGraphManager.call(this, layout);
					}
					CoSEGraphManager.prototype = Object.create(LGraphManager.prototype);
					for (var prop in LGraphManager) CoSEGraphManager[prop] = LGraphManager[prop];
					module$45.exports = CoSEGraphManager;
				}),
				765: ((module$46, __unused_webpack_exports, __webpack_require__) => {
					var FDLayout = __webpack_require__(551).FDLayout;
					var CoSEGraphManager = __webpack_require__(578);
					var CoSEGraph = __webpack_require__(880);
					var CoSENode = __webpack_require__(991);
					var CoSEEdge = __webpack_require__(767);
					var CoSEConstants = __webpack_require__(806);
					var ConstraintHandler = __webpack_require__(902);
					var FDLayoutConstants = __webpack_require__(551).FDLayoutConstants;
					var LayoutConstants = __webpack_require__(551).LayoutConstants;
					var Point = __webpack_require__(551).Point;
					var PointD = __webpack_require__(551).PointD;
					var DimensionD = __webpack_require__(551).DimensionD;
					var Layout = __webpack_require__(551).Layout;
					var Integer = __webpack_require__(551).Integer;
					var IGeometry = __webpack_require__(551).IGeometry;
					var LGraph = __webpack_require__(551).LGraph;
					var Transform = __webpack_require__(551).Transform;
					var LinkedList = __webpack_require__(551).LinkedList;
					function CoSELayout() {
						FDLayout.call(this);
						this.toBeTiled = {};
						this.constraints = {};
					}
					CoSELayout.prototype = Object.create(FDLayout.prototype);
					for (var prop in FDLayout) CoSELayout[prop] = FDLayout[prop];
					CoSELayout.prototype.newGraphManager = function() {
						var gm = new CoSEGraphManager(this);
						this.graphManager = gm;
						return gm;
					};
					CoSELayout.prototype.newGraph = function(vGraph) {
						return new CoSEGraph(null, this.graphManager, vGraph);
					};
					CoSELayout.prototype.newNode = function(vNode) {
						return new CoSENode(this.graphManager, vNode);
					};
					CoSELayout.prototype.newEdge = function(vEdge) {
						return new CoSEEdge(null, null, vEdge);
					};
					CoSELayout.prototype.initParameters = function() {
						FDLayout.prototype.initParameters.call(this, arguments);
						if (!this.isSubLayout) {
							if (CoSEConstants.DEFAULT_EDGE_LENGTH < 10) this.idealEdgeLength = 10;
							else this.idealEdgeLength = CoSEConstants.DEFAULT_EDGE_LENGTH;
							this.useSmartIdealEdgeLengthCalculation = CoSEConstants.DEFAULT_USE_SMART_IDEAL_EDGE_LENGTH_CALCULATION;
							this.gravityConstant = FDLayoutConstants.DEFAULT_GRAVITY_STRENGTH;
							this.compoundGravityConstant = FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_STRENGTH;
							this.gravityRangeFactor = FDLayoutConstants.DEFAULT_GRAVITY_RANGE_FACTOR;
							this.compoundGravityRangeFactor = FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_RANGE_FACTOR;
							this.prunedNodesAll = [];
							this.growTreeIterations = 0;
							this.afterGrowthIterations = 0;
							this.isTreeGrowing = false;
							this.isGrowthFinished = false;
						}
					};
					CoSELayout.prototype.initSpringEmbedder = function() {
						FDLayout.prototype.initSpringEmbedder.call(this);
						this.coolingCycle = 0;
						this.maxCoolingCycle = this.maxIterations / FDLayoutConstants.CONVERGENCE_CHECK_PERIOD;
						this.finalTemperature = .04;
						this.coolingAdjuster = 1;
					};
					CoSELayout.prototype.layout = function() {
						if (LayoutConstants.DEFAULT_CREATE_BENDS_AS_NEEDED) {
							this.createBendpoints();
							this.graphManager.resetAllEdges();
						}
						this.level = 0;
						return this.classicLayout();
					};
					CoSELayout.prototype.classicLayout = function() {
						this.nodesWithGravity = this.calculateNodesToApplyGravitationTo();
						this.graphManager.setAllNodesToApplyGravitation(this.nodesWithGravity);
						this.calcNoOfChildrenForAllNodes();
						this.graphManager.calcLowestCommonAncestors();
						this.graphManager.calcInclusionTreeDepths();
						this.graphManager.getRoot().calcEstimatedSize();
						this.calcIdealEdgeLengths();
						if (!this.incremental) {
							var forest = this.getFlatForest();
							if (forest.length > 0) this.positionNodesRadially(forest);
							else {
								this.reduceTrees();
								this.graphManager.resetAllNodesToApplyGravitation();
								var allNodes = new Set(this.getAllNodes());
								var intersection = this.nodesWithGravity.filter(function(x) {
									return allNodes.has(x);
								});
								this.graphManager.setAllNodesToApplyGravitation(intersection);
								this.positionNodesRandomly();
							}
						} else if (CoSEConstants.TREE_REDUCTION_ON_INCREMENTAL) {
							this.reduceTrees();
							this.graphManager.resetAllNodesToApplyGravitation();
							var allNodes = new Set(this.getAllNodes());
							var intersection = this.nodesWithGravity.filter(function(x) {
								return allNodes.has(x);
							});
							this.graphManager.setAllNodesToApplyGravitation(intersection);
						}
						if (Object.keys(this.constraints).length > 0) {
							ConstraintHandler.handleConstraints(this);
							this.initConstraintVariables();
						}
						this.initSpringEmbedder();
						if (CoSEConstants.APPLY_LAYOUT) this.runSpringEmbedder();
						return true;
					};
					CoSELayout.prototype.tick = function() {
						this.totalIterations++;
						if (this.totalIterations === this.maxIterations && !this.isTreeGrowing && !this.isGrowthFinished) if (this.prunedNodesAll.length > 0) this.isTreeGrowing = true;
						else return true;
						if (this.totalIterations % FDLayoutConstants.CONVERGENCE_CHECK_PERIOD == 0 && !this.isTreeGrowing && !this.isGrowthFinished) {
							if (this.isConverged()) if (this.prunedNodesAll.length > 0) this.isTreeGrowing = true;
							else return true;
							this.coolingCycle++;
							if (this.layoutQuality == 0) this.coolingAdjuster = this.coolingCycle;
							else if (this.layoutQuality == 1) this.coolingAdjuster = this.coolingCycle / 3;
							this.coolingFactor = Math.max(this.initialCoolingFactor - Math.pow(this.coolingCycle, Math.log(100 * (this.initialCoolingFactor - this.finalTemperature)) / Math.log(this.maxCoolingCycle)) / 100 * this.coolingAdjuster, this.finalTemperature);
							this.animationPeriod = Math.ceil(this.initialAnimationPeriod * Math.sqrt(this.coolingFactor));
						}
						if (this.isTreeGrowing) {
							if (this.growTreeIterations % 10 == 0) if (this.prunedNodesAll.length > 0) {
								this.graphManager.updateBounds();
								this.updateGrid();
								this.growTree(this.prunedNodesAll);
								this.graphManager.resetAllNodesToApplyGravitation();
								var allNodes = new Set(this.getAllNodes());
								var intersection = this.nodesWithGravity.filter(function(x) {
									return allNodes.has(x);
								});
								this.graphManager.setAllNodesToApplyGravitation(intersection);
								this.graphManager.updateBounds();
								this.updateGrid();
								if (CoSEConstants.PURE_INCREMENTAL) this.coolingFactor = FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL / 2;
								else this.coolingFactor = FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL;
							} else {
								this.isTreeGrowing = false;
								this.isGrowthFinished = true;
							}
							this.growTreeIterations++;
						}
						if (this.isGrowthFinished) {
							if (this.isConverged()) return true;
							if (this.afterGrowthIterations % 10 == 0) {
								this.graphManager.updateBounds();
								this.updateGrid();
							}
							if (CoSEConstants.PURE_INCREMENTAL) this.coolingFactor = FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL / 2 * ((100 - this.afterGrowthIterations) / 100);
							else this.coolingFactor = FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL * ((100 - this.afterGrowthIterations) / 100);
							this.afterGrowthIterations++;
						}
						var gridUpdateAllowed = !this.isTreeGrowing && !this.isGrowthFinished;
						var forceToNodeSurroundingUpdate = this.growTreeIterations % 10 == 1 && this.isTreeGrowing || this.afterGrowthIterations % 10 == 1 && this.isGrowthFinished;
						this.totalDisplacement = 0;
						this.graphManager.updateBounds();
						this.calcSpringForces();
						this.calcRepulsionForces(gridUpdateAllowed, forceToNodeSurroundingUpdate);
						this.calcGravitationalForces();
						this.moveNodes();
						this.animate();
						return false;
					};
					CoSELayout.prototype.getPositionsData = function() {
						var allNodes = this.graphManager.getAllNodes();
						var pData = {};
						for (var i = 0; i < allNodes.length; i++) {
							var rect = allNodes[i].rect;
							var id = allNodes[i].id;
							pData[id] = {
								id,
								x: rect.getCenterX(),
								y: rect.getCenterY(),
								w: rect.width,
								h: rect.height
							};
						}
						return pData;
					};
					CoSELayout.prototype.runSpringEmbedder = function() {
						this.initialAnimationPeriod = 25;
						this.animationPeriod = this.initialAnimationPeriod;
						var layoutEnded = false;
						if (FDLayoutConstants.ANIMATE === "during") this.emit("layoutstarted");
						else {
							while (!layoutEnded) layoutEnded = this.tick();
							this.graphManager.updateBounds();
						}
					};
					CoSELayout.prototype.moveNodes = function() {
						var lNodes = this.getAllNodes();
						var node;
						for (var i = 0; i < lNodes.length; i++) {
							node = lNodes[i];
							node.calculateDisplacement();
						}
						if (Object.keys(this.constraints).length > 0) this.updateDisplacements();
						for (var i = 0; i < lNodes.length; i++) {
							node = lNodes[i];
							node.move();
						}
					};
					CoSELayout.prototype.initConstraintVariables = function() {
						var self = this;
						this.idToNodeMap = /* @__PURE__ */ new Map();
						this.fixedNodeSet = /* @__PURE__ */ new Set();
						var allNodes = this.graphManager.getAllNodes();
						for (var i = 0; i < allNodes.length; i++) {
							var node = allNodes[i];
							this.idToNodeMap.set(node.id, node);
						}
						var calculateCompoundWeight = function calculateCompoundWeight(compoundNode) {
							var nodes = compoundNode.getChild().getNodes();
							var node;
							var fixedNodeWeight = 0;
							for (var i = 0; i < nodes.length; i++) {
								node = nodes[i];
								if (node.getChild() == null) {
									if (self.fixedNodeSet.has(node.id)) fixedNodeWeight += 100;
								} else fixedNodeWeight += calculateCompoundWeight(node);
							}
							return fixedNodeWeight;
						};
						if (this.constraints.fixedNodeConstraint) {
							this.constraints.fixedNodeConstraint.forEach(function(nodeData) {
								self.fixedNodeSet.add(nodeData.nodeId);
							});
							var allNodes = this.graphManager.getAllNodes();
							var node;
							for (var i = 0; i < allNodes.length; i++) {
								node = allNodes[i];
								if (node.getChild() != null) {
									var fixedNodeWeight = calculateCompoundWeight(node);
									if (fixedNodeWeight > 0) node.fixedNodeWeight = fixedNodeWeight;
								}
							}
						}
						if (this.constraints.relativePlacementConstraint) {
							var nodeToDummyForVerticalAlignment = /* @__PURE__ */ new Map();
							var nodeToDummyForHorizontalAlignment = /* @__PURE__ */ new Map();
							this.dummyToNodeForVerticalAlignment = /* @__PURE__ */ new Map();
							this.dummyToNodeForHorizontalAlignment = /* @__PURE__ */ new Map();
							this.fixedNodesOnHorizontal = /* @__PURE__ */ new Set();
							this.fixedNodesOnVertical = /* @__PURE__ */ new Set();
							this.fixedNodeSet.forEach(function(nodeId) {
								self.fixedNodesOnHorizontal.add(nodeId);
								self.fixedNodesOnVertical.add(nodeId);
							});
							if (this.constraints.alignmentConstraint) {
								if (this.constraints.alignmentConstraint.vertical) {
									var verticalAlignment = this.constraints.alignmentConstraint.vertical;
									for (var i = 0; i < verticalAlignment.length; i++) {
										this.dummyToNodeForVerticalAlignment.set("dummy" + i, []);
										verticalAlignment[i].forEach(function(nodeId) {
											nodeToDummyForVerticalAlignment.set(nodeId, "dummy" + i);
											self.dummyToNodeForVerticalAlignment.get("dummy" + i).push(nodeId);
											if (self.fixedNodeSet.has(nodeId)) self.fixedNodesOnHorizontal.add("dummy" + i);
										});
									}
								}
								if (this.constraints.alignmentConstraint.horizontal) {
									var horizontalAlignment = this.constraints.alignmentConstraint.horizontal;
									for (var i = 0; i < horizontalAlignment.length; i++) {
										this.dummyToNodeForHorizontalAlignment.set("dummy" + i, []);
										horizontalAlignment[i].forEach(function(nodeId) {
											nodeToDummyForHorizontalAlignment.set(nodeId, "dummy" + i);
											self.dummyToNodeForHorizontalAlignment.get("dummy" + i).push(nodeId);
											if (self.fixedNodeSet.has(nodeId)) self.fixedNodesOnVertical.add("dummy" + i);
										});
									}
								}
							}
							if (CoSEConstants.RELAX_MOVEMENT_ON_CONSTRAINTS) {
								this.shuffle = function(array) {
									var j, x, i;
									for (i = array.length - 1; i >= 2 * array.length / 3; i--) {
										j = Math.floor(Math.random() * (i + 1));
										x = array[i];
										array[i] = array[j];
										array[j] = x;
									}
									return array;
								};
								this.nodesInRelativeHorizontal = [];
								this.nodesInRelativeVertical = [];
								this.nodeToRelativeConstraintMapHorizontal = /* @__PURE__ */ new Map();
								this.nodeToRelativeConstraintMapVertical = /* @__PURE__ */ new Map();
								this.nodeToTempPositionMapHorizontal = /* @__PURE__ */ new Map();
								this.nodeToTempPositionMapVertical = /* @__PURE__ */ new Map();
								this.constraints.relativePlacementConstraint.forEach(function(constraint) {
									if (constraint.left) {
										var nodeIdLeft = nodeToDummyForVerticalAlignment.has(constraint.left) ? nodeToDummyForVerticalAlignment.get(constraint.left) : constraint.left;
										var nodeIdRight = nodeToDummyForVerticalAlignment.has(constraint.right) ? nodeToDummyForVerticalAlignment.get(constraint.right) : constraint.right;
										if (!self.nodesInRelativeHorizontal.includes(nodeIdLeft)) {
											self.nodesInRelativeHorizontal.push(nodeIdLeft);
											self.nodeToRelativeConstraintMapHorizontal.set(nodeIdLeft, []);
											if (self.dummyToNodeForVerticalAlignment.has(nodeIdLeft)) self.nodeToTempPositionMapHorizontal.set(nodeIdLeft, self.idToNodeMap.get(self.dummyToNodeForVerticalAlignment.get(nodeIdLeft)[0]).getCenterX());
											else self.nodeToTempPositionMapHorizontal.set(nodeIdLeft, self.idToNodeMap.get(nodeIdLeft).getCenterX());
										}
										if (!self.nodesInRelativeHorizontal.includes(nodeIdRight)) {
											self.nodesInRelativeHorizontal.push(nodeIdRight);
											self.nodeToRelativeConstraintMapHorizontal.set(nodeIdRight, []);
											if (self.dummyToNodeForVerticalAlignment.has(nodeIdRight)) self.nodeToTempPositionMapHorizontal.set(nodeIdRight, self.idToNodeMap.get(self.dummyToNodeForVerticalAlignment.get(nodeIdRight)[0]).getCenterX());
											else self.nodeToTempPositionMapHorizontal.set(nodeIdRight, self.idToNodeMap.get(nodeIdRight).getCenterX());
										}
										self.nodeToRelativeConstraintMapHorizontal.get(nodeIdLeft).push({
											right: nodeIdRight,
											gap: constraint.gap
										});
										self.nodeToRelativeConstraintMapHorizontal.get(nodeIdRight).push({
											left: nodeIdLeft,
											gap: constraint.gap
										});
									} else {
										var nodeIdTop = nodeToDummyForHorizontalAlignment.has(constraint.top) ? nodeToDummyForHorizontalAlignment.get(constraint.top) : constraint.top;
										var nodeIdBottom = nodeToDummyForHorizontalAlignment.has(constraint.bottom) ? nodeToDummyForHorizontalAlignment.get(constraint.bottom) : constraint.bottom;
										if (!self.nodesInRelativeVertical.includes(nodeIdTop)) {
											self.nodesInRelativeVertical.push(nodeIdTop);
											self.nodeToRelativeConstraintMapVertical.set(nodeIdTop, []);
											if (self.dummyToNodeForHorizontalAlignment.has(nodeIdTop)) self.nodeToTempPositionMapVertical.set(nodeIdTop, self.idToNodeMap.get(self.dummyToNodeForHorizontalAlignment.get(nodeIdTop)[0]).getCenterY());
											else self.nodeToTempPositionMapVertical.set(nodeIdTop, self.idToNodeMap.get(nodeIdTop).getCenterY());
										}
										if (!self.nodesInRelativeVertical.includes(nodeIdBottom)) {
											self.nodesInRelativeVertical.push(nodeIdBottom);
											self.nodeToRelativeConstraintMapVertical.set(nodeIdBottom, []);
											if (self.dummyToNodeForHorizontalAlignment.has(nodeIdBottom)) self.nodeToTempPositionMapVertical.set(nodeIdBottom, self.idToNodeMap.get(self.dummyToNodeForHorizontalAlignment.get(nodeIdBottom)[0]).getCenterY());
											else self.nodeToTempPositionMapVertical.set(nodeIdBottom, self.idToNodeMap.get(nodeIdBottom).getCenterY());
										}
										self.nodeToRelativeConstraintMapVertical.get(nodeIdTop).push({
											bottom: nodeIdBottom,
											gap: constraint.gap
										});
										self.nodeToRelativeConstraintMapVertical.get(nodeIdBottom).push({
											top: nodeIdTop,
											gap: constraint.gap
										});
									}
								});
							} else {
								var subGraphOnHorizontal = /* @__PURE__ */ new Map();
								var subGraphOnVertical = /* @__PURE__ */ new Map();
								this.constraints.relativePlacementConstraint.forEach(function(constraint) {
									if (constraint.left) {
										var left = nodeToDummyForVerticalAlignment.has(constraint.left) ? nodeToDummyForVerticalAlignment.get(constraint.left) : constraint.left;
										var right = nodeToDummyForVerticalAlignment.has(constraint.right) ? nodeToDummyForVerticalAlignment.get(constraint.right) : constraint.right;
										if (subGraphOnHorizontal.has(left)) subGraphOnHorizontal.get(left).push(right);
										else subGraphOnHorizontal.set(left, [right]);
										if (subGraphOnHorizontal.has(right)) subGraphOnHorizontal.get(right).push(left);
										else subGraphOnHorizontal.set(right, [left]);
									} else {
										var top = nodeToDummyForHorizontalAlignment.has(constraint.top) ? nodeToDummyForHorizontalAlignment.get(constraint.top) : constraint.top;
										var bottom = nodeToDummyForHorizontalAlignment.has(constraint.bottom) ? nodeToDummyForHorizontalAlignment.get(constraint.bottom) : constraint.bottom;
										if (subGraphOnVertical.has(top)) subGraphOnVertical.get(top).push(bottom);
										else subGraphOnVertical.set(top, [bottom]);
										if (subGraphOnVertical.has(bottom)) subGraphOnVertical.get(bottom).push(top);
										else subGraphOnVertical.set(bottom, [top]);
									}
								});
								var constructComponents = function constructComponents(graph, fixedNodes) {
									var components = [];
									var isFixed = [];
									var queue = new LinkedList();
									var visited = /* @__PURE__ */ new Set();
									var count = 0;
									graph.forEach(function(value, key) {
										if (!visited.has(key)) {
											components[count] = [];
											isFixed[count] = false;
											var currentNode = key;
											queue.push(currentNode);
											visited.add(currentNode);
											components[count].push(currentNode);
											while (queue.length != 0) {
												currentNode = queue.shift();
												if (fixedNodes.has(currentNode)) isFixed[count] = true;
												graph.get(currentNode).forEach(function(neighbor) {
													if (!visited.has(neighbor)) {
														queue.push(neighbor);
														visited.add(neighbor);
														components[count].push(neighbor);
													}
												});
											}
											count++;
										}
									});
									return {
										components,
										isFixed
									};
								};
								var resultOnHorizontal = constructComponents(subGraphOnHorizontal, self.fixedNodesOnHorizontal);
								this.componentsOnHorizontal = resultOnHorizontal.components;
								this.fixedComponentsOnHorizontal = resultOnHorizontal.isFixed;
								var resultOnVertical = constructComponents(subGraphOnVertical, self.fixedNodesOnVertical);
								this.componentsOnVertical = resultOnVertical.components;
								this.fixedComponentsOnVertical = resultOnVertical.isFixed;
							}
						}
					};
					CoSELayout.prototype.updateDisplacements = function() {
						var self = this;
						if (this.constraints.fixedNodeConstraint) this.constraints.fixedNodeConstraint.forEach(function(nodeData) {
							var fixedNode = self.idToNodeMap.get(nodeData.nodeId);
							fixedNode.displacementX = 0;
							fixedNode.displacementY = 0;
						});
						if (this.constraints.alignmentConstraint) {
							if (this.constraints.alignmentConstraint.vertical) {
								var allVerticalAlignments = this.constraints.alignmentConstraint.vertical;
								for (var i = 0; i < allVerticalAlignments.length; i++) {
									var totalDisplacementX = 0;
									for (var j = 0; j < allVerticalAlignments[i].length; j++) {
										if (this.fixedNodeSet.has(allVerticalAlignments[i][j])) {
											totalDisplacementX = 0;
											break;
										}
										totalDisplacementX += this.idToNodeMap.get(allVerticalAlignments[i][j]).displacementX;
									}
									var averageDisplacementX = totalDisplacementX / allVerticalAlignments[i].length;
									for (var j = 0; j < allVerticalAlignments[i].length; j++) this.idToNodeMap.get(allVerticalAlignments[i][j]).displacementX = averageDisplacementX;
								}
							}
							if (this.constraints.alignmentConstraint.horizontal) {
								var allHorizontalAlignments = this.constraints.alignmentConstraint.horizontal;
								for (var i = 0; i < allHorizontalAlignments.length; i++) {
									var totalDisplacementY = 0;
									for (var j = 0; j < allHorizontalAlignments[i].length; j++) {
										if (this.fixedNodeSet.has(allHorizontalAlignments[i][j])) {
											totalDisplacementY = 0;
											break;
										}
										totalDisplacementY += this.idToNodeMap.get(allHorizontalAlignments[i][j]).displacementY;
									}
									var averageDisplacementY = totalDisplacementY / allHorizontalAlignments[i].length;
									for (var j = 0; j < allHorizontalAlignments[i].length; j++) this.idToNodeMap.get(allHorizontalAlignments[i][j]).displacementY = averageDisplacementY;
								}
							}
						}
						if (this.constraints.relativePlacementConstraint) if (CoSEConstants.RELAX_MOVEMENT_ON_CONSTRAINTS) {
							if (this.totalIterations % 10 == 0) {
								this.shuffle(this.nodesInRelativeHorizontal);
								this.shuffle(this.nodesInRelativeVertical);
							}
							this.nodesInRelativeHorizontal.forEach(function(nodeId) {
								if (!self.fixedNodesOnHorizontal.has(nodeId)) {
									var displacement = 0;
									if (self.dummyToNodeForVerticalAlignment.has(nodeId)) displacement = self.idToNodeMap.get(self.dummyToNodeForVerticalAlignment.get(nodeId)[0]).displacementX;
									else displacement = self.idToNodeMap.get(nodeId).displacementX;
									self.nodeToRelativeConstraintMapHorizontal.get(nodeId).forEach(function(constraint) {
										if (constraint.right) {
											var diff = self.nodeToTempPositionMapHorizontal.get(constraint.right) - self.nodeToTempPositionMapHorizontal.get(nodeId) - displacement;
											if (diff < constraint.gap) displacement -= constraint.gap - diff;
										} else {
											var diff = self.nodeToTempPositionMapHorizontal.get(nodeId) - self.nodeToTempPositionMapHorizontal.get(constraint.left) + displacement;
											if (diff < constraint.gap) displacement += constraint.gap - diff;
										}
									});
									self.nodeToTempPositionMapHorizontal.set(nodeId, self.nodeToTempPositionMapHorizontal.get(nodeId) + displacement);
									if (self.dummyToNodeForVerticalAlignment.has(nodeId)) self.dummyToNodeForVerticalAlignment.get(nodeId).forEach(function(nodeId) {
										self.idToNodeMap.get(nodeId).displacementX = displacement;
									});
									else self.idToNodeMap.get(nodeId).displacementX = displacement;
								}
							});
							this.nodesInRelativeVertical.forEach(function(nodeId) {
								if (!self.fixedNodesOnHorizontal.has(nodeId)) {
									var displacement = 0;
									if (self.dummyToNodeForHorizontalAlignment.has(nodeId)) displacement = self.idToNodeMap.get(self.dummyToNodeForHorizontalAlignment.get(nodeId)[0]).displacementY;
									else displacement = self.idToNodeMap.get(nodeId).displacementY;
									self.nodeToRelativeConstraintMapVertical.get(nodeId).forEach(function(constraint) {
										if (constraint.bottom) {
											var diff = self.nodeToTempPositionMapVertical.get(constraint.bottom) - self.nodeToTempPositionMapVertical.get(nodeId) - displacement;
											if (diff < constraint.gap) displacement -= constraint.gap - diff;
										} else {
											var diff = self.nodeToTempPositionMapVertical.get(nodeId) - self.nodeToTempPositionMapVertical.get(constraint.top) + displacement;
											if (diff < constraint.gap) displacement += constraint.gap - diff;
										}
									});
									self.nodeToTempPositionMapVertical.set(nodeId, self.nodeToTempPositionMapVertical.get(nodeId) + displacement);
									if (self.dummyToNodeForHorizontalAlignment.has(nodeId)) self.dummyToNodeForHorizontalAlignment.get(nodeId).forEach(function(nodeId) {
										self.idToNodeMap.get(nodeId).displacementY = displacement;
									});
									else self.idToNodeMap.get(nodeId).displacementY = displacement;
								}
							});
						} else {
							for (var i = 0; i < this.componentsOnHorizontal.length; i++) {
								var component = this.componentsOnHorizontal[i];
								if (this.fixedComponentsOnHorizontal[i]) for (var j = 0; j < component.length; j++) if (this.dummyToNodeForVerticalAlignment.has(component[j])) this.dummyToNodeForVerticalAlignment.get(component[j]).forEach(function(nodeId) {
									self.idToNodeMap.get(nodeId).displacementX = 0;
								});
								else this.idToNodeMap.get(component[j]).displacementX = 0;
								else {
									var sum = 0;
									var count = 0;
									for (var j = 0; j < component.length; j++) if (this.dummyToNodeForVerticalAlignment.has(component[j])) {
										var actualNodes = this.dummyToNodeForVerticalAlignment.get(component[j]);
										sum += actualNodes.length * this.idToNodeMap.get(actualNodes[0]).displacementX;
										count += actualNodes.length;
									} else {
										sum += this.idToNodeMap.get(component[j]).displacementX;
										count++;
									}
									var averageDisplacement = sum / count;
									for (var j = 0; j < component.length; j++) if (this.dummyToNodeForVerticalAlignment.has(component[j])) this.dummyToNodeForVerticalAlignment.get(component[j]).forEach(function(nodeId) {
										self.idToNodeMap.get(nodeId).displacementX = averageDisplacement;
									});
									else this.idToNodeMap.get(component[j]).displacementX = averageDisplacement;
								}
							}
							for (var i = 0; i < this.componentsOnVertical.length; i++) {
								var component = this.componentsOnVertical[i];
								if (this.fixedComponentsOnVertical[i]) for (var j = 0; j < component.length; j++) if (this.dummyToNodeForHorizontalAlignment.has(component[j])) this.dummyToNodeForHorizontalAlignment.get(component[j]).forEach(function(nodeId) {
									self.idToNodeMap.get(nodeId).displacementY = 0;
								});
								else this.idToNodeMap.get(component[j]).displacementY = 0;
								else {
									var sum = 0;
									var count = 0;
									for (var j = 0; j < component.length; j++) if (this.dummyToNodeForHorizontalAlignment.has(component[j])) {
										var actualNodes = this.dummyToNodeForHorizontalAlignment.get(component[j]);
										sum += actualNodes.length * this.idToNodeMap.get(actualNodes[0]).displacementY;
										count += actualNodes.length;
									} else {
										sum += this.idToNodeMap.get(component[j]).displacementY;
										count++;
									}
									var averageDisplacement = sum / count;
									for (var j = 0; j < component.length; j++) if (this.dummyToNodeForHorizontalAlignment.has(component[j])) this.dummyToNodeForHorizontalAlignment.get(component[j]).forEach(function(nodeId) {
										self.idToNodeMap.get(nodeId).displacementY = averageDisplacement;
									});
									else this.idToNodeMap.get(component[j]).displacementY = averageDisplacement;
								}
							}
						}
					};
					CoSELayout.prototype.calculateNodesToApplyGravitationTo = function() {
						var nodeList = [];
						var graph;
						var graphs = this.graphManager.getGraphs();
						var size = graphs.length;
						var i;
						for (i = 0; i < size; i++) {
							graph = graphs[i];
							graph.updateConnected();
							if (!graph.isConnected) nodeList = nodeList.concat(graph.getNodes());
						}
						return nodeList;
					};
					CoSELayout.prototype.createBendpoints = function() {
						var edges = [];
						edges = edges.concat(this.graphManager.getAllEdges());
						var visited = /* @__PURE__ */ new Set();
						var i;
						for (i = 0; i < edges.length; i++) {
							var edge = edges[i];
							if (!visited.has(edge)) {
								var source = edge.getSource();
								var target = edge.getTarget();
								if (source == target) {
									edge.getBendpoints().push(new PointD());
									edge.getBendpoints().push(new PointD());
									this.createDummyNodesForBendpoints(edge);
									visited.add(edge);
								} else {
									var edgeList = [];
									edgeList = edgeList.concat(source.getEdgeListToNode(target));
									edgeList = edgeList.concat(target.getEdgeListToNode(source));
									if (!visited.has(edgeList[0])) {
										if (edgeList.length > 1) {
											var k;
											for (k = 0; k < edgeList.length; k++) {
												var multiEdge = edgeList[k];
												multiEdge.getBendpoints().push(new PointD());
												this.createDummyNodesForBendpoints(multiEdge);
											}
										}
										edgeList.forEach(function(edge) {
											visited.add(edge);
										});
									}
								}
							}
							if (visited.size == edges.length) break;
						}
					};
					CoSELayout.prototype.positionNodesRadially = function(forest) {
						var currentStartingPoint = new Point(0, 0);
						var numberOfColumns = Math.ceil(Math.sqrt(forest.length));
						var height = 0;
						var currentY = 0;
						var currentX = 0;
						var point = new PointD(0, 0);
						for (var i = 0; i < forest.length; i++) {
							if (i % numberOfColumns == 0) {
								currentX = 0;
								currentY = height;
								if (i != 0) currentY += CoSEConstants.DEFAULT_COMPONENT_SEPERATION;
								height = 0;
							}
							var tree = forest[i];
							var centerNode = Layout.findCenterOfTree(tree);
							currentStartingPoint.x = currentX;
							currentStartingPoint.y = currentY;
							point = CoSELayout.radialLayout(tree, centerNode, currentStartingPoint);
							if (point.y > height) height = Math.floor(point.y);
							currentX = Math.floor(point.x + CoSEConstants.DEFAULT_COMPONENT_SEPERATION);
						}
						this.transform(new PointD(LayoutConstants.WORLD_CENTER_X - point.x / 2, LayoutConstants.WORLD_CENTER_Y - point.y / 2));
					};
					CoSELayout.radialLayout = function(tree, centerNode, startingPoint) {
						var radialSep = Math.max(this.maxDiagonalInTree(tree), CoSEConstants.DEFAULT_RADIAL_SEPARATION);
						CoSELayout.branchRadialLayout(centerNode, null, 0, 359, 0, radialSep);
						var bounds = LGraph.calculateBounds(tree);
						var transform = new Transform();
						transform.setDeviceOrgX(bounds.getMinX());
						transform.setDeviceOrgY(bounds.getMinY());
						transform.setWorldOrgX(startingPoint.x);
						transform.setWorldOrgY(startingPoint.y);
						for (var i = 0; i < tree.length; i++) tree[i].transform(transform);
						var bottomRight = new PointD(bounds.getMaxX(), bounds.getMaxY());
						return transform.inverseTransformPoint(bottomRight);
					};
					CoSELayout.branchRadialLayout = function(node, parentOfNode, startAngle, endAngle, distance, radialSeparation) {
						var halfInterval = (endAngle - startAngle + 1) / 2;
						if (halfInterval < 0) halfInterval += 180;
						var teta = (halfInterval + startAngle) % 360 * IGeometry.TWO_PI / 360;
						var x_ = distance * Math.cos(teta);
						var y_ = distance * Math.sin(teta);
						node.setCenter(x_, y_);
						var neighborEdges = [];
						neighborEdges = neighborEdges.concat(node.getEdges());
						var childCount = neighborEdges.length;
						if (parentOfNode != null) childCount--;
						var branchCount = 0;
						var incEdgesCount = neighborEdges.length;
						var startIndex;
						var edges = node.getEdgesBetween(parentOfNode);
						while (edges.length > 1) {
							var temp = edges[0];
							edges.splice(0, 1);
							var index = neighborEdges.indexOf(temp);
							if (index >= 0) neighborEdges.splice(index, 1);
							incEdgesCount--;
							childCount--;
						}
						if (parentOfNode != null) startIndex = (neighborEdges.indexOf(edges[0]) + 1) % incEdgesCount;
						else startIndex = 0;
						var stepAngle = Math.abs(endAngle - startAngle) / childCount;
						for (var i = startIndex; branchCount != childCount; i = ++i % incEdgesCount) {
							var currentNeighbor = neighborEdges[i].getOtherEnd(node);
							if (currentNeighbor == parentOfNode) continue;
							var childStartAngle = (startAngle + branchCount * stepAngle) % 360;
							var childEndAngle = (childStartAngle + stepAngle) % 360;
							CoSELayout.branchRadialLayout(currentNeighbor, node, childStartAngle, childEndAngle, distance + radialSeparation, radialSeparation);
							branchCount++;
						}
					};
					CoSELayout.maxDiagonalInTree = function(tree) {
						var maxDiagonal = Integer.MIN_VALUE;
						for (var i = 0; i < tree.length; i++) {
							var diagonal = tree[i].getDiagonal();
							if (diagonal > maxDiagonal) maxDiagonal = diagonal;
						}
						return maxDiagonal;
					};
					CoSELayout.prototype.calcRepulsionRange = function() {
						return 2 * (this.level + 1) * this.idealEdgeLength;
					};
					CoSELayout.prototype.groupZeroDegreeMembers = function() {
						var self = this;
						var tempMemberGroups = {};
						this.memberGroups = {};
						this.idToDummyNode = {};
						var zeroDegree = [];
						var allNodes = this.graphManager.getAllNodes();
						for (var i = 0; i < allNodes.length; i++) {
							var node = allNodes[i];
							var parent = node.getParent();
							if (this.getNodeDegreeWithChildren(node) === 0 && (parent.id == void 0 || !this.getToBeTiled(parent))) zeroDegree.push(node);
						}
						for (var i = 0; i < zeroDegree.length; i++) {
							var node = zeroDegree[i];
							var p_id = node.getParent().id;
							if (typeof tempMemberGroups[p_id] === "undefined") tempMemberGroups[p_id] = [];
							tempMemberGroups[p_id] = tempMemberGroups[p_id].concat(node);
						}
						Object.keys(tempMemberGroups).forEach(function(p_id) {
							if (tempMemberGroups[p_id].length > 1) {
								var dummyCompoundId = "DummyCompound_" + p_id;
								self.memberGroups[dummyCompoundId] = tempMemberGroups[p_id];
								var parent = tempMemberGroups[p_id][0].getParent();
								var dummyCompound = new CoSENode(self.graphManager);
								dummyCompound.id = dummyCompoundId;
								dummyCompound.paddingLeft = parent.paddingLeft || 0;
								dummyCompound.paddingRight = parent.paddingRight || 0;
								dummyCompound.paddingBottom = parent.paddingBottom || 0;
								dummyCompound.paddingTop = parent.paddingTop || 0;
								self.idToDummyNode[dummyCompoundId] = dummyCompound;
								var dummyParentGraph = self.getGraphManager().add(self.newGraph(), dummyCompound);
								var parentGraph = parent.getChild();
								parentGraph.add(dummyCompound);
								for (var i = 0; i < tempMemberGroups[p_id].length; i++) {
									var node = tempMemberGroups[p_id][i];
									parentGraph.remove(node);
									dummyParentGraph.add(node);
								}
							}
						});
					};
					CoSELayout.prototype.clearCompounds = function() {
						var childGraphMap = {};
						var idToNode = {};
						this.performDFSOnCompounds();
						for (var i = 0; i < this.compoundOrder.length; i++) {
							idToNode[this.compoundOrder[i].id] = this.compoundOrder[i];
							childGraphMap[this.compoundOrder[i].id] = [].concat(this.compoundOrder[i].getChild().getNodes());
							this.graphManager.remove(this.compoundOrder[i].getChild());
							this.compoundOrder[i].child = null;
						}
						this.graphManager.resetAllNodes();
						this.tileCompoundMembers(childGraphMap, idToNode);
					};
					CoSELayout.prototype.clearZeroDegreeMembers = function() {
						var self = this;
						var tiledZeroDegreePack = this.tiledZeroDegreePack = [];
						Object.keys(this.memberGroups).forEach(function(id) {
							var compoundNode = self.idToDummyNode[id];
							tiledZeroDegreePack[id] = self.tileNodes(self.memberGroups[id], compoundNode.paddingLeft + compoundNode.paddingRight);
							compoundNode.rect.width = tiledZeroDegreePack[id].width;
							compoundNode.rect.height = tiledZeroDegreePack[id].height;
							compoundNode.setCenter(tiledZeroDegreePack[id].centerX, tiledZeroDegreePack[id].centerY);
							compoundNode.labelMarginLeft = 0;
							compoundNode.labelMarginTop = 0;
							if (CoSEConstants.NODE_DIMENSIONS_INCLUDE_LABELS) {
								var width = compoundNode.rect.width;
								var height = compoundNode.rect.height;
								if (compoundNode.labelWidth) {
									if (compoundNode.labelPosHorizontal == "left") {
										compoundNode.rect.x -= compoundNode.labelWidth;
										compoundNode.setWidth(width + compoundNode.labelWidth);
										compoundNode.labelMarginLeft = compoundNode.labelWidth;
									} else if (compoundNode.labelPosHorizontal == "center" && compoundNode.labelWidth > width) {
										compoundNode.rect.x -= (compoundNode.labelWidth - width) / 2;
										compoundNode.setWidth(compoundNode.labelWidth);
										compoundNode.labelMarginLeft = (compoundNode.labelWidth - width) / 2;
									} else if (compoundNode.labelPosHorizontal == "right") compoundNode.setWidth(width + compoundNode.labelWidth);
								}
								if (compoundNode.labelHeight) {
									if (compoundNode.labelPosVertical == "top") {
										compoundNode.rect.y -= compoundNode.labelHeight;
										compoundNode.setHeight(height + compoundNode.labelHeight);
										compoundNode.labelMarginTop = compoundNode.labelHeight;
									} else if (compoundNode.labelPosVertical == "center" && compoundNode.labelHeight > height) {
										compoundNode.rect.y -= (compoundNode.labelHeight - height) / 2;
										compoundNode.setHeight(compoundNode.labelHeight);
										compoundNode.labelMarginTop = (compoundNode.labelHeight - height) / 2;
									} else if (compoundNode.labelPosVertical == "bottom") compoundNode.setHeight(height + compoundNode.labelHeight);
								}
							}
						});
					};
					CoSELayout.prototype.repopulateCompounds = function() {
						for (var i = this.compoundOrder.length - 1; i >= 0; i--) {
							var lCompoundNode = this.compoundOrder[i];
							var id = lCompoundNode.id;
							var horizontalMargin = lCompoundNode.paddingLeft;
							var verticalMargin = lCompoundNode.paddingTop;
							var labelMarginLeft = lCompoundNode.labelMarginLeft;
							var labelMarginTop = lCompoundNode.labelMarginTop;
							this.adjustLocations(this.tiledMemberPack[id], lCompoundNode.rect.x, lCompoundNode.rect.y, horizontalMargin, verticalMargin, labelMarginLeft, labelMarginTop);
						}
					};
					CoSELayout.prototype.repopulateZeroDegreeMembers = function() {
						var self = this;
						var tiledPack = this.tiledZeroDegreePack;
						Object.keys(tiledPack).forEach(function(id) {
							var compoundNode = self.idToDummyNode[id];
							var horizontalMargin = compoundNode.paddingLeft;
							var verticalMargin = compoundNode.paddingTop;
							var labelMarginLeft = compoundNode.labelMarginLeft;
							var labelMarginTop = compoundNode.labelMarginTop;
							self.adjustLocations(tiledPack[id], compoundNode.rect.x, compoundNode.rect.y, horizontalMargin, verticalMargin, labelMarginLeft, labelMarginTop);
						});
					};
					CoSELayout.prototype.getToBeTiled = function(node) {
						var id = node.id;
						if (this.toBeTiled[id] != null) return this.toBeTiled[id];
						var childGraph = node.getChild();
						if (childGraph == null) {
							this.toBeTiled[id] = false;
							return false;
						}
						var children = childGraph.getNodes();
						for (var i = 0; i < children.length; i++) {
							var theChild = children[i];
							if (this.getNodeDegree(theChild) > 0) {
								this.toBeTiled[id] = false;
								return false;
							}
							if (theChild.getChild() == null) {
								this.toBeTiled[theChild.id] = false;
								continue;
							}
							if (!this.getToBeTiled(theChild)) {
								this.toBeTiled[id] = false;
								return false;
							}
						}
						this.toBeTiled[id] = true;
						return true;
					};
					CoSELayout.prototype.getNodeDegree = function(node) {
						node.id;
						var edges = node.getEdges();
						var degree = 0;
						for (var i = 0; i < edges.length; i++) {
							var edge = edges[i];
							if (edge.getSource().id !== edge.getTarget().id) degree = degree + 1;
						}
						return degree;
					};
					CoSELayout.prototype.getNodeDegreeWithChildren = function(node) {
						var degree = this.getNodeDegree(node);
						if (node.getChild() == null) return degree;
						var children = node.getChild().getNodes();
						for (var i = 0; i < children.length; i++) {
							var child = children[i];
							degree += this.getNodeDegreeWithChildren(child);
						}
						return degree;
					};
					CoSELayout.prototype.performDFSOnCompounds = function() {
						this.compoundOrder = [];
						this.fillCompexOrderByDFS(this.graphManager.getRoot().getNodes());
					};
					CoSELayout.prototype.fillCompexOrderByDFS = function(children) {
						for (var i = 0; i < children.length; i++) {
							var child = children[i];
							if (child.getChild() != null) this.fillCompexOrderByDFS(child.getChild().getNodes());
							if (this.getToBeTiled(child)) this.compoundOrder.push(child);
						}
					};
					/**
					* This method places each zero degree member wrt given (x,y) coordinates (top left).
					*/
					CoSELayout.prototype.adjustLocations = function(organization, x, y, compoundHorizontalMargin, compoundVerticalMargin, compoundLabelMarginLeft, compoundLabelMarginTop) {
						x += compoundHorizontalMargin + compoundLabelMarginLeft;
						y += compoundVerticalMargin + compoundLabelMarginTop;
						var left = x;
						for (var i = 0; i < organization.rows.length; i++) {
							var row = organization.rows[i];
							x = left;
							var maxHeight = 0;
							for (var j = 0; j < row.length; j++) {
								var lnode = row[j];
								lnode.rect.x = x;
								lnode.rect.y = y;
								x += lnode.rect.width + organization.horizontalPadding;
								if (lnode.rect.height > maxHeight) maxHeight = lnode.rect.height;
							}
							y += maxHeight + organization.verticalPadding;
						}
					};
					CoSELayout.prototype.tileCompoundMembers = function(childGraphMap, idToNode) {
						var self = this;
						this.tiledMemberPack = [];
						Object.keys(childGraphMap).forEach(function(id) {
							var compoundNode = idToNode[id];
							self.tiledMemberPack[id] = self.tileNodes(childGraphMap[id], compoundNode.paddingLeft + compoundNode.paddingRight);
							compoundNode.rect.width = self.tiledMemberPack[id].width;
							compoundNode.rect.height = self.tiledMemberPack[id].height;
							compoundNode.setCenter(self.tiledMemberPack[id].centerX, self.tiledMemberPack[id].centerY);
							compoundNode.labelMarginLeft = 0;
							compoundNode.labelMarginTop = 0;
							if (CoSEConstants.NODE_DIMENSIONS_INCLUDE_LABELS) {
								var width = compoundNode.rect.width;
								var height = compoundNode.rect.height;
								if (compoundNode.labelWidth) {
									if (compoundNode.labelPosHorizontal == "left") {
										compoundNode.rect.x -= compoundNode.labelWidth;
										compoundNode.setWidth(width + compoundNode.labelWidth);
										compoundNode.labelMarginLeft = compoundNode.labelWidth;
									} else if (compoundNode.labelPosHorizontal == "center" && compoundNode.labelWidth > width) {
										compoundNode.rect.x -= (compoundNode.labelWidth - width) / 2;
										compoundNode.setWidth(compoundNode.labelWidth);
										compoundNode.labelMarginLeft = (compoundNode.labelWidth - width) / 2;
									} else if (compoundNode.labelPosHorizontal == "right") compoundNode.setWidth(width + compoundNode.labelWidth);
								}
								if (compoundNode.labelHeight) {
									if (compoundNode.labelPosVertical == "top") {
										compoundNode.rect.y -= compoundNode.labelHeight;
										compoundNode.setHeight(height + compoundNode.labelHeight);
										compoundNode.labelMarginTop = compoundNode.labelHeight;
									} else if (compoundNode.labelPosVertical == "center" && compoundNode.labelHeight > height) {
										compoundNode.rect.y -= (compoundNode.labelHeight - height) / 2;
										compoundNode.setHeight(compoundNode.labelHeight);
										compoundNode.labelMarginTop = (compoundNode.labelHeight - height) / 2;
									} else if (compoundNode.labelPosVertical == "bottom") compoundNode.setHeight(height + compoundNode.labelHeight);
								}
							}
						});
					};
					CoSELayout.prototype.tileNodes = function(nodes, minWidth) {
						var horizontalOrg = this.tileNodesByFavoringDim(nodes, minWidth, true);
						var verticalOrg = this.tileNodesByFavoringDim(nodes, minWidth, false);
						var horizontalRatio = this.getOrgRatio(horizontalOrg);
						var verticalRatio = this.getOrgRatio(verticalOrg);
						var bestOrg;
						if (verticalRatio < horizontalRatio) bestOrg = verticalOrg;
						else bestOrg = horizontalOrg;
						return bestOrg;
					};
					CoSELayout.prototype.getOrgRatio = function(organization) {
						var ratio = organization.width / organization.height;
						if (ratio < 1) ratio = 1 / ratio;
						return ratio;
					};
					CoSELayout.prototype.calcIdealRowWidth = function(members, favorHorizontalDim) {
						var verticalPadding = CoSEConstants.TILING_PADDING_VERTICAL;
						var horizontalPadding = CoSEConstants.TILING_PADDING_HORIZONTAL;
						var membersSize = members.length;
						var totalWidth = 0;
						var totalHeight = 0;
						var maxWidth = 0;
						members.forEach(function(node) {
							totalWidth += node.getWidth();
							totalHeight += node.getHeight();
							if (node.getWidth() > maxWidth) maxWidth = node.getWidth();
						});
						var averageWidth = totalWidth / membersSize;
						var averageHeight = totalHeight / membersSize;
						var delta = Math.pow(verticalPadding - horizontalPadding, 2) + 4 * (averageWidth + horizontalPadding) * (averageHeight + verticalPadding) * membersSize;
						var horizontalCountDouble = (horizontalPadding - verticalPadding + Math.sqrt(delta)) / (2 * (averageWidth + horizontalPadding));
						var horizontalCount;
						if (favorHorizontalDim) {
							horizontalCount = Math.ceil(horizontalCountDouble);
							if (horizontalCount == horizontalCountDouble) horizontalCount++;
						} else horizontalCount = Math.floor(horizontalCountDouble);
						var idealWidth = horizontalCount * (averageWidth + horizontalPadding) - horizontalPadding;
						if (maxWidth > idealWidth) idealWidth = maxWidth;
						idealWidth += horizontalPadding * 2;
						return idealWidth;
					};
					CoSELayout.prototype.tileNodesByFavoringDim = function(nodes, minWidth, favorHorizontalDim) {
						var verticalPadding = CoSEConstants.TILING_PADDING_VERTICAL;
						var horizontalPadding = CoSEConstants.TILING_PADDING_HORIZONTAL;
						var tilingCompareBy = CoSEConstants.TILING_COMPARE_BY;
						var organization = {
							rows: [],
							rowWidth: [],
							rowHeight: [],
							width: 0,
							height: minWidth,
							verticalPadding,
							horizontalPadding,
							centerX: 0,
							centerY: 0
						};
						if (tilingCompareBy) organization.idealRowWidth = this.calcIdealRowWidth(nodes, favorHorizontalDim);
						var getNodeArea = function getNodeArea(n) {
							return n.rect.width * n.rect.height;
						};
						var areaCompareFcn = function areaCompareFcn(n1, n2) {
							return getNodeArea(n2) - getNodeArea(n1);
						};
						nodes.sort(function(n1, n2) {
							var cmpBy = areaCompareFcn;
							if (organization.idealRowWidth) {
								cmpBy = tilingCompareBy;
								return cmpBy(n1.id, n2.id);
							}
							return cmpBy(n1, n2);
						});
						var sumCenterX = 0;
						var sumCenterY = 0;
						for (var i = 0; i < nodes.length; i++) {
							var lNode = nodes[i];
							sumCenterX += lNode.getCenterX();
							sumCenterY += lNode.getCenterY();
						}
						organization.centerX = sumCenterX / nodes.length;
						organization.centerY = sumCenterY / nodes.length;
						for (var i = 0; i < nodes.length; i++) {
							var lNode = nodes[i];
							if (organization.rows.length == 0) this.insertNodeToRow(organization, lNode, 0, minWidth);
							else if (this.canAddHorizontal(organization, lNode.rect.width, lNode.rect.height)) {
								var rowIndex = organization.rows.length - 1;
								if (!organization.idealRowWidth) rowIndex = this.getShortestRowIndex(organization);
								this.insertNodeToRow(organization, lNode, rowIndex, minWidth);
							} else this.insertNodeToRow(organization, lNode, organization.rows.length, minWidth);
							this.shiftToLastRow(organization);
						}
						return organization;
					};
					CoSELayout.prototype.insertNodeToRow = function(organization, node, rowIndex, minWidth) {
						var minCompoundSize = minWidth;
						if (rowIndex == organization.rows.length) {
							organization.rows.push([]);
							organization.rowWidth.push(minCompoundSize);
							organization.rowHeight.push(0);
						}
						var w = organization.rowWidth[rowIndex] + node.rect.width;
						if (organization.rows[rowIndex].length > 0) w += organization.horizontalPadding;
						organization.rowWidth[rowIndex] = w;
						if (organization.width < w) organization.width = w;
						var h = node.rect.height;
						if (rowIndex > 0) h += organization.verticalPadding;
						var extraHeight = 0;
						if (h > organization.rowHeight[rowIndex]) {
							extraHeight = organization.rowHeight[rowIndex];
							organization.rowHeight[rowIndex] = h;
							extraHeight = organization.rowHeight[rowIndex] - extraHeight;
						}
						organization.height += extraHeight;
						organization.rows[rowIndex].push(node);
					};
					CoSELayout.prototype.getShortestRowIndex = function(organization) {
						var r = -1;
						var min = Number.MAX_VALUE;
						for (var i = 0; i < organization.rows.length; i++) if (organization.rowWidth[i] < min) {
							r = i;
							min = organization.rowWidth[i];
						}
						return r;
					};
					CoSELayout.prototype.getLongestRowIndex = function(organization) {
						var r = -1;
						var max = Number.MIN_VALUE;
						for (var i = 0; i < organization.rows.length; i++) if (organization.rowWidth[i] > max) {
							r = i;
							max = organization.rowWidth[i];
						}
						return r;
					};
					/**
					* This method checks whether adding extra width to the organization violates
					* the aspect ratio(1) or not.
					*/
					CoSELayout.prototype.canAddHorizontal = function(organization, extraWidth, extraHeight) {
						if (organization.idealRowWidth) {
							var lastRowIndex = organization.rows.length - 1;
							return organization.rowWidth[lastRowIndex] + extraWidth + organization.horizontalPadding <= organization.idealRowWidth;
						}
						var sri = this.getShortestRowIndex(organization);
						if (sri < 0) return true;
						var min = organization.rowWidth[sri];
						if (min + organization.horizontalPadding + extraWidth <= organization.width) return true;
						var hDiff = 0;
						if (organization.rowHeight[sri] < extraHeight) {
							if (sri > 0) hDiff = extraHeight + organization.verticalPadding - organization.rowHeight[sri];
						}
						var add_to_row_ratio;
						if (organization.width - min >= extraWidth + organization.horizontalPadding) add_to_row_ratio = (organization.height + hDiff) / (min + extraWidth + organization.horizontalPadding);
						else add_to_row_ratio = (organization.height + hDiff) / organization.width;
						hDiff = extraHeight + organization.verticalPadding;
						var add_new_row_ratio;
						if (organization.width < extraWidth) add_new_row_ratio = (organization.height + hDiff) / extraWidth;
						else add_new_row_ratio = (organization.height + hDiff) / organization.width;
						if (add_new_row_ratio < 1) add_new_row_ratio = 1 / add_new_row_ratio;
						if (add_to_row_ratio < 1) add_to_row_ratio = 1 / add_to_row_ratio;
						return add_to_row_ratio < add_new_row_ratio;
					};
					CoSELayout.prototype.shiftToLastRow = function(organization) {
						var longest = this.getLongestRowIndex(organization);
						var last = organization.rowWidth.length - 1;
						var row = organization.rows[longest];
						var node = row[row.length - 1];
						var diff = node.width + organization.horizontalPadding;
						if (organization.width - organization.rowWidth[last] > diff && longest != last) {
							row.splice(-1, 1);
							organization.rows[last].push(node);
							organization.rowWidth[longest] = organization.rowWidth[longest] - diff;
							organization.rowWidth[last] = organization.rowWidth[last] + diff;
							organization.width = organization.rowWidth[instance.getLongestRowIndex(organization)];
							var maxHeight = Number.MIN_VALUE;
							for (var i = 0; i < row.length; i++) if (row[i].height > maxHeight) maxHeight = row[i].height;
							if (longest > 0) maxHeight += organization.verticalPadding;
							var prevTotal = organization.rowHeight[longest] + organization.rowHeight[last];
							organization.rowHeight[longest] = maxHeight;
							if (organization.rowHeight[last] < node.height + organization.verticalPadding) organization.rowHeight[last] = node.height + organization.verticalPadding;
							var finalTotal = organization.rowHeight[longest] + organization.rowHeight[last];
							organization.height += finalTotal - prevTotal;
							this.shiftToLastRow(organization);
						}
					};
					CoSELayout.prototype.tilingPreLayout = function() {
						if (CoSEConstants.TILE) {
							this.groupZeroDegreeMembers();
							this.clearCompounds();
							this.clearZeroDegreeMembers();
						}
					};
					CoSELayout.prototype.tilingPostLayout = function() {
						if (CoSEConstants.TILE) {
							this.repopulateZeroDegreeMembers();
							this.repopulateCompounds();
						}
					};
					CoSELayout.prototype.reduceTrees = function() {
						var prunedNodesAll = [];
						var containsLeaf = true;
						var node;
						while (containsLeaf) {
							var allNodes = this.graphManager.getAllNodes();
							var prunedNodesInStepTemp = [];
							containsLeaf = false;
							for (var i = 0; i < allNodes.length; i++) {
								node = allNodes[i];
								if (node.getEdges().length == 1 && !node.getEdges()[0].isInterGraph && node.getChild() == null) {
									if (CoSEConstants.PURE_INCREMENTAL) {
										var otherEnd = node.getEdges()[0].getOtherEnd(node);
										var relativePosition = new DimensionD(node.getCenterX() - otherEnd.getCenterX(), node.getCenterY() - otherEnd.getCenterY());
										prunedNodesInStepTemp.push([
											node,
											node.getEdges()[0],
											node.getOwner(),
											relativePosition
										]);
									} else prunedNodesInStepTemp.push([
										node,
										node.getEdges()[0],
										node.getOwner()
									]);
									containsLeaf = true;
								}
							}
							if (containsLeaf == true) {
								var prunedNodesInStep = [];
								for (var j = 0; j < prunedNodesInStepTemp.length; j++) if (prunedNodesInStepTemp[j][0].getEdges().length == 1) {
									prunedNodesInStep.push(prunedNodesInStepTemp[j]);
									prunedNodesInStepTemp[j][0].getOwner().remove(prunedNodesInStepTemp[j][0]);
								}
								prunedNodesAll.push(prunedNodesInStep);
								this.graphManager.resetAllNodes();
								this.graphManager.resetAllEdges();
							}
						}
						this.prunedNodesAll = prunedNodesAll;
					};
					CoSELayout.prototype.growTree = function(prunedNodesAll) {
						var prunedNodesInStep = prunedNodesAll[prunedNodesAll.length - 1];
						var nodeData;
						for (var i = 0; i < prunedNodesInStep.length; i++) {
							nodeData = prunedNodesInStep[i];
							this.findPlaceforPrunedNode(nodeData);
							nodeData[2].add(nodeData[0]);
							nodeData[2].add(nodeData[1], nodeData[1].source, nodeData[1].target);
						}
						prunedNodesAll.splice(prunedNodesAll.length - 1, 1);
						this.graphManager.resetAllNodes();
						this.graphManager.resetAllEdges();
					};
					CoSELayout.prototype.findPlaceforPrunedNode = function(nodeData) {
						var gridForPrunedNode;
						var nodeToConnect;
						var prunedNode = nodeData[0];
						if (prunedNode == nodeData[1].source) nodeToConnect = nodeData[1].target;
						else nodeToConnect = nodeData[1].source;
						if (CoSEConstants.PURE_INCREMENTAL) prunedNode.setCenter(nodeToConnect.getCenterX() + nodeData[3].getWidth(), nodeToConnect.getCenterY() + nodeData[3].getHeight());
						else {
							var startGridX = nodeToConnect.startX;
							var finishGridX = nodeToConnect.finishX;
							var startGridY = nodeToConnect.startY;
							var finishGridY = nodeToConnect.finishY;
							var controlRegions = [
								0,
								0,
								0,
								0
							];
							if (startGridY > 0) for (var i = startGridX; i <= finishGridX; i++) controlRegions[0] += this.grid[i][startGridY - 1].length + this.grid[i][startGridY].length - 1;
							if (finishGridX < this.grid.length - 1) for (var i = startGridY; i <= finishGridY; i++) controlRegions[1] += this.grid[finishGridX + 1][i].length + this.grid[finishGridX][i].length - 1;
							if (finishGridY < this.grid[0].length - 1) for (var i = startGridX; i <= finishGridX; i++) controlRegions[2] += this.grid[i][finishGridY + 1].length + this.grid[i][finishGridY].length - 1;
							if (startGridX > 0) for (var i = startGridY; i <= finishGridY; i++) controlRegions[3] += this.grid[startGridX - 1][i].length + this.grid[startGridX][i].length - 1;
							var min = Integer.MAX_VALUE;
							var minCount;
							var minIndex;
							for (var j = 0; j < controlRegions.length; j++) if (controlRegions[j] < min) {
								min = controlRegions[j];
								minCount = 1;
								minIndex = j;
							} else if (controlRegions[j] == min) minCount++;
							if (minCount == 3 && min == 0) {
								if (controlRegions[0] == 0 && controlRegions[1] == 0 && controlRegions[2] == 0) gridForPrunedNode = 1;
								else if (controlRegions[0] == 0 && controlRegions[1] == 0 && controlRegions[3] == 0) gridForPrunedNode = 0;
								else if (controlRegions[0] == 0 && controlRegions[2] == 0 && controlRegions[3] == 0) gridForPrunedNode = 3;
								else if (controlRegions[1] == 0 && controlRegions[2] == 0 && controlRegions[3] == 0) gridForPrunedNode = 2;
							} else if (minCount == 2 && min == 0) {
								var random = Math.floor(Math.random() * 2);
								if (controlRegions[0] == 0 && controlRegions[1] == 0) if (random == 0) gridForPrunedNode = 0;
								else gridForPrunedNode = 1;
								else if (controlRegions[0] == 0 && controlRegions[2] == 0) if (random == 0) gridForPrunedNode = 0;
								else gridForPrunedNode = 2;
								else if (controlRegions[0] == 0 && controlRegions[3] == 0) if (random == 0) gridForPrunedNode = 0;
								else gridForPrunedNode = 3;
								else if (controlRegions[1] == 0 && controlRegions[2] == 0) if (random == 0) gridForPrunedNode = 1;
								else gridForPrunedNode = 2;
								else if (controlRegions[1] == 0 && controlRegions[3] == 0) if (random == 0) gridForPrunedNode = 1;
								else gridForPrunedNode = 3;
								else if (random == 0) gridForPrunedNode = 2;
								else gridForPrunedNode = 3;
							} else if (minCount == 4 && min == 0) {
								var random = Math.floor(Math.random() * 4);
								gridForPrunedNode = random;
							} else gridForPrunedNode = minIndex;
							if (gridForPrunedNode == 0) prunedNode.setCenter(nodeToConnect.getCenterX(), nodeToConnect.getCenterY() - nodeToConnect.getHeight() / 2 - FDLayoutConstants.DEFAULT_EDGE_LENGTH - prunedNode.getHeight() / 2);
							else if (gridForPrunedNode == 1) prunedNode.setCenter(nodeToConnect.getCenterX() + nodeToConnect.getWidth() / 2 + FDLayoutConstants.DEFAULT_EDGE_LENGTH + prunedNode.getWidth() / 2, nodeToConnect.getCenterY());
							else if (gridForPrunedNode == 2) prunedNode.setCenter(nodeToConnect.getCenterX(), nodeToConnect.getCenterY() + nodeToConnect.getHeight() / 2 + FDLayoutConstants.DEFAULT_EDGE_LENGTH + prunedNode.getHeight() / 2);
							else prunedNode.setCenter(nodeToConnect.getCenterX() - nodeToConnect.getWidth() / 2 - FDLayoutConstants.DEFAULT_EDGE_LENGTH - prunedNode.getWidth() / 2, nodeToConnect.getCenterY());
						}
					};
					module$46.exports = CoSELayout;
				}),
				991: ((module$47, __unused_webpack_exports, __webpack_require__) => {
					var FDLayoutNode = __webpack_require__(551).FDLayoutNode;
					var IMath = __webpack_require__(551).IMath;
					function CoSENode(gm, loc, size, vNode) {
						FDLayoutNode.call(this, gm, loc, size, vNode);
					}
					CoSENode.prototype = Object.create(FDLayoutNode.prototype);
					for (var prop in FDLayoutNode) CoSENode[prop] = FDLayoutNode[prop];
					CoSENode.prototype.calculateDisplacement = function() {
						var layout = this.graphManager.getLayout();
						if (this.getChild() != null && this.fixedNodeWeight) {
							this.displacementX += layout.coolingFactor * (this.springForceX + this.repulsionForceX + this.gravitationForceX) / this.fixedNodeWeight;
							this.displacementY += layout.coolingFactor * (this.springForceY + this.repulsionForceY + this.gravitationForceY) / this.fixedNodeWeight;
						} else {
							this.displacementX += layout.coolingFactor * (this.springForceX + this.repulsionForceX + this.gravitationForceX) / this.noOfChildren;
							this.displacementY += layout.coolingFactor * (this.springForceY + this.repulsionForceY + this.gravitationForceY) / this.noOfChildren;
						}
						if (Math.abs(this.displacementX) > layout.coolingFactor * layout.maxNodeDisplacement) this.displacementX = layout.coolingFactor * layout.maxNodeDisplacement * IMath.sign(this.displacementX);
						if (Math.abs(this.displacementY) > layout.coolingFactor * layout.maxNodeDisplacement) this.displacementY = layout.coolingFactor * layout.maxNodeDisplacement * IMath.sign(this.displacementY);
						if (this.child && this.child.getNodes().length > 0) this.propogateDisplacementToChildren(this.displacementX, this.displacementY);
					};
					CoSENode.prototype.propogateDisplacementToChildren = function(dX, dY) {
						var nodes = this.getChild().getNodes();
						var node;
						for (var i = 0; i < nodes.length; i++) {
							node = nodes[i];
							if (node.getChild() == null) {
								node.displacementX += dX;
								node.displacementY += dY;
							} else node.propogateDisplacementToChildren(dX, dY);
						}
					};
					CoSENode.prototype.move = function() {
						var layout = this.graphManager.getLayout();
						if (this.child == null || this.child.getNodes().length == 0) {
							this.moveBy(this.displacementX, this.displacementY);
							layout.totalDisplacement += Math.abs(this.displacementX) + Math.abs(this.displacementY);
						}
						this.springForceX = 0;
						this.springForceY = 0;
						this.repulsionForceX = 0;
						this.repulsionForceY = 0;
						this.gravitationForceX = 0;
						this.gravitationForceY = 0;
						this.displacementX = 0;
						this.displacementY = 0;
					};
					CoSENode.prototype.setPred1 = function(pred1) {
						this.pred1 = pred1;
					};
					CoSENode.prototype.getPred1 = function() {
						return pred1;
					};
					CoSENode.prototype.getPred2 = function() {
						return pred2;
					};
					CoSENode.prototype.setNext = function(next) {
						this.next = next;
					};
					CoSENode.prototype.getNext = function() {
						return next;
					};
					CoSENode.prototype.setProcessed = function(processed) {
						this.processed = processed;
					};
					CoSENode.prototype.isProcessed = function() {
						return processed;
					};
					module$47.exports = CoSENode;
				}),
				902: ((module$48, __unused_webpack_exports, __webpack_require__) => {
					function _toConsumableArray(arr) {
						if (Array.isArray(arr)) {
							for (var i = 0, arr2 = Array(arr.length); i < arr.length; i++) arr2[i] = arr[i];
							return arr2;
						} else return Array.from(arr);
					}
					var CoSEConstants = __webpack_require__(806);
					var LinkedList = __webpack_require__(551).LinkedList;
					var Matrix = __webpack_require__(551).Matrix;
					var SVD = __webpack_require__(551).SVD;
					function ConstraintHandler() {}
					ConstraintHandler.handleConstraints = function(layout) {
						var constraints = {};
						constraints.fixedNodeConstraint = layout.constraints.fixedNodeConstraint;
						constraints.alignmentConstraint = layout.constraints.alignmentConstraint;
						constraints.relativePlacementConstraint = layout.constraints.relativePlacementConstraint;
						var idToNodeMap = /* @__PURE__ */ new Map();
						var nodeIndexes = /* @__PURE__ */ new Map();
						var xCoords = [];
						var yCoords = [];
						var allNodes = layout.getAllNodes();
						var index = 0;
						for (var i = 0; i < allNodes.length; i++) {
							var node = allNodes[i];
							if (node.getChild() == null) {
								nodeIndexes.set(node.id, index++);
								xCoords.push(node.getCenterX());
								yCoords.push(node.getCenterY());
								idToNodeMap.set(node.id, node);
							}
						}
						if (constraints.relativePlacementConstraint) constraints.relativePlacementConstraint.forEach(function(constraint) {
							if (!constraint.gap && constraint.gap != 0) if (constraint.left) constraint.gap = CoSEConstants.DEFAULT_EDGE_LENGTH + idToNodeMap.get(constraint.left).getWidth() / 2 + idToNodeMap.get(constraint.right).getWidth() / 2;
							else constraint.gap = CoSEConstants.DEFAULT_EDGE_LENGTH + idToNodeMap.get(constraint.top).getHeight() / 2 + idToNodeMap.get(constraint.bottom).getHeight() / 2;
						});
						var calculatePositionDiff = function calculatePositionDiff(pos1, pos2) {
							return {
								x: pos1.x - pos2.x,
								y: pos1.y - pos2.y
							};
						};
						var calculateAvgPosition = function calculateAvgPosition(nodeIdSet) {
							var xPosSum = 0;
							var yPosSum = 0;
							nodeIdSet.forEach(function(nodeId) {
								xPosSum += xCoords[nodeIndexes.get(nodeId)];
								yPosSum += yCoords[nodeIndexes.get(nodeId)];
							});
							return {
								x: xPosSum / nodeIdSet.size,
								y: yPosSum / nodeIdSet.size
							};
						};
						var findAppropriatePositionForRelativePlacement = function findAppropriatePositionForRelativePlacement(graph, direction, fixedNodes, dummyPositions, componentSources) {
							function setUnion(setA, setB) {
								var union = new Set(setA);
								var _iteratorNormalCompletion = true;
								var _didIteratorError = false;
								var _iteratorError = void 0;
								try {
									for (var _iterator = setB[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {
										var elem = _step.value;
										union.add(elem);
									}
								} catch (err) {
									_didIteratorError = true;
									_iteratorError = err;
								} finally {
									try {
										if (!_iteratorNormalCompletion && _iterator.return) _iterator.return();
									} finally {
										if (_didIteratorError) throw _iteratorError;
									}
								}
								return union;
							}
							var inDegrees = /* @__PURE__ */ new Map();
							graph.forEach(function(value, key) {
								inDegrees.set(key, 0);
							});
							graph.forEach(function(value, key) {
								value.forEach(function(adjacent) {
									inDegrees.set(adjacent.id, inDegrees.get(adjacent.id) + 1);
								});
							});
							var positionMap = /* @__PURE__ */ new Map();
							var pastMap = /* @__PURE__ */ new Map();
							var queue = new LinkedList();
							inDegrees.forEach(function(value, key) {
								if (value == 0) {
									queue.push(key);
									if (!fixedNodes) if (direction == "horizontal") positionMap.set(key, nodeIndexes.has(key) ? xCoords[nodeIndexes.get(key)] : dummyPositions.get(key));
									else positionMap.set(key, nodeIndexes.has(key) ? yCoords[nodeIndexes.get(key)] : dummyPositions.get(key));
								} else positionMap.set(key, Number.NEGATIVE_INFINITY);
								if (fixedNodes) pastMap.set(key, new Set([key]));
							});
							if (fixedNodes) componentSources.forEach(function(component) {
								var fixedIds = [];
								component.forEach(function(nodeId) {
									if (fixedNodes.has(nodeId)) fixedIds.push(nodeId);
								});
								if (fixedIds.length > 0) {
									var position = 0;
									fixedIds.forEach(function(fixedId) {
										if (direction == "horizontal") {
											positionMap.set(fixedId, nodeIndexes.has(fixedId) ? xCoords[nodeIndexes.get(fixedId)] : dummyPositions.get(fixedId));
											position += positionMap.get(fixedId);
										} else {
											positionMap.set(fixedId, nodeIndexes.has(fixedId) ? yCoords[nodeIndexes.get(fixedId)] : dummyPositions.get(fixedId));
											position += positionMap.get(fixedId);
										}
									});
									position = position / fixedIds.length;
									component.forEach(function(nodeId) {
										if (!fixedNodes.has(nodeId)) positionMap.set(nodeId, position);
									});
								} else {
									var _position = 0;
									component.forEach(function(nodeId) {
										if (direction == "horizontal") _position += nodeIndexes.has(nodeId) ? xCoords[nodeIndexes.get(nodeId)] : dummyPositions.get(nodeId);
										else _position += nodeIndexes.has(nodeId) ? yCoords[nodeIndexes.get(nodeId)] : dummyPositions.get(nodeId);
									});
									_position = _position / component.length;
									component.forEach(function(nodeId) {
										positionMap.set(nodeId, _position);
									});
								}
							});
							var _loop = function _loop() {
								var currentNode = queue.shift();
								graph.get(currentNode).forEach(function(neighbor) {
									if (positionMap.get(neighbor.id) < positionMap.get(currentNode) + neighbor.gap) if (fixedNodes && fixedNodes.has(neighbor.id)) {
										var fixedPosition = void 0;
										if (direction == "horizontal") fixedPosition = nodeIndexes.has(neighbor.id) ? xCoords[nodeIndexes.get(neighbor.id)] : dummyPositions.get(neighbor.id);
										else fixedPosition = nodeIndexes.has(neighbor.id) ? yCoords[nodeIndexes.get(neighbor.id)] : dummyPositions.get(neighbor.id);
										positionMap.set(neighbor.id, fixedPosition);
										if (fixedPosition < positionMap.get(currentNode) + neighbor.gap) {
											var diff = positionMap.get(currentNode) + neighbor.gap - fixedPosition;
											pastMap.get(currentNode).forEach(function(nodeId) {
												positionMap.set(nodeId, positionMap.get(nodeId) - diff);
											});
										}
									} else positionMap.set(neighbor.id, positionMap.get(currentNode) + neighbor.gap);
									inDegrees.set(neighbor.id, inDegrees.get(neighbor.id) - 1);
									if (inDegrees.get(neighbor.id) == 0) queue.push(neighbor.id);
									if (fixedNodes) pastMap.set(neighbor.id, setUnion(pastMap.get(currentNode), pastMap.get(neighbor.id)));
								});
							};
							while (queue.length != 0) _loop();
							if (fixedNodes) {
								var sinkNodes = /* @__PURE__ */ new Set();
								graph.forEach(function(value, key) {
									if (value.length == 0) sinkNodes.add(key);
								});
								var _components = [];
								pastMap.forEach(function(value, key) {
									if (sinkNodes.has(key)) {
										var isFixedComponent = false;
										var _iteratorNormalCompletion2 = true;
										var _didIteratorError2 = false;
										var _iteratorError2 = void 0;
										try {
											for (var _iterator2 = value[Symbol.iterator](), _step2; !(_iteratorNormalCompletion2 = (_step2 = _iterator2.next()).done); _iteratorNormalCompletion2 = true) {
												var nodeId = _step2.value;
												if (fixedNodes.has(nodeId)) isFixedComponent = true;
											}
										} catch (err) {
											_didIteratorError2 = true;
											_iteratorError2 = err;
										} finally {
											try {
												if (!_iteratorNormalCompletion2 && _iterator2.return) _iterator2.return();
											} finally {
												if (_didIteratorError2) throw _iteratorError2;
											}
										}
										if (!isFixedComponent) {
											var isExist = false;
											var existAt = void 0;
											_components.forEach(function(component, index) {
												if (component.has([].concat(_toConsumableArray(value))[0])) {
													isExist = true;
													existAt = index;
												}
											});
											if (!isExist) _components.push(new Set(value));
											else value.forEach(function(ele) {
												_components[existAt].add(ele);
											});
										}
									}
								});
								_components.forEach(function(component, index) {
									var minBefore = Number.POSITIVE_INFINITY;
									var minAfter = Number.POSITIVE_INFINITY;
									var maxBefore = Number.NEGATIVE_INFINITY;
									var maxAfter = Number.NEGATIVE_INFINITY;
									var _iteratorNormalCompletion3 = true;
									var _didIteratorError3 = false;
									var _iteratorError3 = void 0;
									try {
										for (var _iterator3 = component[Symbol.iterator](), _step3; !(_iteratorNormalCompletion3 = (_step3 = _iterator3.next()).done); _iteratorNormalCompletion3 = true) {
											var nodeId = _step3.value;
											var posBefore = void 0;
											if (direction == "horizontal") posBefore = nodeIndexes.has(nodeId) ? xCoords[nodeIndexes.get(nodeId)] : dummyPositions.get(nodeId);
											else posBefore = nodeIndexes.has(nodeId) ? yCoords[nodeIndexes.get(nodeId)] : dummyPositions.get(nodeId);
											var posAfter = positionMap.get(nodeId);
											if (posBefore < minBefore) minBefore = posBefore;
											if (posBefore > maxBefore) maxBefore = posBefore;
											if (posAfter < minAfter) minAfter = posAfter;
											if (posAfter > maxAfter) maxAfter = posAfter;
										}
									} catch (err) {
										_didIteratorError3 = true;
										_iteratorError3 = err;
									} finally {
										try {
											if (!_iteratorNormalCompletion3 && _iterator3.return) _iterator3.return();
										} finally {
											if (_didIteratorError3) throw _iteratorError3;
										}
									}
									var diff = (minBefore + maxBefore) / 2 - (minAfter + maxAfter) / 2;
									var _iteratorNormalCompletion4 = true;
									var _didIteratorError4 = false;
									var _iteratorError4 = void 0;
									try {
										for (var _iterator4 = component[Symbol.iterator](), _step4; !(_iteratorNormalCompletion4 = (_step4 = _iterator4.next()).done); _iteratorNormalCompletion4 = true) {
											var _nodeId = _step4.value;
											positionMap.set(_nodeId, positionMap.get(_nodeId) + diff);
										}
									} catch (err) {
										_didIteratorError4 = true;
										_iteratorError4 = err;
									} finally {
										try {
											if (!_iteratorNormalCompletion4 && _iterator4.return) _iterator4.return();
										} finally {
											if (_didIteratorError4) throw _iteratorError4;
										}
									}
								});
							}
							return positionMap;
						};
						var applyReflectionForRelativePlacement = function applyReflectionForRelativePlacement(relativePlacementConstraints) {
							var reflectOnY = 0, notReflectOnY = 0;
							var reflectOnX = 0, notReflectOnX = 0;
							relativePlacementConstraints.forEach(function(constraint) {
								if (constraint.left) xCoords[nodeIndexes.get(constraint.left)] - xCoords[nodeIndexes.get(constraint.right)] >= 0 ? reflectOnY++ : notReflectOnY++;
								else yCoords[nodeIndexes.get(constraint.top)] - yCoords[nodeIndexes.get(constraint.bottom)] >= 0 ? reflectOnX++ : notReflectOnX++;
							});
							if (reflectOnY > notReflectOnY && reflectOnX > notReflectOnX) for (var _i = 0; _i < nodeIndexes.size; _i++) {
								xCoords[_i] = -1 * xCoords[_i];
								yCoords[_i] = -1 * yCoords[_i];
							}
							else if (reflectOnY > notReflectOnY) for (var _i2 = 0; _i2 < nodeIndexes.size; _i2++) xCoords[_i2] = -1 * xCoords[_i2];
							else if (reflectOnX > notReflectOnX) for (var _i3 = 0; _i3 < nodeIndexes.size; _i3++) yCoords[_i3] = -1 * yCoords[_i3];
						};
						var findComponents = function findComponents(graph) {
							var components = [];
							var queue = new LinkedList();
							var visited = /* @__PURE__ */ new Set();
							var count = 0;
							graph.forEach(function(value, key) {
								if (!visited.has(key)) {
									components[count] = [];
									var _currentNode = key;
									queue.push(_currentNode);
									visited.add(_currentNode);
									components[count].push(_currentNode);
									while (queue.length != 0) {
										_currentNode = queue.shift();
										graph.get(_currentNode).forEach(function(neighbor) {
											if (!visited.has(neighbor.id)) {
												queue.push(neighbor.id);
												visited.add(neighbor.id);
												components[count].push(neighbor.id);
											}
										});
									}
									count++;
								}
							});
							return components;
						};
						var dagToUndirected = function dagToUndirected(dag) {
							var undirected = /* @__PURE__ */ new Map();
							dag.forEach(function(value, key) {
								undirected.set(key, []);
							});
							dag.forEach(function(value, key) {
								value.forEach(function(adjacent) {
									undirected.get(key).push(adjacent);
									undirected.get(adjacent.id).push({
										id: key,
										gap: adjacent.gap,
										direction: adjacent.direction
									});
								});
							});
							return undirected;
						};
						var dagToReversed = function dagToReversed(dag) {
							var reversed = /* @__PURE__ */ new Map();
							dag.forEach(function(value, key) {
								reversed.set(key, []);
							});
							dag.forEach(function(value, key) {
								value.forEach(function(adjacent) {
									reversed.get(adjacent.id).push({
										id: key,
										gap: adjacent.gap,
										direction: adjacent.direction
									});
								});
							});
							return reversed;
						};
						/****  apply transformation to the initial draft layout to better align with constrained nodes ****/
						var targetMatrix = [];
						var sourceMatrix = [];
						var standardTransformation = false;
						var reflectionType = false;
						var fixedNodes = /* @__PURE__ */ new Set();
						var dag = /* @__PURE__ */ new Map();
						var dagUndirected = /* @__PURE__ */ new Map();
						var components = [];
						if (constraints.fixedNodeConstraint) constraints.fixedNodeConstraint.forEach(function(nodeData) {
							fixedNodes.add(nodeData.nodeId);
						});
						if (constraints.relativePlacementConstraint) {
							constraints.relativePlacementConstraint.forEach(function(constraint) {
								if (constraint.left) {
									if (dag.has(constraint.left)) dag.get(constraint.left).push({
										id: constraint.right,
										gap: constraint.gap,
										direction: "horizontal"
									});
									else dag.set(constraint.left, [{
										id: constraint.right,
										gap: constraint.gap,
										direction: "horizontal"
									}]);
									if (!dag.has(constraint.right)) dag.set(constraint.right, []);
								} else {
									if (dag.has(constraint.top)) dag.get(constraint.top).push({
										id: constraint.bottom,
										gap: constraint.gap,
										direction: "vertical"
									});
									else dag.set(constraint.top, [{
										id: constraint.bottom,
										gap: constraint.gap,
										direction: "vertical"
									}]);
									if (!dag.has(constraint.bottom)) dag.set(constraint.bottom, []);
								}
							});
							dagUndirected = dagToUndirected(dag);
							components = findComponents(dagUndirected);
						}
						if (CoSEConstants.TRANSFORM_ON_CONSTRAINT_HANDLING) {
							if (constraints.fixedNodeConstraint && constraints.fixedNodeConstraint.length > 1) {
								constraints.fixedNodeConstraint.forEach(function(nodeData, i) {
									targetMatrix[i] = [nodeData.position.x, nodeData.position.y];
									sourceMatrix[i] = [xCoords[nodeIndexes.get(nodeData.nodeId)], yCoords[nodeIndexes.get(nodeData.nodeId)]];
								});
								standardTransformation = true;
							} else if (constraints.alignmentConstraint) (function() {
								var count = 0;
								if (constraints.alignmentConstraint.vertical) {
									var verticalAlign = constraints.alignmentConstraint.vertical;
									var _loop2 = function _loop2(_i4) {
										var alignmentSet = /* @__PURE__ */ new Set();
										verticalAlign[_i4].forEach(function(nodeId) {
											alignmentSet.add(nodeId);
										});
										var intersection = new Set([].concat(_toConsumableArray(alignmentSet)).filter(function(x) {
											return fixedNodes.has(x);
										}));
										var xPos = void 0;
										if (intersection.size > 0) xPos = xCoords[nodeIndexes.get(intersection.values().next().value)];
										else xPos = calculateAvgPosition(alignmentSet).x;
										verticalAlign[_i4].forEach(function(nodeId) {
											targetMatrix[count] = [xPos, yCoords[nodeIndexes.get(nodeId)]];
											sourceMatrix[count] = [xCoords[nodeIndexes.get(nodeId)], yCoords[nodeIndexes.get(nodeId)]];
											count++;
										});
									};
									for (var _i4 = 0; _i4 < verticalAlign.length; _i4++) _loop2(_i4);
									standardTransformation = true;
								}
								if (constraints.alignmentConstraint.horizontal) {
									var horizontalAlign = constraints.alignmentConstraint.horizontal;
									var _loop3 = function _loop3(_i5) {
										var alignmentSet = /* @__PURE__ */ new Set();
										horizontalAlign[_i5].forEach(function(nodeId) {
											alignmentSet.add(nodeId);
										});
										var intersection = new Set([].concat(_toConsumableArray(alignmentSet)).filter(function(x) {
											return fixedNodes.has(x);
										}));
										var yPos = void 0;
										if (intersection.size > 0) yPos = xCoords[nodeIndexes.get(intersection.values().next().value)];
										else yPos = calculateAvgPosition(alignmentSet).y;
										horizontalAlign[_i5].forEach(function(nodeId) {
											targetMatrix[count] = [xCoords[nodeIndexes.get(nodeId)], yPos];
											sourceMatrix[count] = [xCoords[nodeIndexes.get(nodeId)], yCoords[nodeIndexes.get(nodeId)]];
											count++;
										});
									};
									for (var _i5 = 0; _i5 < horizontalAlign.length; _i5++) _loop3(_i5);
									standardTransformation = true;
								}
								if (constraints.relativePlacementConstraint) reflectionType = true;
							})();
							else if (constraints.relativePlacementConstraint) {
								var largestComponentSize = 0;
								var largestComponentIndex = 0;
								for (var _i6 = 0; _i6 < components.length; _i6++) if (components[_i6].length > largestComponentSize) {
									largestComponentSize = components[_i6].length;
									largestComponentIndex = _i6;
								}
								if (largestComponentSize < dagUndirected.size / 2) {
									applyReflectionForRelativePlacement(constraints.relativePlacementConstraint);
									standardTransformation = false;
									reflectionType = false;
								} else {
									var subGraphOnHorizontal = /* @__PURE__ */ new Map();
									var subGraphOnVertical = /* @__PURE__ */ new Map();
									var constraintsInlargestComponent = [];
									components[largestComponentIndex].forEach(function(nodeId) {
										dag.get(nodeId).forEach(function(adjacent) {
											if (adjacent.direction == "horizontal") {
												if (subGraphOnHorizontal.has(nodeId)) subGraphOnHorizontal.get(nodeId).push(adjacent);
												else subGraphOnHorizontal.set(nodeId, [adjacent]);
												if (!subGraphOnHorizontal.has(adjacent.id)) subGraphOnHorizontal.set(adjacent.id, []);
												constraintsInlargestComponent.push({
													left: nodeId,
													right: adjacent.id
												});
											} else {
												if (subGraphOnVertical.has(nodeId)) subGraphOnVertical.get(nodeId).push(adjacent);
												else subGraphOnVertical.set(nodeId, [adjacent]);
												if (!subGraphOnVertical.has(adjacent.id)) subGraphOnVertical.set(adjacent.id, []);
												constraintsInlargestComponent.push({
													top: nodeId,
													bottom: adjacent.id
												});
											}
										});
									});
									applyReflectionForRelativePlacement(constraintsInlargestComponent);
									reflectionType = false;
									var positionMapHorizontal = findAppropriatePositionForRelativePlacement(subGraphOnHorizontal, "horizontal");
									var positionMapVertical = findAppropriatePositionForRelativePlacement(subGraphOnVertical, "vertical");
									components[largestComponentIndex].forEach(function(nodeId, i) {
										sourceMatrix[i] = [xCoords[nodeIndexes.get(nodeId)], yCoords[nodeIndexes.get(nodeId)]];
										targetMatrix[i] = [];
										if (positionMapHorizontal.has(nodeId)) targetMatrix[i][0] = positionMapHorizontal.get(nodeId);
										else targetMatrix[i][0] = xCoords[nodeIndexes.get(nodeId)];
										if (positionMapVertical.has(nodeId)) targetMatrix[i][1] = positionMapVertical.get(nodeId);
										else targetMatrix[i][1] = yCoords[nodeIndexes.get(nodeId)];
									});
									standardTransformation = true;
								}
							}
							if (standardTransformation) {
								var transformationMatrix = void 0;
								var targetMatrixTranspose = Matrix.transpose(targetMatrix);
								var sourceMatrixTranspose = Matrix.transpose(sourceMatrix);
								for (var _i7 = 0; _i7 < targetMatrixTranspose.length; _i7++) {
									targetMatrixTranspose[_i7] = Matrix.multGamma(targetMatrixTranspose[_i7]);
									sourceMatrixTranspose[_i7] = Matrix.multGamma(sourceMatrixTranspose[_i7]);
								}
								var tempMatrix = Matrix.multMat(targetMatrixTranspose, Matrix.transpose(sourceMatrixTranspose));
								var SVDResult = SVD.svd(tempMatrix);
								transformationMatrix = Matrix.multMat(SVDResult.V, Matrix.transpose(SVDResult.U));
								for (var _i8 = 0; _i8 < nodeIndexes.size; _i8++) {
									var temp1 = [xCoords[_i8], yCoords[_i8]];
									var temp2 = [transformationMatrix[0][0], transformationMatrix[1][0]];
									var temp3 = [transformationMatrix[0][1], transformationMatrix[1][1]];
									xCoords[_i8] = Matrix.dotProduct(temp1, temp2);
									yCoords[_i8] = Matrix.dotProduct(temp1, temp3);
								}
								if (reflectionType) applyReflectionForRelativePlacement(constraints.relativePlacementConstraint);
							}
						}
						if (CoSEConstants.ENFORCE_CONSTRAINTS) {
							/****  enforce constraints on the transformed draft layout ****/
							if (constraints.fixedNodeConstraint && constraints.fixedNodeConstraint.length > 0) {
								var translationAmount = {
									x: 0,
									y: 0
								};
								constraints.fixedNodeConstraint.forEach(function(nodeData, i) {
									var posInTheory = {
										x: xCoords[nodeIndexes.get(nodeData.nodeId)],
										y: yCoords[nodeIndexes.get(nodeData.nodeId)]
									};
									var posDesired = nodeData.position;
									var posDiff = calculatePositionDiff(posDesired, posInTheory);
									translationAmount.x += posDiff.x;
									translationAmount.y += posDiff.y;
								});
								translationAmount.x /= constraints.fixedNodeConstraint.length;
								translationAmount.y /= constraints.fixedNodeConstraint.length;
								xCoords.forEach(function(value, i) {
									xCoords[i] += translationAmount.x;
								});
								yCoords.forEach(function(value, i) {
									yCoords[i] += translationAmount.y;
								});
								constraints.fixedNodeConstraint.forEach(function(nodeData) {
									xCoords[nodeIndexes.get(nodeData.nodeId)] = nodeData.position.x;
									yCoords[nodeIndexes.get(nodeData.nodeId)] = nodeData.position.y;
								});
							}
							if (constraints.alignmentConstraint) {
								if (constraints.alignmentConstraint.vertical) {
									var xAlign = constraints.alignmentConstraint.vertical;
									var _loop4 = function _loop4(_i9) {
										var alignmentSet = /* @__PURE__ */ new Set();
										xAlign[_i9].forEach(function(nodeId) {
											alignmentSet.add(nodeId);
										});
										var intersection = new Set([].concat(_toConsumableArray(alignmentSet)).filter(function(x) {
											return fixedNodes.has(x);
										}));
										var xPos = void 0;
										if (intersection.size > 0) xPos = xCoords[nodeIndexes.get(intersection.values().next().value)];
										else xPos = calculateAvgPosition(alignmentSet).x;
										alignmentSet.forEach(function(nodeId) {
											if (!fixedNodes.has(nodeId)) xCoords[nodeIndexes.get(nodeId)] = xPos;
										});
									};
									for (var _i9 = 0; _i9 < xAlign.length; _i9++) _loop4(_i9);
								}
								if (constraints.alignmentConstraint.horizontal) {
									var yAlign = constraints.alignmentConstraint.horizontal;
									var _loop5 = function _loop5(_i10) {
										var alignmentSet = /* @__PURE__ */ new Set();
										yAlign[_i10].forEach(function(nodeId) {
											alignmentSet.add(nodeId);
										});
										var intersection = new Set([].concat(_toConsumableArray(alignmentSet)).filter(function(x) {
											return fixedNodes.has(x);
										}));
										var yPos = void 0;
										if (intersection.size > 0) yPos = yCoords[nodeIndexes.get(intersection.values().next().value)];
										else yPos = calculateAvgPosition(alignmentSet).y;
										alignmentSet.forEach(function(nodeId) {
											if (!fixedNodes.has(nodeId)) yCoords[nodeIndexes.get(nodeId)] = yPos;
										});
									};
									for (var _i10 = 0; _i10 < yAlign.length; _i10++) _loop5(_i10);
								}
							}
							if (constraints.relativePlacementConstraint) (function() {
								var nodeToDummyForVerticalAlignment = /* @__PURE__ */ new Map();
								var nodeToDummyForHorizontalAlignment = /* @__PURE__ */ new Map();
								var dummyToNodeForVerticalAlignment = /* @__PURE__ */ new Map();
								var dummyToNodeForHorizontalAlignment = /* @__PURE__ */ new Map();
								var dummyPositionsForVerticalAlignment = /* @__PURE__ */ new Map();
								var dummyPositionsForHorizontalAlignment = /* @__PURE__ */ new Map();
								var fixedNodesOnHorizontal = /* @__PURE__ */ new Set();
								var fixedNodesOnVertical = /* @__PURE__ */ new Set();
								fixedNodes.forEach(function(nodeId) {
									fixedNodesOnHorizontal.add(nodeId);
									fixedNodesOnVertical.add(nodeId);
								});
								if (constraints.alignmentConstraint) {
									if (constraints.alignmentConstraint.vertical) {
										var verticalAlignment = constraints.alignmentConstraint.vertical;
										var _loop6 = function _loop6(_i11) {
											dummyToNodeForVerticalAlignment.set("dummy" + _i11, []);
											verticalAlignment[_i11].forEach(function(nodeId) {
												nodeToDummyForVerticalAlignment.set(nodeId, "dummy" + _i11);
												dummyToNodeForVerticalAlignment.get("dummy" + _i11).push(nodeId);
												if (fixedNodes.has(nodeId)) fixedNodesOnHorizontal.add("dummy" + _i11);
											});
											dummyPositionsForVerticalAlignment.set("dummy" + _i11, xCoords[nodeIndexes.get(verticalAlignment[_i11][0])]);
										};
										for (var _i11 = 0; _i11 < verticalAlignment.length; _i11++) _loop6(_i11);
									}
									if (constraints.alignmentConstraint.horizontal) {
										var horizontalAlignment = constraints.alignmentConstraint.horizontal;
										var _loop7 = function _loop7(_i12) {
											dummyToNodeForHorizontalAlignment.set("dummy" + _i12, []);
											horizontalAlignment[_i12].forEach(function(nodeId) {
												nodeToDummyForHorizontalAlignment.set(nodeId, "dummy" + _i12);
												dummyToNodeForHorizontalAlignment.get("dummy" + _i12).push(nodeId);
												if (fixedNodes.has(nodeId)) fixedNodesOnVertical.add("dummy" + _i12);
											});
											dummyPositionsForHorizontalAlignment.set("dummy" + _i12, yCoords[nodeIndexes.get(horizontalAlignment[_i12][0])]);
										};
										for (var _i12 = 0; _i12 < horizontalAlignment.length; _i12++) _loop7(_i12);
									}
								}
								var dagOnHorizontal = /* @__PURE__ */ new Map();
								var dagOnVertical = /* @__PURE__ */ new Map();
								var _loop8 = function _loop8(nodeId) {
									dag.get(nodeId).forEach(function(adjacent) {
										var sourceId = void 0;
										var targetNode = void 0;
										if (adjacent["direction"] == "horizontal") {
											sourceId = nodeToDummyForVerticalAlignment.get(nodeId) ? nodeToDummyForVerticalAlignment.get(nodeId) : nodeId;
											if (nodeToDummyForVerticalAlignment.get(adjacent.id)) targetNode = {
												id: nodeToDummyForVerticalAlignment.get(adjacent.id),
												gap: adjacent.gap,
												direction: adjacent.direction
											};
											else targetNode = adjacent;
											if (dagOnHorizontal.has(sourceId)) dagOnHorizontal.get(sourceId).push(targetNode);
											else dagOnHorizontal.set(sourceId, [targetNode]);
											if (!dagOnHorizontal.has(targetNode.id)) dagOnHorizontal.set(targetNode.id, []);
										} else {
											sourceId = nodeToDummyForHorizontalAlignment.get(nodeId) ? nodeToDummyForHorizontalAlignment.get(nodeId) : nodeId;
											if (nodeToDummyForHorizontalAlignment.get(adjacent.id)) targetNode = {
												id: nodeToDummyForHorizontalAlignment.get(adjacent.id),
												gap: adjacent.gap,
												direction: adjacent.direction
											};
											else targetNode = adjacent;
											if (dagOnVertical.has(sourceId)) dagOnVertical.get(sourceId).push(targetNode);
											else dagOnVertical.set(sourceId, [targetNode]);
											if (!dagOnVertical.has(targetNode.id)) dagOnVertical.set(targetNode.id, []);
										}
									});
								};
								var _iteratorNormalCompletion5 = true;
								var _didIteratorError5 = false;
								var _iteratorError5 = void 0;
								try {
									for (var _iterator5 = dag.keys()[Symbol.iterator](), _step5; !(_iteratorNormalCompletion5 = (_step5 = _iterator5.next()).done); _iteratorNormalCompletion5 = true) {
										var nodeId = _step5.value;
										_loop8(nodeId);
									}
								} catch (err) {
									_didIteratorError5 = true;
									_iteratorError5 = err;
								} finally {
									try {
										if (!_iteratorNormalCompletion5 && _iterator5.return) _iterator5.return();
									} finally {
										if (_didIteratorError5) throw _iteratorError5;
									}
								}
								var undirectedOnHorizontal = dagToUndirected(dagOnHorizontal);
								var undirectedOnVertical = dagToUndirected(dagOnVertical);
								var componentsOnHorizontal = findComponents(undirectedOnHorizontal);
								var componentsOnVertical = findComponents(undirectedOnVertical);
								var reversedDagOnHorizontal = dagToReversed(dagOnHorizontal);
								var reversedDagOnVertical = dagToReversed(dagOnVertical);
								var componentSourcesOnHorizontal = [];
								var componentSourcesOnVertical = [];
								componentsOnHorizontal.forEach(function(component, index) {
									componentSourcesOnHorizontal[index] = [];
									component.forEach(function(nodeId) {
										if (reversedDagOnHorizontal.get(nodeId).length == 0) componentSourcesOnHorizontal[index].push(nodeId);
									});
								});
								componentsOnVertical.forEach(function(component, index) {
									componentSourcesOnVertical[index] = [];
									component.forEach(function(nodeId) {
										if (reversedDagOnVertical.get(nodeId).length == 0) componentSourcesOnVertical[index].push(nodeId);
									});
								});
								var positionMapHorizontal = findAppropriatePositionForRelativePlacement(dagOnHorizontal, "horizontal", fixedNodesOnHorizontal, dummyPositionsForVerticalAlignment, componentSourcesOnHorizontal);
								var positionMapVertical = findAppropriatePositionForRelativePlacement(dagOnVertical, "vertical", fixedNodesOnVertical, dummyPositionsForHorizontalAlignment, componentSourcesOnVertical);
								var _loop9 = function _loop9(key) {
									if (dummyToNodeForVerticalAlignment.get(key)) dummyToNodeForVerticalAlignment.get(key).forEach(function(nodeId) {
										xCoords[nodeIndexes.get(nodeId)] = positionMapHorizontal.get(key);
									});
									else xCoords[nodeIndexes.get(key)] = positionMapHorizontal.get(key);
								};
								var _iteratorNormalCompletion6 = true;
								var _didIteratorError6 = false;
								var _iteratorError6 = void 0;
								try {
									for (var _iterator6 = positionMapHorizontal.keys()[Symbol.iterator](), _step6; !(_iteratorNormalCompletion6 = (_step6 = _iterator6.next()).done); _iteratorNormalCompletion6 = true) {
										var key = _step6.value;
										_loop9(key);
									}
								} catch (err) {
									_didIteratorError6 = true;
									_iteratorError6 = err;
								} finally {
									try {
										if (!_iteratorNormalCompletion6 && _iterator6.return) _iterator6.return();
									} finally {
										if (_didIteratorError6) throw _iteratorError6;
									}
								}
								var _loop10 = function _loop10(key) {
									if (dummyToNodeForHorizontalAlignment.get(key)) dummyToNodeForHorizontalAlignment.get(key).forEach(function(nodeId) {
										yCoords[nodeIndexes.get(nodeId)] = positionMapVertical.get(key);
									});
									else yCoords[nodeIndexes.get(key)] = positionMapVertical.get(key);
								};
								var _iteratorNormalCompletion7 = true;
								var _didIteratorError7 = false;
								var _iteratorError7 = void 0;
								try {
									for (var _iterator7 = positionMapVertical.keys()[Symbol.iterator](), _step7; !(_iteratorNormalCompletion7 = (_step7 = _iterator7.next()).done); _iteratorNormalCompletion7 = true) {
										var key = _step7.value;
										_loop10(key);
									}
								} catch (err) {
									_didIteratorError7 = true;
									_iteratorError7 = err;
								} finally {
									try {
										if (!_iteratorNormalCompletion7 && _iterator7.return) _iterator7.return();
									} finally {
										if (_didIteratorError7) throw _iteratorError7;
									}
								}
							})();
						}
						for (var _i13 = 0; _i13 < allNodes.length; _i13++) {
							var _node = allNodes[_i13];
							if (_node.getChild() == null) _node.setCenter(xCoords[nodeIndexes.get(_node.id)], yCoords[nodeIndexes.get(_node.id)]);
						}
					};
					module$48.exports = ConstraintHandler;
				}),
				551: ((module$49) => {
					module$49.exports = __WEBPACK_EXTERNAL_MODULE__551__;
				})
			};
			var __webpack_module_cache__ = {};
			function __webpack_require__(moduleId) {
				var cachedModule = __webpack_module_cache__[moduleId];
				if (cachedModule !== void 0) return cachedModule.exports;
				var module$50 = __webpack_module_cache__[moduleId] = { exports: {} };
				__webpack_modules__[moduleId](module$50, module$50.exports, __webpack_require__);
				return module$50.exports;
			}
			return __webpack_require__(45);
		})();
	});
}));
//#endregion
//#region node_modules/layout-base/layout-base.js
var require_layout_base = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	(function webpackUniversalModuleDefinition(root, factory) {
		if (typeof exports === "object" && typeof module === "object") module.exports = factory();
		else if (typeof define === "function" && define.amd) define([], factory);
		else if (typeof exports === "object") exports["layoutBase"] = factory();
		else root["layoutBase"] = factory();
	})(exports, function() {
		return (function(modules) {
			var installedModules = {};
			function __webpack_require__(moduleId) {
				if (installedModules[moduleId]) return installedModules[moduleId].exports;
				var module$11 = installedModules[moduleId] = {
					i: moduleId,
					l: false,
					exports: {}
				};
				modules[moduleId].call(module$11.exports, module$11, module$11.exports, __webpack_require__);
				module$11.l = true;
				return module$11.exports;
			}
			__webpack_require__.m = modules;
			__webpack_require__.c = installedModules;
			__webpack_require__.i = function(value) {
				return value;
			};
			__webpack_require__.d = function(exports$10, name, getter) {
				if (!__webpack_require__.o(exports$10, name)) Object.defineProperty(exports$10, name, {
					configurable: false,
					enumerable: true,
					get: getter
				});
			};
			__webpack_require__.n = function(module$12) {
				var getter = module$12 && module$12.__esModule ? function getDefault() {
					return module$12["default"];
				} : function getModuleExports() {
					return module$12;
				};
				__webpack_require__.d(getter, "a", getter);
				return getter;
			};
			__webpack_require__.o = function(object, property) {
				return Object.prototype.hasOwnProperty.call(object, property);
			};
			__webpack_require__.p = "";
			return __webpack_require__(__webpack_require__.s = 26);
		})([
			(function(module$13, exports$11, __webpack_require__) {
				"use strict";
				function LayoutConstants() {}
				/**
				* Layout Quality: 0:draft, 1:default, 2:proof
				*/
				LayoutConstants.QUALITY = 1;
				/**
				* Default parameters
				*/
				LayoutConstants.DEFAULT_CREATE_BENDS_AS_NEEDED = false;
				LayoutConstants.DEFAULT_INCREMENTAL = false;
				LayoutConstants.DEFAULT_ANIMATION_ON_LAYOUT = true;
				LayoutConstants.DEFAULT_ANIMATION_DURING_LAYOUT = false;
				LayoutConstants.DEFAULT_ANIMATION_PERIOD = 50;
				LayoutConstants.DEFAULT_UNIFORM_LEAF_NODE_SIZES = false;
				LayoutConstants.DEFAULT_GRAPH_MARGIN = 15;
				LayoutConstants.NODE_DIMENSIONS_INCLUDE_LABELS = false;
				LayoutConstants.SIMPLE_NODE_SIZE = 40;
				LayoutConstants.SIMPLE_NODE_HALF_SIZE = LayoutConstants.SIMPLE_NODE_SIZE / 2;
				LayoutConstants.EMPTY_COMPOUND_NODE_SIZE = 40;
				LayoutConstants.MIN_EDGE_LENGTH = 1;
				LayoutConstants.WORLD_BOUNDARY = 1e6;
				LayoutConstants.INITIAL_WORLD_BOUNDARY = LayoutConstants.WORLD_BOUNDARY / 1e3;
				LayoutConstants.WORLD_CENTER_X = 1200;
				LayoutConstants.WORLD_CENTER_Y = 900;
				module$13.exports = LayoutConstants;
			}),
			(function(module$14, exports$12, __webpack_require__) {
				"use strict";
				var LGraphObject = __webpack_require__(2);
				var IGeometry = __webpack_require__(8);
				var IMath = __webpack_require__(9);
				function LEdge(source, target, vEdge) {
					LGraphObject.call(this, vEdge);
					this.isOverlapingSourceAndTarget = false;
					this.vGraphObject = vEdge;
					this.bendpoints = [];
					this.source = source;
					this.target = target;
				}
				LEdge.prototype = Object.create(LGraphObject.prototype);
				for (var prop in LGraphObject) LEdge[prop] = LGraphObject[prop];
				LEdge.prototype.getSource = function() {
					return this.source;
				};
				LEdge.prototype.getTarget = function() {
					return this.target;
				};
				LEdge.prototype.isInterGraph = function() {
					return this.isInterGraph;
				};
				LEdge.prototype.getLength = function() {
					return this.length;
				};
				LEdge.prototype.isOverlapingSourceAndTarget = function() {
					return this.isOverlapingSourceAndTarget;
				};
				LEdge.prototype.getBendpoints = function() {
					return this.bendpoints;
				};
				LEdge.prototype.getLca = function() {
					return this.lca;
				};
				LEdge.prototype.getSourceInLca = function() {
					return this.sourceInLca;
				};
				LEdge.prototype.getTargetInLca = function() {
					return this.targetInLca;
				};
				LEdge.prototype.getOtherEnd = function(node) {
					if (this.source === node) return this.target;
					else if (this.target === node) return this.source;
					else throw "Node is not incident with this edge";
				};
				LEdge.prototype.getOtherEndInGraph = function(node, graph) {
					var otherEnd = this.getOtherEnd(node);
					var root = graph.getGraphManager().getRoot();
					while (true) {
						if (otherEnd.getOwner() == graph) return otherEnd;
						if (otherEnd.getOwner() == root) break;
						otherEnd = otherEnd.getOwner().getParent();
					}
					return null;
				};
				LEdge.prototype.updateLength = function() {
					var clipPointCoordinates = new Array(4);
					this.isOverlapingSourceAndTarget = IGeometry.getIntersection(this.target.getRect(), this.source.getRect(), clipPointCoordinates);
					if (!this.isOverlapingSourceAndTarget) {
						this.lengthX = clipPointCoordinates[0] - clipPointCoordinates[2];
						this.lengthY = clipPointCoordinates[1] - clipPointCoordinates[3];
						if (Math.abs(this.lengthX) < 1) this.lengthX = IMath.sign(this.lengthX);
						if (Math.abs(this.lengthY) < 1) this.lengthY = IMath.sign(this.lengthY);
						this.length = Math.sqrt(this.lengthX * this.lengthX + this.lengthY * this.lengthY);
					}
				};
				LEdge.prototype.updateLengthSimple = function() {
					this.lengthX = this.target.getCenterX() - this.source.getCenterX();
					this.lengthY = this.target.getCenterY() - this.source.getCenterY();
					if (Math.abs(this.lengthX) < 1) this.lengthX = IMath.sign(this.lengthX);
					if (Math.abs(this.lengthY) < 1) this.lengthY = IMath.sign(this.lengthY);
					this.length = Math.sqrt(this.lengthX * this.lengthX + this.lengthY * this.lengthY);
				};
				module$14.exports = LEdge;
			}),
			(function(module$15, exports$13, __webpack_require__) {
				"use strict";
				function LGraphObject(vGraphObject) {
					this.vGraphObject = vGraphObject;
				}
				module$15.exports = LGraphObject;
			}),
			(function(module$16, exports$14, __webpack_require__) {
				"use strict";
				var LGraphObject = __webpack_require__(2);
				var Integer = __webpack_require__(10);
				var RectangleD = __webpack_require__(13);
				var LayoutConstants = __webpack_require__(0);
				var RandomSeed = __webpack_require__(16);
				var PointD = __webpack_require__(4);
				function LNode(gm, loc, size, vNode) {
					if (size == null && vNode == null) vNode = loc;
					LGraphObject.call(this, vNode);
					if (gm.graphManager != null) gm = gm.graphManager;
					this.estimatedSize = Integer.MIN_VALUE;
					this.inclusionTreeDepth = Integer.MAX_VALUE;
					this.vGraphObject = vNode;
					this.edges = [];
					this.graphManager = gm;
					if (size != null && loc != null) this.rect = new RectangleD(loc.x, loc.y, size.width, size.height);
					else this.rect = new RectangleD();
				}
				LNode.prototype = Object.create(LGraphObject.prototype);
				for (var prop in LGraphObject) LNode[prop] = LGraphObject[prop];
				LNode.prototype.getEdges = function() {
					return this.edges;
				};
				LNode.prototype.getChild = function() {
					return this.child;
				};
				LNode.prototype.getOwner = function() {
					return this.owner;
				};
				LNode.prototype.getWidth = function() {
					return this.rect.width;
				};
				LNode.prototype.setWidth = function(width) {
					this.rect.width = width;
				};
				LNode.prototype.getHeight = function() {
					return this.rect.height;
				};
				LNode.prototype.setHeight = function(height) {
					this.rect.height = height;
				};
				LNode.prototype.getCenterX = function() {
					return this.rect.x + this.rect.width / 2;
				};
				LNode.prototype.getCenterY = function() {
					return this.rect.y + this.rect.height / 2;
				};
				LNode.prototype.getCenter = function() {
					return new PointD(this.rect.x + this.rect.width / 2, this.rect.y + this.rect.height / 2);
				};
				LNode.prototype.getLocation = function() {
					return new PointD(this.rect.x, this.rect.y);
				};
				LNode.prototype.getRect = function() {
					return this.rect;
				};
				LNode.prototype.getDiagonal = function() {
					return Math.sqrt(this.rect.width * this.rect.width + this.rect.height * this.rect.height);
				};
				/**
				* This method returns half the diagonal length of this node.
				*/
				LNode.prototype.getHalfTheDiagonal = function() {
					return Math.sqrt(this.rect.height * this.rect.height + this.rect.width * this.rect.width) / 2;
				};
				LNode.prototype.setRect = function(upperLeft, dimension) {
					this.rect.x = upperLeft.x;
					this.rect.y = upperLeft.y;
					this.rect.width = dimension.width;
					this.rect.height = dimension.height;
				};
				LNode.prototype.setCenter = function(cx, cy) {
					this.rect.x = cx - this.rect.width / 2;
					this.rect.y = cy - this.rect.height / 2;
				};
				LNode.prototype.setLocation = function(x, y) {
					this.rect.x = x;
					this.rect.y = y;
				};
				LNode.prototype.moveBy = function(dx, dy) {
					this.rect.x += dx;
					this.rect.y += dy;
				};
				LNode.prototype.getEdgeListToNode = function(to) {
					var edgeList = [];
					var self = this;
					self.edges.forEach(function(edge) {
						if (edge.target == to) {
							if (edge.source != self) throw "Incorrect edge source!";
							edgeList.push(edge);
						}
					});
					return edgeList;
				};
				LNode.prototype.getEdgesBetween = function(other) {
					var edgeList = [];
					var self = this;
					self.edges.forEach(function(edge) {
						if (!(edge.source == self || edge.target == self)) throw "Incorrect edge source and/or target";
						if (edge.target == other || edge.source == other) edgeList.push(edge);
					});
					return edgeList;
				};
				LNode.prototype.getNeighborsList = function() {
					var neighbors = /* @__PURE__ */ new Set();
					var self = this;
					self.edges.forEach(function(edge) {
						if (edge.source == self) neighbors.add(edge.target);
						else {
							if (edge.target != self) throw "Incorrect incidency!";
							neighbors.add(edge.source);
						}
					});
					return neighbors;
				};
				LNode.prototype.withChildren = function() {
					var withNeighborsList = /* @__PURE__ */ new Set();
					var childNode;
					var children;
					withNeighborsList.add(this);
					if (this.child != null) {
						var nodes = this.child.getNodes();
						for (var i = 0; i < nodes.length; i++) {
							childNode = nodes[i];
							children = childNode.withChildren();
							children.forEach(function(node) {
								withNeighborsList.add(node);
							});
						}
					}
					return withNeighborsList;
				};
				LNode.prototype.getNoOfChildren = function() {
					var noOfChildren = 0;
					var childNode;
					if (this.child == null) noOfChildren = 1;
					else {
						var nodes = this.child.getNodes();
						for (var i = 0; i < nodes.length; i++) {
							childNode = nodes[i];
							noOfChildren += childNode.getNoOfChildren();
						}
					}
					if (noOfChildren == 0) noOfChildren = 1;
					return noOfChildren;
				};
				LNode.prototype.getEstimatedSize = function() {
					if (this.estimatedSize == Integer.MIN_VALUE) throw "assert failed";
					return this.estimatedSize;
				};
				LNode.prototype.calcEstimatedSize = function() {
					if (this.child == null) return this.estimatedSize = (this.rect.width + this.rect.height) / 2;
					else {
						this.estimatedSize = this.child.calcEstimatedSize();
						this.rect.width = this.estimatedSize;
						this.rect.height = this.estimatedSize;
						return this.estimatedSize;
					}
				};
				LNode.prototype.scatter = function() {
					var randomCenterX;
					var randomCenterY;
					var minX = -LayoutConstants.INITIAL_WORLD_BOUNDARY;
					var maxX = LayoutConstants.INITIAL_WORLD_BOUNDARY;
					randomCenterX = LayoutConstants.WORLD_CENTER_X + RandomSeed.nextDouble() * (maxX - minX) + minX;
					var minY = -LayoutConstants.INITIAL_WORLD_BOUNDARY;
					var maxY = LayoutConstants.INITIAL_WORLD_BOUNDARY;
					randomCenterY = LayoutConstants.WORLD_CENTER_Y + RandomSeed.nextDouble() * (maxY - minY) + minY;
					this.rect.x = randomCenterX;
					this.rect.y = randomCenterY;
				};
				LNode.prototype.updateBounds = function() {
					if (this.getChild() == null) throw "assert failed";
					if (this.getChild().getNodes().length != 0) {
						var childGraph = this.getChild();
						childGraph.updateBounds(true);
						this.rect.x = childGraph.getLeft();
						this.rect.y = childGraph.getTop();
						this.setWidth(childGraph.getRight() - childGraph.getLeft());
						this.setHeight(childGraph.getBottom() - childGraph.getTop());
						if (LayoutConstants.NODE_DIMENSIONS_INCLUDE_LABELS) {
							var width = childGraph.getRight() - childGraph.getLeft();
							var height = childGraph.getBottom() - childGraph.getTop();
							if (this.labelWidth > width) {
								this.rect.x -= (this.labelWidth - width) / 2;
								this.setWidth(this.labelWidth);
							}
							if (this.labelHeight > height) {
								if (this.labelPos == "center") this.rect.y -= (this.labelHeight - height) / 2;
								else if (this.labelPos == "top") this.rect.y -= this.labelHeight - height;
								this.setHeight(this.labelHeight);
							}
						}
					}
				};
				LNode.prototype.getInclusionTreeDepth = function() {
					if (this.inclusionTreeDepth == Integer.MAX_VALUE) throw "assert failed";
					return this.inclusionTreeDepth;
				};
				LNode.prototype.transform = function(trans) {
					var left = this.rect.x;
					if (left > LayoutConstants.WORLD_BOUNDARY) left = LayoutConstants.WORLD_BOUNDARY;
					else if (left < -LayoutConstants.WORLD_BOUNDARY) left = -LayoutConstants.WORLD_BOUNDARY;
					var top = this.rect.y;
					if (top > LayoutConstants.WORLD_BOUNDARY) top = LayoutConstants.WORLD_BOUNDARY;
					else if (top < -LayoutConstants.WORLD_BOUNDARY) top = -LayoutConstants.WORLD_BOUNDARY;
					var leftTop = new PointD(left, top);
					var vLeftTop = trans.inverseTransformPoint(leftTop);
					this.setLocation(vLeftTop.x, vLeftTop.y);
				};
				LNode.prototype.getLeft = function() {
					return this.rect.x;
				};
				LNode.prototype.getRight = function() {
					return this.rect.x + this.rect.width;
				};
				LNode.prototype.getTop = function() {
					return this.rect.y;
				};
				LNode.prototype.getBottom = function() {
					return this.rect.y + this.rect.height;
				};
				LNode.prototype.getParent = function() {
					if (this.owner == null) return null;
					return this.owner.getParent();
				};
				module$16.exports = LNode;
			}),
			(function(module$17, exports$15, __webpack_require__) {
				"use strict";
				function PointD(x, y) {
					if (x == null && y == null) {
						this.x = 0;
						this.y = 0;
					} else {
						this.x = x;
						this.y = y;
					}
				}
				PointD.prototype.getX = function() {
					return this.x;
				};
				PointD.prototype.getY = function() {
					return this.y;
				};
				PointD.prototype.setX = function(x) {
					this.x = x;
				};
				PointD.prototype.setY = function(y) {
					this.y = y;
				};
				PointD.prototype.getDifference = function(pt) {
					return new DimensionD(this.x - pt.x, this.y - pt.y);
				};
				PointD.prototype.getCopy = function() {
					return new PointD(this.x, this.y);
				};
				PointD.prototype.translate = function(dim) {
					this.x += dim.width;
					this.y += dim.height;
					return this;
				};
				module$17.exports = PointD;
			}),
			(function(module$18, exports$16, __webpack_require__) {
				"use strict";
				var LGraphObject = __webpack_require__(2);
				var Integer = __webpack_require__(10);
				var LayoutConstants = __webpack_require__(0);
				var LGraphManager = __webpack_require__(6);
				var LNode = __webpack_require__(3);
				var LEdge = __webpack_require__(1);
				var RectangleD = __webpack_require__(13);
				var Point = __webpack_require__(12);
				var LinkedList = __webpack_require__(11);
				function LGraph(parent, obj2, vGraph) {
					LGraphObject.call(this, vGraph);
					this.estimatedSize = Integer.MIN_VALUE;
					this.margin = LayoutConstants.DEFAULT_GRAPH_MARGIN;
					this.edges = [];
					this.nodes = [];
					this.isConnected = false;
					this.parent = parent;
					if (obj2 != null && obj2 instanceof LGraphManager) this.graphManager = obj2;
					else if (obj2 != null && obj2 instanceof Layout) this.graphManager = obj2.graphManager;
				}
				LGraph.prototype = Object.create(LGraphObject.prototype);
				for (var prop in LGraphObject) LGraph[prop] = LGraphObject[prop];
				LGraph.prototype.getNodes = function() {
					return this.nodes;
				};
				LGraph.prototype.getEdges = function() {
					return this.edges;
				};
				LGraph.prototype.getGraphManager = function() {
					return this.graphManager;
				};
				LGraph.prototype.getParent = function() {
					return this.parent;
				};
				LGraph.prototype.getLeft = function() {
					return this.left;
				};
				LGraph.prototype.getRight = function() {
					return this.right;
				};
				LGraph.prototype.getTop = function() {
					return this.top;
				};
				LGraph.prototype.getBottom = function() {
					return this.bottom;
				};
				LGraph.prototype.isConnected = function() {
					return this.isConnected;
				};
				LGraph.prototype.add = function(obj1, sourceNode, targetNode) {
					if (sourceNode == null && targetNode == null) {
						var newNode = obj1;
						if (this.graphManager == null) throw "Graph has no graph mgr!";
						if (this.getNodes().indexOf(newNode) > -1) throw "Node already in graph!";
						newNode.owner = this;
						this.getNodes().push(newNode);
						return newNode;
					} else {
						var newEdge = obj1;
						if (!(this.getNodes().indexOf(sourceNode) > -1 && this.getNodes().indexOf(targetNode) > -1)) throw "Source or target not in graph!";
						if (!(sourceNode.owner == targetNode.owner && sourceNode.owner == this)) throw "Both owners must be this graph!";
						if (sourceNode.owner != targetNode.owner) return null;
						newEdge.source = sourceNode;
						newEdge.target = targetNode;
						newEdge.isInterGraph = false;
						this.getEdges().push(newEdge);
						sourceNode.edges.push(newEdge);
						if (targetNode != sourceNode) targetNode.edges.push(newEdge);
						return newEdge;
					}
				};
				LGraph.prototype.remove = function(obj) {
					var node = obj;
					if (obj instanceof LNode) {
						if (node == null) throw "Node is null!";
						if (!(node.owner != null && node.owner == this)) throw "Owner graph is invalid!";
						if (this.graphManager == null) throw "Owner graph manager is invalid!";
						var edgesToBeRemoved = node.edges.slice();
						var edge;
						var s = edgesToBeRemoved.length;
						for (var i = 0; i < s; i++) {
							edge = edgesToBeRemoved[i];
							if (edge.isInterGraph) this.graphManager.remove(edge);
							else edge.source.owner.remove(edge);
						}
						var index = this.nodes.indexOf(node);
						if (index == -1) throw "Node not in owner node list!";
						this.nodes.splice(index, 1);
					} else if (obj instanceof LEdge) {
						var edge = obj;
						if (edge == null) throw "Edge is null!";
						if (!(edge.source != null && edge.target != null)) throw "Source and/or target is null!";
						if (!(edge.source.owner != null && edge.target.owner != null && edge.source.owner == this && edge.target.owner == this)) throw "Source and/or target owner is invalid!";
						var sourceIndex = edge.source.edges.indexOf(edge);
						var targetIndex = edge.target.edges.indexOf(edge);
						if (!(sourceIndex > -1 && targetIndex > -1)) throw "Source and/or target doesn't know this edge!";
						edge.source.edges.splice(sourceIndex, 1);
						if (edge.target != edge.source) edge.target.edges.splice(targetIndex, 1);
						var index = edge.source.owner.getEdges().indexOf(edge);
						if (index == -1) throw "Not in owner's edge list!";
						edge.source.owner.getEdges().splice(index, 1);
					}
				};
				LGraph.prototype.updateLeftTop = function() {
					var top = Integer.MAX_VALUE;
					var left = Integer.MAX_VALUE;
					var nodeTop;
					var nodeLeft;
					var margin;
					var nodes = this.getNodes();
					var s = nodes.length;
					for (var i = 0; i < s; i++) {
						var lNode = nodes[i];
						nodeTop = lNode.getTop();
						nodeLeft = lNode.getLeft();
						if (top > nodeTop) top = nodeTop;
						if (left > nodeLeft) left = nodeLeft;
					}
					if (top == Integer.MAX_VALUE) return null;
					if (nodes[0].getParent().paddingLeft != void 0) margin = nodes[0].getParent().paddingLeft;
					else margin = this.margin;
					this.left = left - margin;
					this.top = top - margin;
					return new Point(this.left, this.top);
				};
				LGraph.prototype.updateBounds = function(recursive) {
					var left = Integer.MAX_VALUE;
					var right = -Integer.MAX_VALUE;
					var top = Integer.MAX_VALUE;
					var bottom = -Integer.MAX_VALUE;
					var nodeLeft;
					var nodeRight;
					var nodeTop;
					var nodeBottom;
					var margin;
					var nodes = this.nodes;
					var s = nodes.length;
					for (var i = 0; i < s; i++) {
						var lNode = nodes[i];
						if (recursive && lNode.child != null) lNode.updateBounds();
						nodeLeft = lNode.getLeft();
						nodeRight = lNode.getRight();
						nodeTop = lNode.getTop();
						nodeBottom = lNode.getBottom();
						if (left > nodeLeft) left = nodeLeft;
						if (right < nodeRight) right = nodeRight;
						if (top > nodeTop) top = nodeTop;
						if (bottom < nodeBottom) bottom = nodeBottom;
					}
					var boundingRect = new RectangleD(left, top, right - left, bottom - top);
					if (left == Integer.MAX_VALUE) {
						this.left = this.parent.getLeft();
						this.right = this.parent.getRight();
						this.top = this.parent.getTop();
						this.bottom = this.parent.getBottom();
					}
					if (nodes[0].getParent().paddingLeft != void 0) margin = nodes[0].getParent().paddingLeft;
					else margin = this.margin;
					this.left = boundingRect.x - margin;
					this.right = boundingRect.x + boundingRect.width + margin;
					this.top = boundingRect.y - margin;
					this.bottom = boundingRect.y + boundingRect.height + margin;
				};
				LGraph.calculateBounds = function(nodes) {
					var left = Integer.MAX_VALUE;
					var right = -Integer.MAX_VALUE;
					var top = Integer.MAX_VALUE;
					var bottom = -Integer.MAX_VALUE;
					var nodeLeft;
					var nodeRight;
					var nodeTop;
					var nodeBottom;
					var s = nodes.length;
					for (var i = 0; i < s; i++) {
						var lNode = nodes[i];
						nodeLeft = lNode.getLeft();
						nodeRight = lNode.getRight();
						nodeTop = lNode.getTop();
						nodeBottom = lNode.getBottom();
						if (left > nodeLeft) left = nodeLeft;
						if (right < nodeRight) right = nodeRight;
						if (top > nodeTop) top = nodeTop;
						if (bottom < nodeBottom) bottom = nodeBottom;
					}
					return new RectangleD(left, top, right - left, bottom - top);
				};
				LGraph.prototype.getInclusionTreeDepth = function() {
					if (this == this.graphManager.getRoot()) return 1;
					else return this.parent.getInclusionTreeDepth();
				};
				LGraph.prototype.getEstimatedSize = function() {
					if (this.estimatedSize == Integer.MIN_VALUE) throw "assert failed";
					return this.estimatedSize;
				};
				LGraph.prototype.calcEstimatedSize = function() {
					var size = 0;
					var nodes = this.nodes;
					var s = nodes.length;
					for (var i = 0; i < s; i++) {
						var lNode = nodes[i];
						size += lNode.calcEstimatedSize();
					}
					if (size == 0) this.estimatedSize = LayoutConstants.EMPTY_COMPOUND_NODE_SIZE;
					else this.estimatedSize = size / Math.sqrt(this.nodes.length);
					return this.estimatedSize;
				};
				LGraph.prototype.updateConnected = function() {
					var self = this;
					if (this.nodes.length == 0) {
						this.isConnected = true;
						return;
					}
					var queue = new LinkedList();
					var visited = /* @__PURE__ */ new Set();
					var currentNode = this.nodes[0];
					var neighborEdges;
					var currentNeighbor;
					currentNode.withChildren().forEach(function(node) {
						queue.push(node);
						visited.add(node);
					});
					while (queue.length !== 0) {
						currentNode = queue.shift();
						neighborEdges = currentNode.getEdges();
						var size = neighborEdges.length;
						for (var i = 0; i < size; i++) {
							currentNeighbor = neighborEdges[i].getOtherEndInGraph(currentNode, this);
							if (currentNeighbor != null && !visited.has(currentNeighbor)) currentNeighbor.withChildren().forEach(function(node) {
								queue.push(node);
								visited.add(node);
							});
						}
					}
					this.isConnected = false;
					if (visited.size >= this.nodes.length) {
						var noOfVisitedInThisGraph = 0;
						visited.forEach(function(visitedNode) {
							if (visitedNode.owner == self) noOfVisitedInThisGraph++;
						});
						if (noOfVisitedInThisGraph == this.nodes.length) this.isConnected = true;
					}
				};
				module$18.exports = LGraph;
			}),
			(function(module$19, exports$17, __webpack_require__) {
				"use strict";
				var LGraph;
				var LEdge = __webpack_require__(1);
				function LGraphManager(layout) {
					LGraph = __webpack_require__(5);
					this.layout = layout;
					this.graphs = [];
					this.edges = [];
				}
				LGraphManager.prototype.addRoot = function() {
					var ngraph = this.layout.newGraph();
					var nnode = this.layout.newNode(null);
					var root = this.add(ngraph, nnode);
					this.setRootGraph(root);
					return this.rootGraph;
				};
				LGraphManager.prototype.add = function(newGraph, parentNode, newEdge, sourceNode, targetNode) {
					if (newEdge == null && sourceNode == null && targetNode == null) {
						if (newGraph == null) throw "Graph is null!";
						if (parentNode == null) throw "Parent node is null!";
						if (this.graphs.indexOf(newGraph) > -1) throw "Graph already in this graph mgr!";
						this.graphs.push(newGraph);
						if (newGraph.parent != null) throw "Already has a parent!";
						if (parentNode.child != null) throw "Already has a child!";
						newGraph.parent = parentNode;
						parentNode.child = newGraph;
						return newGraph;
					} else {
						targetNode = newEdge;
						sourceNode = parentNode;
						newEdge = newGraph;
						var sourceGraph = sourceNode.getOwner();
						var targetGraph = targetNode.getOwner();
						if (!(sourceGraph != null && sourceGraph.getGraphManager() == this)) throw "Source not in this graph mgr!";
						if (!(targetGraph != null && targetGraph.getGraphManager() == this)) throw "Target not in this graph mgr!";
						if (sourceGraph == targetGraph) {
							newEdge.isInterGraph = false;
							return sourceGraph.add(newEdge, sourceNode, targetNode);
						} else {
							newEdge.isInterGraph = true;
							newEdge.source = sourceNode;
							newEdge.target = targetNode;
							if (this.edges.indexOf(newEdge) > -1) throw "Edge already in inter-graph edge list!";
							this.edges.push(newEdge);
							if (!(newEdge.source != null && newEdge.target != null)) throw "Edge source and/or target is null!";
							if (!(newEdge.source.edges.indexOf(newEdge) == -1 && newEdge.target.edges.indexOf(newEdge) == -1)) throw "Edge already in source and/or target incidency list!";
							newEdge.source.edges.push(newEdge);
							newEdge.target.edges.push(newEdge);
							return newEdge;
						}
					}
				};
				LGraphManager.prototype.remove = function(lObj) {
					if (lObj instanceof LGraph) {
						var graph = lObj;
						if (graph.getGraphManager() != this) throw "Graph not in this graph mgr";
						if (!(graph == this.rootGraph || graph.parent != null && graph.parent.graphManager == this)) throw "Invalid parent node!";
						var edgesToBeRemoved = [];
						edgesToBeRemoved = edgesToBeRemoved.concat(graph.getEdges());
						var edge;
						var s = edgesToBeRemoved.length;
						for (var i = 0; i < s; i++) {
							edge = edgesToBeRemoved[i];
							graph.remove(edge);
						}
						var nodesToBeRemoved = [];
						nodesToBeRemoved = nodesToBeRemoved.concat(graph.getNodes());
						var node;
						s = nodesToBeRemoved.length;
						for (var i = 0; i < s; i++) {
							node = nodesToBeRemoved[i];
							graph.remove(node);
						}
						if (graph == this.rootGraph) this.setRootGraph(null);
						var index = this.graphs.indexOf(graph);
						this.graphs.splice(index, 1);
						graph.parent = null;
					} else if (lObj instanceof LEdge) {
						edge = lObj;
						if (edge == null) throw "Edge is null!";
						if (!edge.isInterGraph) throw "Not an inter-graph edge!";
						if (!(edge.source != null && edge.target != null)) throw "Source and/or target is null!";
						if (!(edge.source.edges.indexOf(edge) != -1 && edge.target.edges.indexOf(edge) != -1)) throw "Source and/or target doesn't know this edge!";
						var index = edge.source.edges.indexOf(edge);
						edge.source.edges.splice(index, 1);
						index = edge.target.edges.indexOf(edge);
						edge.target.edges.splice(index, 1);
						if (!(edge.source.owner != null && edge.source.owner.getGraphManager() != null)) throw "Edge owner graph or owner graph manager is null!";
						if (edge.source.owner.getGraphManager().edges.indexOf(edge) == -1) throw "Not in owner graph manager's edge list!";
						var index = edge.source.owner.getGraphManager().edges.indexOf(edge);
						edge.source.owner.getGraphManager().edges.splice(index, 1);
					}
				};
				LGraphManager.prototype.updateBounds = function() {
					this.rootGraph.updateBounds(true);
				};
				LGraphManager.prototype.getGraphs = function() {
					return this.graphs;
				};
				LGraphManager.prototype.getAllNodes = function() {
					if (this.allNodes == null) {
						var nodeList = [];
						var graphs = this.getGraphs();
						var s = graphs.length;
						for (var i = 0; i < s; i++) nodeList = nodeList.concat(graphs[i].getNodes());
						this.allNodes = nodeList;
					}
					return this.allNodes;
				};
				LGraphManager.prototype.resetAllNodes = function() {
					this.allNodes = null;
				};
				LGraphManager.prototype.resetAllEdges = function() {
					this.allEdges = null;
				};
				LGraphManager.prototype.resetAllNodesToApplyGravitation = function() {
					this.allNodesToApplyGravitation = null;
				};
				LGraphManager.prototype.getAllEdges = function() {
					if (this.allEdges == null) {
						var edgeList = [];
						var graphs = this.getGraphs();
						graphs.length;
						for (var i = 0; i < graphs.length; i++) edgeList = edgeList.concat(graphs[i].getEdges());
						edgeList = edgeList.concat(this.edges);
						this.allEdges = edgeList;
					}
					return this.allEdges;
				};
				LGraphManager.prototype.getAllNodesToApplyGravitation = function() {
					return this.allNodesToApplyGravitation;
				};
				LGraphManager.prototype.setAllNodesToApplyGravitation = function(nodeList) {
					if (this.allNodesToApplyGravitation != null) throw "assert failed";
					this.allNodesToApplyGravitation = nodeList;
				};
				LGraphManager.prototype.getRoot = function() {
					return this.rootGraph;
				};
				LGraphManager.prototype.setRootGraph = function(graph) {
					if (graph.getGraphManager() != this) throw "Root not in this graph mgr!";
					this.rootGraph = graph;
					if (graph.parent == null) graph.parent = this.layout.newNode("Root node");
				};
				LGraphManager.prototype.getLayout = function() {
					return this.layout;
				};
				LGraphManager.prototype.isOneAncestorOfOther = function(firstNode, secondNode) {
					if (!(firstNode != null && secondNode != null)) throw "assert failed";
					if (firstNode == secondNode) return true;
					var ownerGraph = firstNode.getOwner();
					var parentNode;
					do {
						parentNode = ownerGraph.getParent();
						if (parentNode == null) break;
						if (parentNode == secondNode) return true;
						ownerGraph = parentNode.getOwner();
						if (ownerGraph == null) break;
					} while (true);
					ownerGraph = secondNode.getOwner();
					do {
						parentNode = ownerGraph.getParent();
						if (parentNode == null) break;
						if (parentNode == firstNode) return true;
						ownerGraph = parentNode.getOwner();
						if (ownerGraph == null) break;
					} while (true);
					return false;
				};
				LGraphManager.prototype.calcLowestCommonAncestors = function() {
					var edge;
					var sourceNode;
					var targetNode;
					var sourceAncestorGraph;
					var targetAncestorGraph;
					var edges = this.getAllEdges();
					var s = edges.length;
					for (var i = 0; i < s; i++) {
						edge = edges[i];
						sourceNode = edge.source;
						targetNode = edge.target;
						edge.lca = null;
						edge.sourceInLca = sourceNode;
						edge.targetInLca = targetNode;
						if (sourceNode == targetNode) {
							edge.lca = sourceNode.getOwner();
							continue;
						}
						sourceAncestorGraph = sourceNode.getOwner();
						while (edge.lca == null) {
							edge.targetInLca = targetNode;
							targetAncestorGraph = targetNode.getOwner();
							while (edge.lca == null) {
								if (targetAncestorGraph == sourceAncestorGraph) {
									edge.lca = targetAncestorGraph;
									break;
								}
								if (targetAncestorGraph == this.rootGraph) break;
								if (edge.lca != null) throw "assert failed";
								edge.targetInLca = targetAncestorGraph.getParent();
								targetAncestorGraph = edge.targetInLca.getOwner();
							}
							if (sourceAncestorGraph == this.rootGraph) break;
							if (edge.lca == null) {
								edge.sourceInLca = sourceAncestorGraph.getParent();
								sourceAncestorGraph = edge.sourceInLca.getOwner();
							}
						}
						if (edge.lca == null) throw "assert failed";
					}
				};
				LGraphManager.prototype.calcLowestCommonAncestor = function(firstNode, secondNode) {
					if (firstNode == secondNode) return firstNode.getOwner();
					var firstOwnerGraph = firstNode.getOwner();
					do {
						if (firstOwnerGraph == null) break;
						var secondOwnerGraph = secondNode.getOwner();
						do {
							if (secondOwnerGraph == null) break;
							if (secondOwnerGraph == firstOwnerGraph) return secondOwnerGraph;
							secondOwnerGraph = secondOwnerGraph.getParent().getOwner();
						} while (true);
						firstOwnerGraph = firstOwnerGraph.getParent().getOwner();
					} while (true);
					return firstOwnerGraph;
				};
				LGraphManager.prototype.calcInclusionTreeDepths = function(graph, depth) {
					if (graph == null && depth == null) {
						graph = this.rootGraph;
						depth = 1;
					}
					var node;
					var nodes = graph.getNodes();
					var s = nodes.length;
					for (var i = 0; i < s; i++) {
						node = nodes[i];
						node.inclusionTreeDepth = depth;
						if (node.child != null) this.calcInclusionTreeDepths(node.child, depth + 1);
					}
				};
				LGraphManager.prototype.includesInvalidEdge = function() {
					var edge;
					var s = this.edges.length;
					for (var i = 0; i < s; i++) {
						edge = this.edges[i];
						if (this.isOneAncestorOfOther(edge.source, edge.target)) return true;
					}
					return false;
				};
				module$19.exports = LGraphManager;
			}),
			(function(module$20, exports$18, __webpack_require__) {
				"use strict";
				var LayoutConstants = __webpack_require__(0);
				function FDLayoutConstants() {}
				for (var prop in LayoutConstants) FDLayoutConstants[prop] = LayoutConstants[prop];
				FDLayoutConstants.MAX_ITERATIONS = 2500;
				FDLayoutConstants.DEFAULT_EDGE_LENGTH = 50;
				FDLayoutConstants.DEFAULT_SPRING_STRENGTH = .45;
				FDLayoutConstants.DEFAULT_REPULSION_STRENGTH = 4500;
				FDLayoutConstants.DEFAULT_GRAVITY_STRENGTH = .4;
				FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_STRENGTH = 1;
				FDLayoutConstants.DEFAULT_GRAVITY_RANGE_FACTOR = 3.8;
				FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_RANGE_FACTOR = 1.5;
				FDLayoutConstants.DEFAULT_USE_SMART_IDEAL_EDGE_LENGTH_CALCULATION = true;
				FDLayoutConstants.DEFAULT_USE_SMART_REPULSION_RANGE_CALCULATION = true;
				FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL = .3;
				FDLayoutConstants.COOLING_ADAPTATION_FACTOR = .33;
				FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT = 1e3;
				FDLayoutConstants.ADAPTATION_UPPER_NODE_LIMIT = 5e3;
				FDLayoutConstants.MAX_NODE_DISPLACEMENT_INCREMENTAL = 100;
				FDLayoutConstants.MAX_NODE_DISPLACEMENT = FDLayoutConstants.MAX_NODE_DISPLACEMENT_INCREMENTAL * 3;
				FDLayoutConstants.MIN_REPULSION_DIST = FDLayoutConstants.DEFAULT_EDGE_LENGTH / 10;
				FDLayoutConstants.CONVERGENCE_CHECK_PERIOD = 100;
				FDLayoutConstants.PER_LEVEL_IDEAL_EDGE_LENGTH_FACTOR = .1;
				FDLayoutConstants.MIN_EDGE_LENGTH = 1;
				FDLayoutConstants.GRID_CALCULATION_CHECK_PERIOD = 10;
				module$20.exports = FDLayoutConstants;
			}),
			(function(module$21, exports$19, __webpack_require__) {
				"use strict";
				/**
				* This class maintains a list of static geometry related utility methods.
				*
				*
				* Copyright: i-Vis Research Group, Bilkent University, 2007 - present
				*/
				var Point = __webpack_require__(12);
				function IGeometry() {}
				/**
				* This method calculates *half* the amount in x and y directions of the two
				* input rectangles needed to separate them keeping their respective
				* positioning, and returns the result in the input array. An input
				* separation buffer added to the amount in both directions. We assume that
				* the two rectangles do intersect.
				*/
				IGeometry.calcSeparationAmount = function(rectA, rectB, overlapAmount, separationBuffer) {
					if (!rectA.intersects(rectB)) throw "assert failed";
					var directions = new Array(2);
					this.decideDirectionsForOverlappingNodes(rectA, rectB, directions);
					overlapAmount[0] = Math.min(rectA.getRight(), rectB.getRight()) - Math.max(rectA.x, rectB.x);
					overlapAmount[1] = Math.min(rectA.getBottom(), rectB.getBottom()) - Math.max(rectA.y, rectB.y);
					if (rectA.getX() <= rectB.getX() && rectA.getRight() >= rectB.getRight()) overlapAmount[0] += Math.min(rectB.getX() - rectA.getX(), rectA.getRight() - rectB.getRight());
					else if (rectB.getX() <= rectA.getX() && rectB.getRight() >= rectA.getRight()) overlapAmount[0] += Math.min(rectA.getX() - rectB.getX(), rectB.getRight() - rectA.getRight());
					if (rectA.getY() <= rectB.getY() && rectA.getBottom() >= rectB.getBottom()) overlapAmount[1] += Math.min(rectB.getY() - rectA.getY(), rectA.getBottom() - rectB.getBottom());
					else if (rectB.getY() <= rectA.getY() && rectB.getBottom() >= rectA.getBottom()) overlapAmount[1] += Math.min(rectA.getY() - rectB.getY(), rectB.getBottom() - rectA.getBottom());
					var slope = Math.abs((rectB.getCenterY() - rectA.getCenterY()) / (rectB.getCenterX() - rectA.getCenterX()));
					if (rectB.getCenterY() === rectA.getCenterY() && rectB.getCenterX() === rectA.getCenterX()) slope = 1;
					var moveByY = slope * overlapAmount[0];
					var moveByX = overlapAmount[1] / slope;
					if (overlapAmount[0] < moveByX) moveByX = overlapAmount[0];
					else moveByY = overlapAmount[1];
					overlapAmount[0] = -1 * directions[0] * (moveByX / 2 + separationBuffer);
					overlapAmount[1] = -1 * directions[1] * (moveByY / 2 + separationBuffer);
				};
				/**
				* This method decides the separation direction of overlapping nodes
				*
				* if directions[0] = -1, then rectA goes left
				* if directions[0] = 1,  then rectA goes right
				* if directions[1] = -1, then rectA goes up
				* if directions[1] = 1,  then rectA goes down
				*/
				IGeometry.decideDirectionsForOverlappingNodes = function(rectA, rectB, directions) {
					if (rectA.getCenterX() < rectB.getCenterX()) directions[0] = -1;
					else directions[0] = 1;
					if (rectA.getCenterY() < rectB.getCenterY()) directions[1] = -1;
					else directions[1] = 1;
				};
				/**
				* This method calculates the intersection (clipping) points of the two
				* input rectangles with line segment defined by the centers of these two
				* rectangles. The clipping points are saved in the input double array and
				* whether or not the two rectangles overlap is returned.
				*/
				IGeometry.getIntersection2 = function(rectA, rectB, result) {
					var p1x = rectA.getCenterX();
					var p1y = rectA.getCenterY();
					var p2x = rectB.getCenterX();
					var p2y = rectB.getCenterY();
					if (rectA.intersects(rectB)) {
						result[0] = p1x;
						result[1] = p1y;
						result[2] = p2x;
						result[3] = p2y;
						return true;
					}
					var topLeftAx = rectA.getX();
					var topLeftAy = rectA.getY();
					var topRightAx = rectA.getRight();
					var bottomLeftAx = rectA.getX();
					var bottomLeftAy = rectA.getBottom();
					var bottomRightAx = rectA.getRight();
					var halfWidthA = rectA.getWidthHalf();
					var halfHeightA = rectA.getHeightHalf();
					var topLeftBx = rectB.getX();
					var topLeftBy = rectB.getY();
					var topRightBx = rectB.getRight();
					var bottomLeftBx = rectB.getX();
					var bottomLeftBy = rectB.getBottom();
					var bottomRightBx = rectB.getRight();
					var halfWidthB = rectB.getWidthHalf();
					var halfHeightB = rectB.getHeightHalf();
					var clipPointAFound = false;
					var clipPointBFound = false;
					if (p1x === p2x) {
						if (p1y > p2y) {
							result[0] = p1x;
							result[1] = topLeftAy;
							result[2] = p2x;
							result[3] = bottomLeftBy;
							return false;
						} else if (p1y < p2y) {
							result[0] = p1x;
							result[1] = bottomLeftAy;
							result[2] = p2x;
							result[3] = topLeftBy;
							return false;
						}
					} else if (p1y === p2y) {
						if (p1x > p2x) {
							result[0] = topLeftAx;
							result[1] = p1y;
							result[2] = topRightBx;
							result[3] = p2y;
							return false;
						} else if (p1x < p2x) {
							result[0] = topRightAx;
							result[1] = p1y;
							result[2] = topLeftBx;
							result[3] = p2y;
							return false;
						}
					} else {
						var slopeA = rectA.height / rectA.width;
						var slopeB = rectB.height / rectB.width;
						var slopePrime = (p2y - p1y) / (p2x - p1x);
						var cardinalDirectionA = void 0;
						var cardinalDirectionB = void 0;
						var tempPointAx = void 0;
						var tempPointAy = void 0;
						var tempPointBx = void 0;
						var tempPointBy = void 0;
						if (-slopeA === slopePrime) if (p1x > p2x) {
							result[0] = bottomLeftAx;
							result[1] = bottomLeftAy;
							clipPointAFound = true;
						} else {
							result[0] = topRightAx;
							result[1] = topLeftAy;
							clipPointAFound = true;
						}
						else if (slopeA === slopePrime) if (p1x > p2x) {
							result[0] = topLeftAx;
							result[1] = topLeftAy;
							clipPointAFound = true;
						} else {
							result[0] = bottomRightAx;
							result[1] = bottomLeftAy;
							clipPointAFound = true;
						}
						if (-slopeB === slopePrime) if (p2x > p1x) {
							result[2] = bottomLeftBx;
							result[3] = bottomLeftBy;
							clipPointBFound = true;
						} else {
							result[2] = topRightBx;
							result[3] = topLeftBy;
							clipPointBFound = true;
						}
						else if (slopeB === slopePrime) if (p2x > p1x) {
							result[2] = topLeftBx;
							result[3] = topLeftBy;
							clipPointBFound = true;
						} else {
							result[2] = bottomRightBx;
							result[3] = bottomLeftBy;
							clipPointBFound = true;
						}
						if (clipPointAFound && clipPointBFound) return false;
						if (p1x > p2x) if (p1y > p2y) {
							cardinalDirectionA = this.getCardinalDirection(slopeA, slopePrime, 4);
							cardinalDirectionB = this.getCardinalDirection(slopeB, slopePrime, 2);
						} else {
							cardinalDirectionA = this.getCardinalDirection(-slopeA, slopePrime, 3);
							cardinalDirectionB = this.getCardinalDirection(-slopeB, slopePrime, 1);
						}
						else if (p1y > p2y) {
							cardinalDirectionA = this.getCardinalDirection(-slopeA, slopePrime, 1);
							cardinalDirectionB = this.getCardinalDirection(-slopeB, slopePrime, 3);
						} else {
							cardinalDirectionA = this.getCardinalDirection(slopeA, slopePrime, 2);
							cardinalDirectionB = this.getCardinalDirection(slopeB, slopePrime, 4);
						}
						if (!clipPointAFound) switch (cardinalDirectionA) {
							case 1:
								tempPointAy = topLeftAy;
								tempPointAx = p1x + -halfHeightA / slopePrime;
								result[0] = tempPointAx;
								result[1] = tempPointAy;
								break;
							case 2:
								tempPointAx = bottomRightAx;
								tempPointAy = p1y + halfWidthA * slopePrime;
								result[0] = tempPointAx;
								result[1] = tempPointAy;
								break;
							case 3:
								tempPointAy = bottomLeftAy;
								tempPointAx = p1x + halfHeightA / slopePrime;
								result[0] = tempPointAx;
								result[1] = tempPointAy;
								break;
							case 4:
								tempPointAx = bottomLeftAx;
								tempPointAy = p1y + -halfWidthA * slopePrime;
								result[0] = tempPointAx;
								result[1] = tempPointAy;
								break;
						}
						if (!clipPointBFound) switch (cardinalDirectionB) {
							case 1:
								tempPointBy = topLeftBy;
								tempPointBx = p2x + -halfHeightB / slopePrime;
								result[2] = tempPointBx;
								result[3] = tempPointBy;
								break;
							case 2:
								tempPointBx = bottomRightBx;
								tempPointBy = p2y + halfWidthB * slopePrime;
								result[2] = tempPointBx;
								result[3] = tempPointBy;
								break;
							case 3:
								tempPointBy = bottomLeftBy;
								tempPointBx = p2x + halfHeightB / slopePrime;
								result[2] = tempPointBx;
								result[3] = tempPointBy;
								break;
							case 4:
								tempPointBx = bottomLeftBx;
								tempPointBy = p2y + -halfWidthB * slopePrime;
								result[2] = tempPointBx;
								result[3] = tempPointBy;
								break;
						}
					}
					return false;
				};
				/**
				* This method returns in which cardinal direction does input point stays
				* 1: North
				* 2: East
				* 3: South
				* 4: West
				*/
				IGeometry.getCardinalDirection = function(slope, slopePrime, line) {
					if (slope > slopePrime) return line;
					else return 1 + line % 4;
				};
				/**
				* This method calculates the intersection of the two lines defined by
				* point pairs (s1,s2) and (f1,f2).
				*/
				IGeometry.getIntersection = function(s1, s2, f1, f2) {
					if (f2 == null) return this.getIntersection2(s1, s2, f1);
					var x1 = s1.x;
					var y1 = s1.y;
					var x2 = s2.x;
					var y2 = s2.y;
					var x3 = f1.x;
					var y3 = f1.y;
					var x4 = f2.x;
					var y4 = f2.y;
					var x = void 0, y = void 0;
					var a1 = void 0, a2 = void 0, b1 = void 0, b2 = void 0, c1 = void 0, c2 = void 0;
					var denom = void 0;
					a1 = y2 - y1;
					b1 = x1 - x2;
					c1 = x2 * y1 - x1 * y2;
					a2 = y4 - y3;
					b2 = x3 - x4;
					c2 = x4 * y3 - x3 * y4;
					denom = a1 * b2 - a2 * b1;
					if (denom === 0) return null;
					x = (b1 * c2 - b2 * c1) / denom;
					y = (a2 * c1 - a1 * c2) / denom;
					return new Point(x, y);
				};
				/**
				* This method finds and returns the angle of the vector from the + x-axis
				* in clockwise direction (compatible w/ Java coordinate system!).
				*/
				IGeometry.angleOfVector = function(Cx, Cy, Nx, Ny) {
					var C_angle = void 0;
					if (Cx !== Nx) {
						C_angle = Math.atan((Ny - Cy) / (Nx - Cx));
						if (Nx < Cx) C_angle += Math.PI;
						else if (Ny < Cy) C_angle += this.TWO_PI;
					} else if (Ny < Cy) C_angle = this.ONE_AND_HALF_PI;
					else C_angle = this.HALF_PI;
					return C_angle;
				};
				/**
				* This method checks whether the given two line segments (one with point
				* p1 and p2, the other with point p3 and p4) intersect at a point other
				* than these points.
				*/
				IGeometry.doIntersect = function(p1, p2, p3, p4) {
					var a = p1.x;
					var b = p1.y;
					var c = p2.x;
					var d = p2.y;
					var p = p3.x;
					var q = p3.y;
					var r = p4.x;
					var s = p4.y;
					var det = (c - a) * (s - q) - (r - p) * (d - b);
					if (det === 0) return false;
					else {
						var lambda = ((s - q) * (r - a) + (p - r) * (s - b)) / det;
						var gamma = ((b - d) * (r - a) + (c - a) * (s - b)) / det;
						return 0 < lambda && lambda < 1 && 0 < gamma && gamma < 1;
					}
				};
				/**
				* Some useful pre-calculated constants
				*/
				IGeometry.HALF_PI = .5 * Math.PI;
				IGeometry.ONE_AND_HALF_PI = 1.5 * Math.PI;
				IGeometry.TWO_PI = 2 * Math.PI;
				IGeometry.THREE_PI = 3 * Math.PI;
				module$21.exports = IGeometry;
			}),
			(function(module$22, exports$20, __webpack_require__) {
				"use strict";
				function IMath() {}
				/**
				* This method returns the sign of the input value.
				*/
				IMath.sign = function(value) {
					if (value > 0) return 1;
					else if (value < 0) return -1;
					else return 0;
				};
				IMath.floor = function(value) {
					return value < 0 ? Math.ceil(value) : Math.floor(value);
				};
				IMath.ceil = function(value) {
					return value < 0 ? Math.floor(value) : Math.ceil(value);
				};
				module$22.exports = IMath;
			}),
			(function(module$23, exports$21, __webpack_require__) {
				"use strict";
				function Integer() {}
				Integer.MAX_VALUE = 2147483647;
				Integer.MIN_VALUE = -2147483648;
				module$23.exports = Integer;
			}),
			(function(module$24, exports$22, __webpack_require__) {
				"use strict";
				var _createClass = function() {
					function defineProperties(target, props) {
						for (var i = 0; i < props.length; i++) {
							var descriptor = props[i];
							descriptor.enumerable = descriptor.enumerable || false;
							descriptor.configurable = true;
							if ("value" in descriptor) descriptor.writable = true;
							Object.defineProperty(target, descriptor.key, descriptor);
						}
					}
					return function(Constructor, protoProps, staticProps) {
						if (protoProps) defineProperties(Constructor.prototype, protoProps);
						if (staticProps) defineProperties(Constructor, staticProps);
						return Constructor;
					};
				}();
				function _classCallCheck(instance, Constructor) {
					if (!(instance instanceof Constructor)) throw new TypeError("Cannot call a class as a function");
				}
				var nodeFrom = function nodeFrom(value) {
					return {
						value,
						next: null,
						prev: null
					};
				};
				var add = function add(prev, node, next, list) {
					if (prev !== null) prev.next = node;
					else list.head = node;
					if (next !== null) next.prev = node;
					else list.tail = node;
					node.prev = prev;
					node.next = next;
					list.length++;
					return node;
				};
				var _remove = function _remove(node, list) {
					var prev = node.prev, next = node.next;
					if (prev !== null) prev.next = next;
					else list.head = next;
					if (next !== null) next.prev = prev;
					else list.tail = prev;
					node.prev = node.next = null;
					list.length--;
					return node;
				};
				module$24.exports = function() {
					function LinkedList(vals) {
						var _this = this;
						_classCallCheck(this, LinkedList);
						this.length = 0;
						this.head = null;
						this.tail = null;
						if (vals != null) vals.forEach(function(v) {
							return _this.push(v);
						});
					}
					_createClass(LinkedList, [
						{
							key: "size",
							value: function size() {
								return this.length;
							}
						},
						{
							key: "insertBefore",
							value: function insertBefore(val, otherNode) {
								return add(otherNode.prev, nodeFrom(val), otherNode, this);
							}
						},
						{
							key: "insertAfter",
							value: function insertAfter(val, otherNode) {
								return add(otherNode, nodeFrom(val), otherNode.next, this);
							}
						},
						{
							key: "insertNodeBefore",
							value: function insertNodeBefore(newNode, otherNode) {
								return add(otherNode.prev, newNode, otherNode, this);
							}
						},
						{
							key: "insertNodeAfter",
							value: function insertNodeAfter(newNode, otherNode) {
								return add(otherNode, newNode, otherNode.next, this);
							}
						},
						{
							key: "push",
							value: function push(val) {
								return add(this.tail, nodeFrom(val), null, this);
							}
						},
						{
							key: "unshift",
							value: function unshift(val) {
								return add(null, nodeFrom(val), this.head, this);
							}
						},
						{
							key: "remove",
							value: function remove(node) {
								return _remove(node, this);
							}
						},
						{
							key: "pop",
							value: function pop() {
								return _remove(this.tail, this).value;
							}
						},
						{
							key: "popNode",
							value: function popNode() {
								return _remove(this.tail, this);
							}
						},
						{
							key: "shift",
							value: function shift() {
								return _remove(this.head, this).value;
							}
						},
						{
							key: "shiftNode",
							value: function shiftNode() {
								return _remove(this.head, this);
							}
						},
						{
							key: "get_object_at",
							value: function get_object_at(index) {
								if (index <= this.length()) {
									var i = 1;
									var current = this.head;
									while (i < index) {
										current = current.next;
										i++;
									}
									return current.value;
								}
							}
						},
						{
							key: "set_object_at",
							value: function set_object_at(index, value) {
								if (index <= this.length()) {
									var i = 1;
									var current = this.head;
									while (i < index) {
										current = current.next;
										i++;
									}
									current.value = value;
								}
							}
						}
					]);
					return LinkedList;
				}();
			}),
			(function(module$25, exports$23, __webpack_require__) {
				"use strict";
				function Point(x, y, p) {
					this.x = null;
					this.y = null;
					if (x == null && y == null && p == null) {
						this.x = 0;
						this.y = 0;
					} else if (typeof x == "number" && typeof y == "number" && p == null) {
						this.x = x;
						this.y = y;
					} else if (x.constructor.name == "Point" && y == null && p == null) {
						p = x;
						this.x = p.x;
						this.y = p.y;
					}
				}
				Point.prototype.getX = function() {
					return this.x;
				};
				Point.prototype.getY = function() {
					return this.y;
				};
				Point.prototype.getLocation = function() {
					return new Point(this.x, this.y);
				};
				Point.prototype.setLocation = function(x, y, p) {
					if (x.constructor.name == "Point" && y == null && p == null) {
						p = x;
						this.setLocation(p.x, p.y);
					} else if (typeof x == "number" && typeof y == "number" && p == null) if (parseInt(x) == x && parseInt(y) == y) this.move(x, y);
					else {
						this.x = Math.floor(x + .5);
						this.y = Math.floor(y + .5);
					}
				};
				Point.prototype.move = function(x, y) {
					this.x = x;
					this.y = y;
				};
				Point.prototype.translate = function(dx, dy) {
					this.x += dx;
					this.y += dy;
				};
				Point.prototype.equals = function(obj) {
					if (obj.constructor.name == "Point") {
						var pt = obj;
						return this.x == pt.x && this.y == pt.y;
					}
					return this == obj;
				};
				Point.prototype.toString = function() {
					return new Point().constructor.name + "[x=" + this.x + ",y=" + this.y + "]";
				};
				module$25.exports = Point;
			}),
			(function(module$26, exports$24, __webpack_require__) {
				"use strict";
				function RectangleD(x, y, width, height) {
					this.x = 0;
					this.y = 0;
					this.width = 0;
					this.height = 0;
					if (x != null && y != null && width != null && height != null) {
						this.x = x;
						this.y = y;
						this.width = width;
						this.height = height;
					}
				}
				RectangleD.prototype.getX = function() {
					return this.x;
				};
				RectangleD.prototype.setX = function(x) {
					this.x = x;
				};
				RectangleD.prototype.getY = function() {
					return this.y;
				};
				RectangleD.prototype.setY = function(y) {
					this.y = y;
				};
				RectangleD.prototype.getWidth = function() {
					return this.width;
				};
				RectangleD.prototype.setWidth = function(width) {
					this.width = width;
				};
				RectangleD.prototype.getHeight = function() {
					return this.height;
				};
				RectangleD.prototype.setHeight = function(height) {
					this.height = height;
				};
				RectangleD.prototype.getRight = function() {
					return this.x + this.width;
				};
				RectangleD.prototype.getBottom = function() {
					return this.y + this.height;
				};
				RectangleD.prototype.intersects = function(a) {
					if (this.getRight() < a.x) return false;
					if (this.getBottom() < a.y) return false;
					if (a.getRight() < this.x) return false;
					if (a.getBottom() < this.y) return false;
					return true;
				};
				RectangleD.prototype.getCenterX = function() {
					return this.x + this.width / 2;
				};
				RectangleD.prototype.getMinX = function() {
					return this.getX();
				};
				RectangleD.prototype.getMaxX = function() {
					return this.getX() + this.width;
				};
				RectangleD.prototype.getCenterY = function() {
					return this.y + this.height / 2;
				};
				RectangleD.prototype.getMinY = function() {
					return this.getY();
				};
				RectangleD.prototype.getMaxY = function() {
					return this.getY() + this.height;
				};
				RectangleD.prototype.getWidthHalf = function() {
					return this.width / 2;
				};
				RectangleD.prototype.getHeightHalf = function() {
					return this.height / 2;
				};
				module$26.exports = RectangleD;
			}),
			(function(module$27, exports$25, __webpack_require__) {
				"use strict";
				var _typeof = typeof Symbol === "function" && typeof Symbol.iterator === "symbol" ? function(obj) {
					return typeof obj;
				} : function(obj) {
					return obj && typeof Symbol === "function" && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj;
				};
				function UniqueIDGeneretor() {}
				UniqueIDGeneretor.lastID = 0;
				UniqueIDGeneretor.createID = function(obj) {
					if (UniqueIDGeneretor.isPrimitive(obj)) return obj;
					if (obj.uniqueID != null) return obj.uniqueID;
					obj.uniqueID = UniqueIDGeneretor.getString();
					UniqueIDGeneretor.lastID++;
					return obj.uniqueID;
				};
				UniqueIDGeneretor.getString = function(id) {
					if (id == null) id = UniqueIDGeneretor.lastID;
					return "Object#" + id;
				};
				UniqueIDGeneretor.isPrimitive = function(arg) {
					var type = typeof arg === "undefined" ? "undefined" : _typeof(arg);
					return arg == null || type != "object" && type != "function";
				};
				module$27.exports = UniqueIDGeneretor;
			}),
			(function(module$28, exports$26, __webpack_require__) {
				"use strict";
				function _toConsumableArray(arr) {
					if (Array.isArray(arr)) {
						for (var i = 0, arr2 = Array(arr.length); i < arr.length; i++) arr2[i] = arr[i];
						return arr2;
					} else return Array.from(arr);
				}
				var LayoutConstants = __webpack_require__(0);
				var LGraphManager = __webpack_require__(6);
				var LNode = __webpack_require__(3);
				var LEdge = __webpack_require__(1);
				var LGraph = __webpack_require__(5);
				var PointD = __webpack_require__(4);
				var Transform = __webpack_require__(17);
				var Emitter = __webpack_require__(27);
				function Layout(isRemoteUse) {
					Emitter.call(this);
					this.layoutQuality = LayoutConstants.QUALITY;
					this.createBendsAsNeeded = LayoutConstants.DEFAULT_CREATE_BENDS_AS_NEEDED;
					this.incremental = LayoutConstants.DEFAULT_INCREMENTAL;
					this.animationOnLayout = LayoutConstants.DEFAULT_ANIMATION_ON_LAYOUT;
					this.animationDuringLayout = LayoutConstants.DEFAULT_ANIMATION_DURING_LAYOUT;
					this.animationPeriod = LayoutConstants.DEFAULT_ANIMATION_PERIOD;
					/**
					* Whether or not leaf nodes (non-compound nodes) are of uniform sizes. When
					* they are, both spring and repulsion forces between two leaf nodes can be
					* calculated without the expensive clipping point calculations, resulting
					* in major speed-up.
					*/
					this.uniformLeafNodeSizes = LayoutConstants.DEFAULT_UNIFORM_LEAF_NODE_SIZES;
					/**
					* This is used for creation of bendpoints by using dummy nodes and edges.
					* Maps an LEdge to its dummy bendpoint path.
					*/
					this.edgeToDummyNodes = /* @__PURE__ */ new Map();
					this.graphManager = new LGraphManager(this);
					this.isLayoutFinished = false;
					this.isSubLayout = false;
					this.isRemoteUse = false;
					if (isRemoteUse != null) this.isRemoteUse = isRemoteUse;
				}
				Layout.RANDOM_SEED = 1;
				Layout.prototype = Object.create(Emitter.prototype);
				Layout.prototype.getGraphManager = function() {
					return this.graphManager;
				};
				Layout.prototype.getAllNodes = function() {
					return this.graphManager.getAllNodes();
				};
				Layout.prototype.getAllEdges = function() {
					return this.graphManager.getAllEdges();
				};
				Layout.prototype.getAllNodesToApplyGravitation = function() {
					return this.graphManager.getAllNodesToApplyGravitation();
				};
				Layout.prototype.newGraphManager = function() {
					var gm = new LGraphManager(this);
					this.graphManager = gm;
					return gm;
				};
				Layout.prototype.newGraph = function(vGraph) {
					return new LGraph(null, this.graphManager, vGraph);
				};
				Layout.prototype.newNode = function(vNode) {
					return new LNode(this.graphManager, vNode);
				};
				Layout.prototype.newEdge = function(vEdge) {
					return new LEdge(null, null, vEdge);
				};
				Layout.prototype.checkLayoutSuccess = function() {
					return this.graphManager.getRoot() == null || this.graphManager.getRoot().getNodes().length == 0 || this.graphManager.includesInvalidEdge();
				};
				Layout.prototype.runLayout = function() {
					this.isLayoutFinished = false;
					if (this.tilingPreLayout) this.tilingPreLayout();
					this.initParameters();
					var isLayoutSuccessfull;
					if (this.checkLayoutSuccess()) isLayoutSuccessfull = false;
					else isLayoutSuccessfull = this.layout();
					if (LayoutConstants.ANIMATE === "during") return false;
					if (isLayoutSuccessfull) {
						if (!this.isSubLayout) this.doPostLayout();
					}
					if (this.tilingPostLayout) this.tilingPostLayout();
					this.isLayoutFinished = true;
					return isLayoutSuccessfull;
				};
				/**
				* This method performs the operations required after layout.
				*/
				Layout.prototype.doPostLayout = function() {
					if (!this.incremental) this.transform();
					this.update();
				};
				/**
				* This method updates the geometry of the target graph according to
				* calculated layout.
				*/
				Layout.prototype.update2 = function() {
					if (this.createBendsAsNeeded) {
						this.createBendpointsFromDummyNodes();
						this.graphManager.resetAllEdges();
					}
					if (!this.isRemoteUse) {
						var allEdges = this.graphManager.getAllEdges();
						for (var i = 0; i < allEdges.length; i++) allEdges[i];
						var nodes = this.graphManager.getRoot().getNodes();
						for (var i = 0; i < nodes.length; i++) nodes[i];
						this.update(this.graphManager.getRoot());
					}
				};
				Layout.prototype.update = function(obj) {
					if (obj == null) this.update2();
					else if (obj instanceof LNode) {
						var node = obj;
						if (node.getChild() != null) {
							var nodes = node.getChild().getNodes();
							for (var i = 0; i < nodes.length; i++) update(nodes[i]);
						}
						if (node.vGraphObject != null) node.vGraphObject.update(node);
					} else if (obj instanceof LEdge) {
						var edge = obj;
						if (edge.vGraphObject != null) edge.vGraphObject.update(edge);
					} else if (obj instanceof LGraph) {
						var graph = obj;
						if (graph.vGraphObject != null) graph.vGraphObject.update(graph);
					}
				};
				/**
				* This method is used to set all layout parameters to default values
				* determined at compile time.
				*/
				Layout.prototype.initParameters = function() {
					if (!this.isSubLayout) {
						this.layoutQuality = LayoutConstants.QUALITY;
						this.animationDuringLayout = LayoutConstants.DEFAULT_ANIMATION_DURING_LAYOUT;
						this.animationPeriod = LayoutConstants.DEFAULT_ANIMATION_PERIOD;
						this.animationOnLayout = LayoutConstants.DEFAULT_ANIMATION_ON_LAYOUT;
						this.incremental = LayoutConstants.DEFAULT_INCREMENTAL;
						this.createBendsAsNeeded = LayoutConstants.DEFAULT_CREATE_BENDS_AS_NEEDED;
						this.uniformLeafNodeSizes = LayoutConstants.DEFAULT_UNIFORM_LEAF_NODE_SIZES;
					}
					if (this.animationDuringLayout) this.animationOnLayout = false;
				};
				Layout.prototype.transform = function(newLeftTop) {
					if (newLeftTop == void 0) this.transform(new PointD(0, 0));
					else {
						var trans = new Transform();
						var leftTop = this.graphManager.getRoot().updateLeftTop();
						if (leftTop != null) {
							trans.setWorldOrgX(newLeftTop.x);
							trans.setWorldOrgY(newLeftTop.y);
							trans.setDeviceOrgX(leftTop.x);
							trans.setDeviceOrgY(leftTop.y);
							var nodes = this.getAllNodes();
							var node;
							for (var i = 0; i < nodes.length; i++) {
								node = nodes[i];
								node.transform(trans);
							}
						}
					}
				};
				Layout.prototype.positionNodesRandomly = function(graph) {
					if (graph == void 0) {
						this.positionNodesRandomly(this.getGraphManager().getRoot());
						this.getGraphManager().getRoot().updateBounds(true);
					} else {
						var lNode;
						var childGraph;
						var nodes = graph.getNodes();
						for (var i = 0; i < nodes.length; i++) {
							lNode = nodes[i];
							childGraph = lNode.getChild();
							if (childGraph == null) lNode.scatter();
							else if (childGraph.getNodes().length == 0) lNode.scatter();
							else {
								this.positionNodesRandomly(childGraph);
								lNode.updateBounds();
							}
						}
					}
				};
				/**
				* This method returns a list of trees where each tree is represented as a
				* list of l-nodes. The method returns a list of size 0 when:
				* - The graph is not flat or
				* - One of the component(s) of the graph is not a tree.
				*/
				Layout.prototype.getFlatForest = function() {
					var flatForest = [];
					var isForest = true;
					var allNodes = this.graphManager.getRoot().getNodes();
					var isFlat = true;
					for (var i = 0; i < allNodes.length; i++) if (allNodes[i].getChild() != null) isFlat = false;
					if (!isFlat) return flatForest;
					var visited = /* @__PURE__ */ new Set();
					var toBeVisited = [];
					var parents = /* @__PURE__ */ new Map();
					var unProcessedNodes = [];
					unProcessedNodes = unProcessedNodes.concat(allNodes);
					while (unProcessedNodes.length > 0 && isForest) {
						toBeVisited.push(unProcessedNodes[0]);
						while (toBeVisited.length > 0 && isForest) {
							var currentNode = toBeVisited[0];
							toBeVisited.splice(0, 1);
							visited.add(currentNode);
							var neighborEdges = currentNode.getEdges();
							for (var i = 0; i < neighborEdges.length; i++) {
								var currentNeighbor = neighborEdges[i].getOtherEnd(currentNode);
								if (parents.get(currentNode) != currentNeighbor) if (!visited.has(currentNeighbor)) {
									toBeVisited.push(currentNeighbor);
									parents.set(currentNeighbor, currentNode);
								} else {
									isForest = false;
									break;
								}
							}
						}
						if (!isForest) flatForest = [];
						else {
							var temp = [].concat(_toConsumableArray(visited));
							flatForest.push(temp);
							for (var i = 0; i < temp.length; i++) {
								var value = temp[i];
								var index = unProcessedNodes.indexOf(value);
								if (index > -1) unProcessedNodes.splice(index, 1);
							}
							visited = /* @__PURE__ */ new Set();
							parents = /* @__PURE__ */ new Map();
						}
					}
					return flatForest;
				};
				/**
				* This method creates dummy nodes (an l-level node with minimal dimensions)
				* for the given edge (one per bendpoint). The existing l-level structure
				* is updated accordingly.
				*/
				Layout.prototype.createDummyNodesForBendpoints = function(edge) {
					var dummyNodes = [];
					var prev = edge.source;
					var graph = this.graphManager.calcLowestCommonAncestor(edge.source, edge.target);
					for (var i = 0; i < edge.bendpoints.length; i++) {
						var dummyNode = this.newNode(null);
						dummyNode.setRect(new Point(0, 0), new Dimension(1, 1));
						graph.add(dummyNode);
						var dummyEdge = this.newEdge(null);
						this.graphManager.add(dummyEdge, prev, dummyNode);
						dummyNodes.add(dummyNode);
						prev = dummyNode;
					}
					var dummyEdge = this.newEdge(null);
					this.graphManager.add(dummyEdge, prev, edge.target);
					this.edgeToDummyNodes.set(edge, dummyNodes);
					if (edge.isInterGraph()) this.graphManager.remove(edge);
					else graph.remove(edge);
					return dummyNodes;
				};
				/**
				* This method creates bendpoints for edges from the dummy nodes
				* at l-level.
				*/
				Layout.prototype.createBendpointsFromDummyNodes = function() {
					var edges = [];
					edges = edges.concat(this.graphManager.getAllEdges());
					edges = [].concat(_toConsumableArray(this.edgeToDummyNodes.keys())).concat(edges);
					for (var k = 0; k < edges.length; k++) {
						var lEdge = edges[k];
						if (lEdge.bendpoints.length > 0) {
							var path = this.edgeToDummyNodes.get(lEdge);
							for (var i = 0; i < path.length; i++) {
								var dummyNode = path[i];
								var p = new PointD(dummyNode.getCenterX(), dummyNode.getCenterY());
								var ebp = lEdge.bendpoints.get(i);
								ebp.x = p.x;
								ebp.y = p.y;
								dummyNode.getOwner().remove(dummyNode);
							}
							this.graphManager.add(lEdge, lEdge.source, lEdge.target);
						}
					}
				};
				Layout.transform = function(sliderValue, defaultValue, minDiv, maxMul) {
					if (minDiv != void 0 && maxMul != void 0) {
						var value = defaultValue;
						if (sliderValue <= 50) {
							var minValue = defaultValue / minDiv;
							value -= (defaultValue - minValue) / 50 * (50 - sliderValue);
						} else {
							var maxValue = defaultValue * maxMul;
							value += (maxValue - defaultValue) / 50 * (sliderValue - 50);
						}
						return value;
					} else {
						var a, b;
						if (sliderValue <= 50) {
							a = 9 * defaultValue / 500;
							b = defaultValue / 10;
						} else {
							a = 9 * defaultValue / 50;
							b = -8 * defaultValue;
						}
						return a * sliderValue + b;
					}
				};
				/**
				* This method finds and returns the center of the given nodes, assuming
				* that the given nodes form a tree in themselves.
				*/
				Layout.findCenterOfTree = function(nodes) {
					var list = [];
					list = list.concat(nodes);
					var removedNodes = [];
					var remainingDegrees = /* @__PURE__ */ new Map();
					var foundCenter = false;
					var centerNode = null;
					if (list.length == 1 || list.length == 2) {
						foundCenter = true;
						centerNode = list[0];
					}
					for (var i = 0; i < list.length; i++) {
						var node = list[i];
						var degree = node.getNeighborsList().size;
						remainingDegrees.set(node, node.getNeighborsList().size);
						if (degree == 1) removedNodes.push(node);
					}
					var tempList = [];
					tempList = tempList.concat(removedNodes);
					while (!foundCenter) {
						var tempList2 = [];
						tempList2 = tempList2.concat(tempList);
						tempList = [];
						for (var i = 0; i < list.length; i++) {
							var node = list[i];
							var index = list.indexOf(node);
							if (index >= 0) list.splice(index, 1);
							node.getNeighborsList().forEach(function(neighbour) {
								if (removedNodes.indexOf(neighbour) < 0) {
									var newDegree = remainingDegrees.get(neighbour) - 1;
									if (newDegree == 1) tempList.push(neighbour);
									remainingDegrees.set(neighbour, newDegree);
								}
							});
						}
						removedNodes = removedNodes.concat(tempList);
						if (list.length == 1 || list.length == 2) {
							foundCenter = true;
							centerNode = list[0];
						}
					}
					return centerNode;
				};
				/**
				* During the coarsening process, this layout may be referenced by two graph managers
				* this setter function grants access to change the currently being used graph manager
				*/
				Layout.prototype.setGraphManager = function(gm) {
					this.graphManager = gm;
				};
				module$28.exports = Layout;
			}),
			(function(module$29, exports$27, __webpack_require__) {
				"use strict";
				function RandomSeed() {}
				RandomSeed.seed = 1;
				RandomSeed.x = 0;
				RandomSeed.nextDouble = function() {
					RandomSeed.x = Math.sin(RandomSeed.seed++) * 1e4;
					return RandomSeed.x - Math.floor(RandomSeed.x);
				};
				module$29.exports = RandomSeed;
			}),
			(function(module$30, exports$28, __webpack_require__) {
				"use strict";
				var PointD = __webpack_require__(4);
				function Transform(x, y) {
					this.lworldOrgX = 0;
					this.lworldOrgY = 0;
					this.ldeviceOrgX = 0;
					this.ldeviceOrgY = 0;
					this.lworldExtX = 1;
					this.lworldExtY = 1;
					this.ldeviceExtX = 1;
					this.ldeviceExtY = 1;
				}
				Transform.prototype.getWorldOrgX = function() {
					return this.lworldOrgX;
				};
				Transform.prototype.setWorldOrgX = function(wox) {
					this.lworldOrgX = wox;
				};
				Transform.prototype.getWorldOrgY = function() {
					return this.lworldOrgY;
				};
				Transform.prototype.setWorldOrgY = function(woy) {
					this.lworldOrgY = woy;
				};
				Transform.prototype.getWorldExtX = function() {
					return this.lworldExtX;
				};
				Transform.prototype.setWorldExtX = function(wex) {
					this.lworldExtX = wex;
				};
				Transform.prototype.getWorldExtY = function() {
					return this.lworldExtY;
				};
				Transform.prototype.setWorldExtY = function(wey) {
					this.lworldExtY = wey;
				};
				Transform.prototype.getDeviceOrgX = function() {
					return this.ldeviceOrgX;
				};
				Transform.prototype.setDeviceOrgX = function(dox) {
					this.ldeviceOrgX = dox;
				};
				Transform.prototype.getDeviceOrgY = function() {
					return this.ldeviceOrgY;
				};
				Transform.prototype.setDeviceOrgY = function(doy) {
					this.ldeviceOrgY = doy;
				};
				Transform.prototype.getDeviceExtX = function() {
					return this.ldeviceExtX;
				};
				Transform.prototype.setDeviceExtX = function(dex) {
					this.ldeviceExtX = dex;
				};
				Transform.prototype.getDeviceExtY = function() {
					return this.ldeviceExtY;
				};
				Transform.prototype.setDeviceExtY = function(dey) {
					this.ldeviceExtY = dey;
				};
				Transform.prototype.transformX = function(x) {
					var xDevice = 0;
					var worldExtX = this.lworldExtX;
					if (worldExtX != 0) xDevice = this.ldeviceOrgX + (x - this.lworldOrgX) * this.ldeviceExtX / worldExtX;
					return xDevice;
				};
				Transform.prototype.transformY = function(y) {
					var yDevice = 0;
					var worldExtY = this.lworldExtY;
					if (worldExtY != 0) yDevice = this.ldeviceOrgY + (y - this.lworldOrgY) * this.ldeviceExtY / worldExtY;
					return yDevice;
				};
				Transform.prototype.inverseTransformX = function(x) {
					var xWorld = 0;
					var deviceExtX = this.ldeviceExtX;
					if (deviceExtX != 0) xWorld = this.lworldOrgX + (x - this.ldeviceOrgX) * this.lworldExtX / deviceExtX;
					return xWorld;
				};
				Transform.prototype.inverseTransformY = function(y) {
					var yWorld = 0;
					var deviceExtY = this.ldeviceExtY;
					if (deviceExtY != 0) yWorld = this.lworldOrgY + (y - this.ldeviceOrgY) * this.lworldExtY / deviceExtY;
					return yWorld;
				};
				Transform.prototype.inverseTransformPoint = function(inPoint) {
					return new PointD(this.inverseTransformX(inPoint.x), this.inverseTransformY(inPoint.y));
				};
				module$30.exports = Transform;
			}),
			(function(module$31, exports$29, __webpack_require__) {
				"use strict";
				function _toConsumableArray(arr) {
					if (Array.isArray(arr)) {
						for (var i = 0, arr2 = Array(arr.length); i < arr.length; i++) arr2[i] = arr[i];
						return arr2;
					} else return Array.from(arr);
				}
				var Layout = __webpack_require__(15);
				var FDLayoutConstants = __webpack_require__(7);
				var LayoutConstants = __webpack_require__(0);
				var IGeometry = __webpack_require__(8);
				var IMath = __webpack_require__(9);
				function FDLayout() {
					Layout.call(this);
					this.useSmartIdealEdgeLengthCalculation = FDLayoutConstants.DEFAULT_USE_SMART_IDEAL_EDGE_LENGTH_CALCULATION;
					this.idealEdgeLength = FDLayoutConstants.DEFAULT_EDGE_LENGTH;
					this.springConstant = FDLayoutConstants.DEFAULT_SPRING_STRENGTH;
					this.repulsionConstant = FDLayoutConstants.DEFAULT_REPULSION_STRENGTH;
					this.gravityConstant = FDLayoutConstants.DEFAULT_GRAVITY_STRENGTH;
					this.compoundGravityConstant = FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_STRENGTH;
					this.gravityRangeFactor = FDLayoutConstants.DEFAULT_GRAVITY_RANGE_FACTOR;
					this.compoundGravityRangeFactor = FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_RANGE_FACTOR;
					this.displacementThresholdPerNode = 3 * FDLayoutConstants.DEFAULT_EDGE_LENGTH / 100;
					this.coolingFactor = FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL;
					this.initialCoolingFactor = FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL;
					this.totalDisplacement = 0;
					this.oldTotalDisplacement = 0;
					this.maxIterations = FDLayoutConstants.MAX_ITERATIONS;
				}
				FDLayout.prototype = Object.create(Layout.prototype);
				for (var prop in Layout) FDLayout[prop] = Layout[prop];
				FDLayout.prototype.initParameters = function() {
					Layout.prototype.initParameters.call(this, arguments);
					this.totalIterations = 0;
					this.notAnimatedIterations = 0;
					this.useFRGridVariant = FDLayoutConstants.DEFAULT_USE_SMART_REPULSION_RANGE_CALCULATION;
					this.grid = [];
				};
				FDLayout.prototype.calcIdealEdgeLengths = function() {
					var edge;
					var lcaDepth;
					var source;
					var target;
					var sizeOfSourceInLca;
					var sizeOfTargetInLca;
					var allEdges = this.getGraphManager().getAllEdges();
					for (var i = 0; i < allEdges.length; i++) {
						edge = allEdges[i];
						edge.idealLength = this.idealEdgeLength;
						if (edge.isInterGraph) {
							source = edge.getSource();
							target = edge.getTarget();
							sizeOfSourceInLca = edge.getSourceInLca().getEstimatedSize();
							sizeOfTargetInLca = edge.getTargetInLca().getEstimatedSize();
							if (this.useSmartIdealEdgeLengthCalculation) edge.idealLength += sizeOfSourceInLca + sizeOfTargetInLca - 2 * LayoutConstants.SIMPLE_NODE_SIZE;
							lcaDepth = edge.getLca().getInclusionTreeDepth();
							edge.idealLength += FDLayoutConstants.DEFAULT_EDGE_LENGTH * FDLayoutConstants.PER_LEVEL_IDEAL_EDGE_LENGTH_FACTOR * (source.getInclusionTreeDepth() + target.getInclusionTreeDepth() - 2 * lcaDepth);
						}
					}
				};
				FDLayout.prototype.initSpringEmbedder = function() {
					var s = this.getAllNodes().length;
					if (this.incremental) {
						if (s > FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) this.coolingFactor = Math.max(this.coolingFactor * FDLayoutConstants.COOLING_ADAPTATION_FACTOR, this.coolingFactor - (s - FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) / (FDLayoutConstants.ADAPTATION_UPPER_NODE_LIMIT - FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) * this.coolingFactor * (1 - FDLayoutConstants.COOLING_ADAPTATION_FACTOR));
						this.maxNodeDisplacement = FDLayoutConstants.MAX_NODE_DISPLACEMENT_INCREMENTAL;
					} else {
						if (s > FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) this.coolingFactor = Math.max(FDLayoutConstants.COOLING_ADAPTATION_FACTOR, 1 - (s - FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) / (FDLayoutConstants.ADAPTATION_UPPER_NODE_LIMIT - FDLayoutConstants.ADAPTATION_LOWER_NODE_LIMIT) * (1 - FDLayoutConstants.COOLING_ADAPTATION_FACTOR));
						else this.coolingFactor = 1;
						this.initialCoolingFactor = this.coolingFactor;
						this.maxNodeDisplacement = FDLayoutConstants.MAX_NODE_DISPLACEMENT;
					}
					this.maxIterations = Math.max(this.getAllNodes().length * 5, this.maxIterations);
					this.totalDisplacementThreshold = this.displacementThresholdPerNode * this.getAllNodes().length;
					this.repulsionRange = this.calcRepulsionRange();
				};
				FDLayout.prototype.calcSpringForces = function() {
					var lEdges = this.getAllEdges();
					var edge;
					for (var i = 0; i < lEdges.length; i++) {
						edge = lEdges[i];
						this.calcSpringForce(edge, edge.idealLength);
					}
				};
				FDLayout.prototype.calcRepulsionForces = function() {
					var gridUpdateAllowed = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : true;
					var forceToNodeSurroundingUpdate = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : false;
					var i, j;
					var nodeA, nodeB;
					var lNodes = this.getAllNodes();
					var processedNodeSet;
					if (this.useFRGridVariant) {
						if (this.totalIterations % FDLayoutConstants.GRID_CALCULATION_CHECK_PERIOD == 1 && gridUpdateAllowed) this.updateGrid();
						processedNodeSet = /* @__PURE__ */ new Set();
						for (i = 0; i < lNodes.length; i++) {
							nodeA = lNodes[i];
							this.calculateRepulsionForceOfANode(nodeA, processedNodeSet, gridUpdateAllowed, forceToNodeSurroundingUpdate);
							processedNodeSet.add(nodeA);
						}
					} else for (i = 0; i < lNodes.length; i++) {
						nodeA = lNodes[i];
						for (j = i + 1; j < lNodes.length; j++) {
							nodeB = lNodes[j];
							if (nodeA.getOwner() != nodeB.getOwner()) continue;
							this.calcRepulsionForce(nodeA, nodeB);
						}
					}
				};
				FDLayout.prototype.calcGravitationalForces = function() {
					var node;
					var lNodes = this.getAllNodesToApplyGravitation();
					for (var i = 0; i < lNodes.length; i++) {
						node = lNodes[i];
						this.calcGravitationalForce(node);
					}
				};
				FDLayout.prototype.moveNodes = function() {
					var lNodes = this.getAllNodes();
					var node;
					for (var i = 0; i < lNodes.length; i++) {
						node = lNodes[i];
						node.move();
					}
				};
				FDLayout.prototype.calcSpringForce = function(edge, idealLength) {
					var sourceNode = edge.getSource();
					var targetNode = edge.getTarget();
					var length;
					var springForce;
					var springForceX;
					var springForceY;
					if (this.uniformLeafNodeSizes && sourceNode.getChild() == null && targetNode.getChild() == null) edge.updateLengthSimple();
					else {
						edge.updateLength();
						if (edge.isOverlapingSourceAndTarget) return;
					}
					length = edge.getLength();
					if (length == 0) return;
					springForce = this.springConstant * (length - idealLength);
					springForceX = springForce * (edge.lengthX / length);
					springForceY = springForce * (edge.lengthY / length);
					sourceNode.springForceX += springForceX;
					sourceNode.springForceY += springForceY;
					targetNode.springForceX -= springForceX;
					targetNode.springForceY -= springForceY;
				};
				FDLayout.prototype.calcRepulsionForce = function(nodeA, nodeB) {
					var rectA = nodeA.getRect();
					var rectB = nodeB.getRect();
					var overlapAmount = new Array(2);
					var clipPoints = new Array(4);
					var distanceX;
					var distanceY;
					var distanceSquared;
					var distance;
					var repulsionForce;
					var repulsionForceX;
					var repulsionForceY;
					if (rectA.intersects(rectB)) {
						IGeometry.calcSeparationAmount(rectA, rectB, overlapAmount, FDLayoutConstants.DEFAULT_EDGE_LENGTH / 2);
						repulsionForceX = 2 * overlapAmount[0];
						repulsionForceY = 2 * overlapAmount[1];
						var childrenConstant = nodeA.noOfChildren * nodeB.noOfChildren / (nodeA.noOfChildren + nodeB.noOfChildren);
						nodeA.repulsionForceX -= childrenConstant * repulsionForceX;
						nodeA.repulsionForceY -= childrenConstant * repulsionForceY;
						nodeB.repulsionForceX += childrenConstant * repulsionForceX;
						nodeB.repulsionForceY += childrenConstant * repulsionForceY;
					} else {
						if (this.uniformLeafNodeSizes && nodeA.getChild() == null && nodeB.getChild() == null) {
							distanceX = rectB.getCenterX() - rectA.getCenterX();
							distanceY = rectB.getCenterY() - rectA.getCenterY();
						} else {
							IGeometry.getIntersection(rectA, rectB, clipPoints);
							distanceX = clipPoints[2] - clipPoints[0];
							distanceY = clipPoints[3] - clipPoints[1];
						}
						if (Math.abs(distanceX) < FDLayoutConstants.MIN_REPULSION_DIST) distanceX = IMath.sign(distanceX) * FDLayoutConstants.MIN_REPULSION_DIST;
						if (Math.abs(distanceY) < FDLayoutConstants.MIN_REPULSION_DIST) distanceY = IMath.sign(distanceY) * FDLayoutConstants.MIN_REPULSION_DIST;
						distanceSquared = distanceX * distanceX + distanceY * distanceY;
						distance = Math.sqrt(distanceSquared);
						repulsionForce = this.repulsionConstant * nodeA.noOfChildren * nodeB.noOfChildren / distanceSquared;
						repulsionForceX = repulsionForce * distanceX / distance;
						repulsionForceY = repulsionForce * distanceY / distance;
						nodeA.repulsionForceX -= repulsionForceX;
						nodeA.repulsionForceY -= repulsionForceY;
						nodeB.repulsionForceX += repulsionForceX;
						nodeB.repulsionForceY += repulsionForceY;
					}
				};
				FDLayout.prototype.calcGravitationalForce = function(node) {
					var ownerGraph;
					var ownerCenterX;
					var ownerCenterY;
					var distanceX;
					var distanceY;
					var absDistanceX;
					var absDistanceY;
					var estimatedSize;
					ownerGraph = node.getOwner();
					ownerCenterX = (ownerGraph.getRight() + ownerGraph.getLeft()) / 2;
					ownerCenterY = (ownerGraph.getTop() + ownerGraph.getBottom()) / 2;
					distanceX = node.getCenterX() - ownerCenterX;
					distanceY = node.getCenterY() - ownerCenterY;
					absDistanceX = Math.abs(distanceX) + node.getWidth() / 2;
					absDistanceY = Math.abs(distanceY) + node.getHeight() / 2;
					if (node.getOwner() == this.graphManager.getRoot()) {
						estimatedSize = ownerGraph.getEstimatedSize() * this.gravityRangeFactor;
						if (absDistanceX > estimatedSize || absDistanceY > estimatedSize) {
							node.gravitationForceX = -this.gravityConstant * distanceX;
							node.gravitationForceY = -this.gravityConstant * distanceY;
						}
					} else {
						estimatedSize = ownerGraph.getEstimatedSize() * this.compoundGravityRangeFactor;
						if (absDistanceX > estimatedSize || absDistanceY > estimatedSize) {
							node.gravitationForceX = -this.gravityConstant * distanceX * this.compoundGravityConstant;
							node.gravitationForceY = -this.gravityConstant * distanceY * this.compoundGravityConstant;
						}
					}
				};
				FDLayout.prototype.isConverged = function() {
					var converged;
					var oscilating = false;
					if (this.totalIterations > this.maxIterations / 3) oscilating = Math.abs(this.totalDisplacement - this.oldTotalDisplacement) < 2;
					converged = this.totalDisplacement < this.totalDisplacementThreshold;
					this.oldTotalDisplacement = this.totalDisplacement;
					return converged || oscilating;
				};
				FDLayout.prototype.animate = function() {
					if (this.animationDuringLayout && !this.isSubLayout) if (this.notAnimatedIterations == this.animationPeriod) {
						this.update();
						this.notAnimatedIterations = 0;
					} else this.notAnimatedIterations++;
				};
				FDLayout.prototype.calcNoOfChildrenForAllNodes = function() {
					var node;
					var allNodes = this.graphManager.getAllNodes();
					for (var i = 0; i < allNodes.length; i++) {
						node = allNodes[i];
						node.noOfChildren = node.getNoOfChildren();
					}
				};
				FDLayout.prototype.calcGrid = function(graph) {
					var sizeX = 0;
					var sizeY = 0;
					sizeX = parseInt(Math.ceil((graph.getRight() - graph.getLeft()) / this.repulsionRange));
					sizeY = parseInt(Math.ceil((graph.getBottom() - graph.getTop()) / this.repulsionRange));
					var grid = new Array(sizeX);
					for (var i = 0; i < sizeX; i++) grid[i] = new Array(sizeY);
					for (var i = 0; i < sizeX; i++) for (var j = 0; j < sizeY; j++) grid[i][j] = new Array();
					return grid;
				};
				FDLayout.prototype.addNodeToGrid = function(v, left, top) {
					var startX = 0;
					var finishX = 0;
					var startY = 0;
					var finishY = 0;
					startX = parseInt(Math.floor((v.getRect().x - left) / this.repulsionRange));
					finishX = parseInt(Math.floor((v.getRect().width + v.getRect().x - left) / this.repulsionRange));
					startY = parseInt(Math.floor((v.getRect().y - top) / this.repulsionRange));
					finishY = parseInt(Math.floor((v.getRect().height + v.getRect().y - top) / this.repulsionRange));
					for (var i = startX; i <= finishX; i++) for (var j = startY; j <= finishY; j++) {
						this.grid[i][j].push(v);
						v.setGridCoordinates(startX, finishX, startY, finishY);
					}
				};
				FDLayout.prototype.updateGrid = function() {
					var i;
					var nodeA;
					var lNodes = this.getAllNodes();
					this.grid = this.calcGrid(this.graphManager.getRoot());
					for (i = 0; i < lNodes.length; i++) {
						nodeA = lNodes[i];
						this.addNodeToGrid(nodeA, this.graphManager.getRoot().getLeft(), this.graphManager.getRoot().getTop());
					}
				};
				FDLayout.prototype.calculateRepulsionForceOfANode = function(nodeA, processedNodeSet, gridUpdateAllowed, forceToNodeSurroundingUpdate) {
					if (this.totalIterations % FDLayoutConstants.GRID_CALCULATION_CHECK_PERIOD == 1 && gridUpdateAllowed || forceToNodeSurroundingUpdate) {
						var surrounding = /* @__PURE__ */ new Set();
						nodeA.surrounding = new Array();
						var nodeB;
						var grid = this.grid;
						for (var i = nodeA.startX - 1; i < nodeA.finishX + 2; i++) for (var j = nodeA.startY - 1; j < nodeA.finishY + 2; j++) if (!(i < 0 || j < 0 || i >= grid.length || j >= grid[0].length)) for (var k = 0; k < grid[i][j].length; k++) {
							nodeB = grid[i][j][k];
							if (nodeA.getOwner() != nodeB.getOwner() || nodeA == nodeB) continue;
							if (!processedNodeSet.has(nodeB) && !surrounding.has(nodeB)) {
								var distanceX = Math.abs(nodeA.getCenterX() - nodeB.getCenterX()) - (nodeA.getWidth() / 2 + nodeB.getWidth() / 2);
								var distanceY = Math.abs(nodeA.getCenterY() - nodeB.getCenterY()) - (nodeA.getHeight() / 2 + nodeB.getHeight() / 2);
								if (distanceX <= this.repulsionRange && distanceY <= this.repulsionRange) surrounding.add(nodeB);
							}
						}
						nodeA.surrounding = [].concat(_toConsumableArray(surrounding));
					}
					for (i = 0; i < nodeA.surrounding.length; i++) this.calcRepulsionForce(nodeA, nodeA.surrounding[i]);
				};
				FDLayout.prototype.calcRepulsionRange = function() {
					return 0;
				};
				module$31.exports = FDLayout;
			}),
			(function(module$32, exports$30, __webpack_require__) {
				"use strict";
				var LEdge = __webpack_require__(1);
				var FDLayoutConstants = __webpack_require__(7);
				function FDLayoutEdge(source, target, vEdge) {
					LEdge.call(this, source, target, vEdge);
					this.idealLength = FDLayoutConstants.DEFAULT_EDGE_LENGTH;
				}
				FDLayoutEdge.prototype = Object.create(LEdge.prototype);
				for (var prop in LEdge) FDLayoutEdge[prop] = LEdge[prop];
				module$32.exports = FDLayoutEdge;
			}),
			(function(module$33, exports$31, __webpack_require__) {
				"use strict";
				var LNode = __webpack_require__(3);
				function FDLayoutNode(gm, loc, size, vNode) {
					LNode.call(this, gm, loc, size, vNode);
					this.springForceX = 0;
					this.springForceY = 0;
					this.repulsionForceX = 0;
					this.repulsionForceY = 0;
					this.gravitationForceX = 0;
					this.gravitationForceY = 0;
					this.displacementX = 0;
					this.displacementY = 0;
					this.startX = 0;
					this.finishX = 0;
					this.startY = 0;
					this.finishY = 0;
					this.surrounding = [];
				}
				FDLayoutNode.prototype = Object.create(LNode.prototype);
				for (var prop in LNode) FDLayoutNode[prop] = LNode[prop];
				FDLayoutNode.prototype.setGridCoordinates = function(_startX, _finishX, _startY, _finishY) {
					this.startX = _startX;
					this.finishX = _finishX;
					this.startY = _startY;
					this.finishY = _finishY;
				};
				module$33.exports = FDLayoutNode;
			}),
			(function(module$34, exports$32, __webpack_require__) {
				"use strict";
				function DimensionD(width, height) {
					this.width = 0;
					this.height = 0;
					if (width !== null && height !== null) {
						this.height = height;
						this.width = width;
					}
				}
				DimensionD.prototype.getWidth = function() {
					return this.width;
				};
				DimensionD.prototype.setWidth = function(width) {
					this.width = width;
				};
				DimensionD.prototype.getHeight = function() {
					return this.height;
				};
				DimensionD.prototype.setHeight = function(height) {
					this.height = height;
				};
				module$34.exports = DimensionD;
			}),
			(function(module$35, exports$33, __webpack_require__) {
				"use strict";
				var UniqueIDGeneretor = __webpack_require__(14);
				function HashMap() {
					this.map = {};
					this.keys = [];
				}
				HashMap.prototype.put = function(key, value) {
					var theId = UniqueIDGeneretor.createID(key);
					if (!this.contains(theId)) {
						this.map[theId] = value;
						this.keys.push(key);
					}
				};
				HashMap.prototype.contains = function(key) {
					UniqueIDGeneretor.createID(key);
					return this.map[key] != null;
				};
				HashMap.prototype.get = function(key) {
					var theId = UniqueIDGeneretor.createID(key);
					return this.map[theId];
				};
				HashMap.prototype.keySet = function() {
					return this.keys;
				};
				module$35.exports = HashMap;
			}),
			(function(module$36, exports$34, __webpack_require__) {
				"use strict";
				var UniqueIDGeneretor = __webpack_require__(14);
				function HashSet() {
					this.set = {};
				}
				HashSet.prototype.add = function(obj) {
					var theId = UniqueIDGeneretor.createID(obj);
					if (!this.contains(theId)) this.set[theId] = obj;
				};
				HashSet.prototype.remove = function(obj) {
					delete this.set[UniqueIDGeneretor.createID(obj)];
				};
				HashSet.prototype.clear = function() {
					this.set = {};
				};
				HashSet.prototype.contains = function(obj) {
					return this.set[UniqueIDGeneretor.createID(obj)] == obj;
				};
				HashSet.prototype.isEmpty = function() {
					return this.size() === 0;
				};
				HashSet.prototype.size = function() {
					return Object.keys(this.set).length;
				};
				HashSet.prototype.addAllTo = function(list) {
					var keys = Object.keys(this.set);
					var length = keys.length;
					for (var i = 0; i < length; i++) list.push(this.set[keys[i]]);
				};
				HashSet.prototype.size = function() {
					return Object.keys(this.set).length;
				};
				HashSet.prototype.addAll = function(list) {
					var s = list.length;
					for (var i = 0; i < s; i++) {
						var v = list[i];
						this.add(v);
					}
				};
				module$36.exports = HashSet;
			}),
			(function(module$37, exports$35, __webpack_require__) {
				"use strict";
				var _createClass = function() {
					function defineProperties(target, props) {
						for (var i = 0; i < props.length; i++) {
							var descriptor = props[i];
							descriptor.enumerable = descriptor.enumerable || false;
							descriptor.configurable = true;
							if ("value" in descriptor) descriptor.writable = true;
							Object.defineProperty(target, descriptor.key, descriptor);
						}
					}
					return function(Constructor, protoProps, staticProps) {
						if (protoProps) defineProperties(Constructor.prototype, protoProps);
						if (staticProps) defineProperties(Constructor, staticProps);
						return Constructor;
					};
				}();
				function _classCallCheck(instance, Constructor) {
					if (!(instance instanceof Constructor)) throw new TypeError("Cannot call a class as a function");
				}
				/**
				* A classic Quicksort algorithm with Hoare's partition
				* - Works also on LinkedList objects
				*
				* Copyright: i-Vis Research Group, Bilkent University, 2007 - present
				*/
				var LinkedList = __webpack_require__(11);
				module$37.exports = function() {
					function Quicksort(A, compareFunction) {
						_classCallCheck(this, Quicksort);
						if (compareFunction !== null || compareFunction !== void 0) this.compareFunction = this._defaultCompareFunction;
						var length = void 0;
						if (A instanceof LinkedList) length = A.size();
						else length = A.length;
						this._quicksort(A, 0, length - 1);
					}
					_createClass(Quicksort, [
						{
							key: "_quicksort",
							value: function _quicksort(A, p, r) {
								if (p < r) {
									var q = this._partition(A, p, r);
									this._quicksort(A, p, q);
									this._quicksort(A, q + 1, r);
								}
							}
						},
						{
							key: "_partition",
							value: function _partition(A, p, r) {
								var x = this._get(A, p);
								var i = p;
								var j = r;
								while (true) {
									while (this.compareFunction(x, this._get(A, j))) j--;
									while (this.compareFunction(this._get(A, i), x)) i++;
									if (i < j) {
										this._swap(A, i, j);
										i++;
										j--;
									} else return j;
								}
							}
						},
						{
							key: "_get",
							value: function _get(object, index) {
								if (object instanceof LinkedList) return object.get_object_at(index);
								else return object[index];
							}
						},
						{
							key: "_set",
							value: function _set(object, index, value) {
								if (object instanceof LinkedList) object.set_object_at(index, value);
								else object[index] = value;
							}
						},
						{
							key: "_swap",
							value: function _swap(A, i, j) {
								var temp = this._get(A, i);
								this._set(A, i, this._get(A, j));
								this._set(A, j, temp);
							}
						},
						{
							key: "_defaultCompareFunction",
							value: function _defaultCompareFunction(a, b) {
								return b > a;
							}
						}
					]);
					return Quicksort;
				}();
			}),
			(function(module$38, exports$36, __webpack_require__) {
				"use strict";
				var _createClass = function() {
					function defineProperties(target, props) {
						for (var i = 0; i < props.length; i++) {
							var descriptor = props[i];
							descriptor.enumerable = descriptor.enumerable || false;
							descriptor.configurable = true;
							if ("value" in descriptor) descriptor.writable = true;
							Object.defineProperty(target, descriptor.key, descriptor);
						}
					}
					return function(Constructor, protoProps, staticProps) {
						if (protoProps) defineProperties(Constructor.prototype, protoProps);
						if (staticProps) defineProperties(Constructor, staticProps);
						return Constructor;
					};
				}();
				function _classCallCheck(instance, Constructor) {
					if (!(instance instanceof Constructor)) throw new TypeError("Cannot call a class as a function");
				}
				module$38.exports = function() {
					function NeedlemanWunsch(sequence1, sequence2) {
						var match_score = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : 1;
						var mismatch_penalty = arguments.length > 3 && arguments[3] !== void 0 ? arguments[3] : -1;
						var gap_penalty = arguments.length > 4 && arguments[4] !== void 0 ? arguments[4] : -1;
						_classCallCheck(this, NeedlemanWunsch);
						this.sequence1 = sequence1;
						this.sequence2 = sequence2;
						this.match_score = match_score;
						this.mismatch_penalty = mismatch_penalty;
						this.gap_penalty = gap_penalty;
						this.iMax = sequence1.length + 1;
						this.jMax = sequence2.length + 1;
						this.grid = new Array(this.iMax);
						for (var i = 0; i < this.iMax; i++) {
							this.grid[i] = new Array(this.jMax);
							for (var j = 0; j < this.jMax; j++) this.grid[i][j] = 0;
						}
						this.tracebackGrid = new Array(this.iMax);
						for (var _i = 0; _i < this.iMax; _i++) {
							this.tracebackGrid[_i] = new Array(this.jMax);
							for (var _j = 0; _j < this.jMax; _j++) this.tracebackGrid[_i][_j] = [
								null,
								null,
								null
							];
						}
						this.alignments = [];
						this.score = -1;
						this.computeGrids();
					}
					_createClass(NeedlemanWunsch, [
						{
							key: "getScore",
							value: function getScore() {
								return this.score;
							}
						},
						{
							key: "getAlignments",
							value: function getAlignments() {
								return this.alignments;
							}
						},
						{
							key: "computeGrids",
							value: function computeGrids() {
								for (var j = 1; j < this.jMax; j++) {
									this.grid[0][j] = this.grid[0][j - 1] + this.gap_penalty;
									this.tracebackGrid[0][j] = [
										false,
										false,
										true
									];
								}
								for (var i = 1; i < this.iMax; i++) {
									this.grid[i][0] = this.grid[i - 1][0] + this.gap_penalty;
									this.tracebackGrid[i][0] = [
										false,
										true,
										false
									];
								}
								for (var _i2 = 1; _i2 < this.iMax; _i2++) for (var _j2 = 1; _j2 < this.jMax; _j2++) {
									var diag = void 0;
									if (this.sequence1[_i2 - 1] === this.sequence2[_j2 - 1]) diag = this.grid[_i2 - 1][_j2 - 1] + this.match_score;
									else diag = this.grid[_i2 - 1][_j2 - 1] + this.mismatch_penalty;
									var up = this.grid[_i2 - 1][_j2] + this.gap_penalty;
									var left = this.grid[_i2][_j2 - 1] + this.gap_penalty;
									var maxOf = [
										diag,
										up,
										left
									];
									var indices = this.arrayAllMaxIndexes(maxOf);
									this.grid[_i2][_j2] = maxOf[indices[0]];
									this.tracebackGrid[_i2][_j2] = [
										indices.includes(0),
										indices.includes(1),
										indices.includes(2)
									];
								}
								this.score = this.grid[this.iMax - 1][this.jMax - 1];
							}
						},
						{
							key: "alignmentTraceback",
							value: function alignmentTraceback() {
								var inProcessAlignments = [];
								inProcessAlignments.push({
									pos: [this.sequence1.length, this.sequence2.length],
									seq1: "",
									seq2: ""
								});
								while (inProcessAlignments[0]) {
									var current = inProcessAlignments[0];
									var directions = this.tracebackGrid[current.pos[0]][current.pos[1]];
									if (directions[0]) inProcessAlignments.push({
										pos: [current.pos[0] - 1, current.pos[1] - 1],
										seq1: this.sequence1[current.pos[0] - 1] + current.seq1,
										seq2: this.sequence2[current.pos[1] - 1] + current.seq2
									});
									if (directions[1]) inProcessAlignments.push({
										pos: [current.pos[0] - 1, current.pos[1]],
										seq1: this.sequence1[current.pos[0] - 1] + current.seq1,
										seq2: "-" + current.seq2
									});
									if (directions[2]) inProcessAlignments.push({
										pos: [current.pos[0], current.pos[1] - 1],
										seq1: "-" + current.seq1,
										seq2: this.sequence2[current.pos[1] - 1] + current.seq2
									});
									if (current.pos[0] === 0 && current.pos[1] === 0) this.alignments.push({
										sequence1: current.seq1,
										sequence2: current.seq2
									});
									inProcessAlignments.shift();
								}
								return this.alignments;
							}
						},
						{
							key: "getAllIndexes",
							value: function getAllIndexes(arr, val) {
								var indexes = [], i = -1;
								while ((i = arr.indexOf(val, i + 1)) !== -1) indexes.push(i);
								return indexes;
							}
						},
						{
							key: "arrayAllMaxIndexes",
							value: function arrayAllMaxIndexes(array) {
								return this.getAllIndexes(array, Math.max.apply(null, array));
							}
						}
					]);
					return NeedlemanWunsch;
				}();
			}),
			(function(module$39, exports$37, __webpack_require__) {
				"use strict";
				var layoutBase = function layoutBase() {};
				layoutBase.FDLayout = __webpack_require__(18);
				layoutBase.FDLayoutConstants = __webpack_require__(7);
				layoutBase.FDLayoutEdge = __webpack_require__(19);
				layoutBase.FDLayoutNode = __webpack_require__(20);
				layoutBase.DimensionD = __webpack_require__(21);
				layoutBase.HashMap = __webpack_require__(22);
				layoutBase.HashSet = __webpack_require__(23);
				layoutBase.IGeometry = __webpack_require__(8);
				layoutBase.IMath = __webpack_require__(9);
				layoutBase.Integer = __webpack_require__(10);
				layoutBase.Point = __webpack_require__(12);
				layoutBase.PointD = __webpack_require__(4);
				layoutBase.RandomSeed = __webpack_require__(16);
				layoutBase.RectangleD = __webpack_require__(13);
				layoutBase.Transform = __webpack_require__(17);
				layoutBase.UniqueIDGeneretor = __webpack_require__(14);
				layoutBase.Quicksort = __webpack_require__(24);
				layoutBase.LinkedList = __webpack_require__(11);
				layoutBase.LGraphObject = __webpack_require__(2);
				layoutBase.LGraph = __webpack_require__(5);
				layoutBase.LEdge = __webpack_require__(1);
				layoutBase.LGraphManager = __webpack_require__(6);
				layoutBase.LNode = __webpack_require__(3);
				layoutBase.Layout = __webpack_require__(15);
				layoutBase.LayoutConstants = __webpack_require__(0);
				layoutBase.NeedlemanWunsch = __webpack_require__(25);
				module$39.exports = layoutBase;
			}),
			(function(module$40, exports$38, __webpack_require__) {
				"use strict";
				function Emitter() {
					this.listeners = [];
				}
				var p = Emitter.prototype;
				p.addListener = function(event, callback) {
					this.listeners.push({
						event,
						callback
					});
				};
				p.removeListener = function(event, callback) {
					for (var i = this.listeners.length; i >= 0; i--) {
						var l = this.listeners[i];
						if (l.event === event && l.callback === callback) this.listeners.splice(i, 1);
					}
				};
				p.emit = function(event, data) {
					for (var i = 0; i < this.listeners.length; i++) {
						var l = this.listeners[i];
						if (event === l.event) l.callback(data);
					}
				};
				module$40.exports = Emitter;
			})
		]);
	});
}));
//#endregion
//#region node_modules/cose-base/cose-base.js
var require_cose_base = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	(function webpackUniversalModuleDefinition(root, factory) {
		if (typeof exports === "object" && typeof module === "object") module.exports = factory(require_layout_base());
		else if (typeof define === "function" && define.amd) define(["layout-base"], factory);
		else if (typeof exports === "object") exports["coseBase"] = factory(require_layout_base());
		else root["coseBase"] = factory(root["layoutBase"]);
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
			return __webpack_require__(__webpack_require__.s = 7);
		})([
			(function(module$3, exports$2) {
				module$3.exports = __WEBPACK_EXTERNAL_MODULE_0__;
			}),
			(function(module$4, exports$3, __webpack_require__) {
				"use strict";
				var FDLayoutConstants = __webpack_require__(0).FDLayoutConstants;
				function CoSEConstants() {}
				for (var prop in FDLayoutConstants) CoSEConstants[prop] = FDLayoutConstants[prop];
				CoSEConstants.DEFAULT_USE_MULTI_LEVEL_SCALING = false;
				CoSEConstants.DEFAULT_RADIAL_SEPARATION = FDLayoutConstants.DEFAULT_EDGE_LENGTH;
				CoSEConstants.DEFAULT_COMPONENT_SEPERATION = 60;
				CoSEConstants.TILE = true;
				CoSEConstants.TILING_PADDING_VERTICAL = 10;
				CoSEConstants.TILING_PADDING_HORIZONTAL = 10;
				CoSEConstants.TREE_REDUCTION_ON_INCREMENTAL = false;
				module$4.exports = CoSEConstants;
			}),
			(function(module$5, exports$4, __webpack_require__) {
				"use strict";
				var FDLayoutEdge = __webpack_require__(0).FDLayoutEdge;
				function CoSEEdge(source, target, vEdge) {
					FDLayoutEdge.call(this, source, target, vEdge);
				}
				CoSEEdge.prototype = Object.create(FDLayoutEdge.prototype);
				for (var prop in FDLayoutEdge) CoSEEdge[prop] = FDLayoutEdge[prop];
				module$5.exports = CoSEEdge;
			}),
			(function(module$6, exports$5, __webpack_require__) {
				"use strict";
				var LGraph = __webpack_require__(0).LGraph;
				function CoSEGraph(parent, graphMgr, vGraph) {
					LGraph.call(this, parent, graphMgr, vGraph);
				}
				CoSEGraph.prototype = Object.create(LGraph.prototype);
				for (var prop in LGraph) CoSEGraph[prop] = LGraph[prop];
				module$6.exports = CoSEGraph;
			}),
			(function(module$7, exports$6, __webpack_require__) {
				"use strict";
				var LGraphManager = __webpack_require__(0).LGraphManager;
				function CoSEGraphManager(layout) {
					LGraphManager.call(this, layout);
				}
				CoSEGraphManager.prototype = Object.create(LGraphManager.prototype);
				for (var prop in LGraphManager) CoSEGraphManager[prop] = LGraphManager[prop];
				module$7.exports = CoSEGraphManager;
			}),
			(function(module$8, exports$7, __webpack_require__) {
				"use strict";
				var FDLayoutNode = __webpack_require__(0).FDLayoutNode;
				var IMath = __webpack_require__(0).IMath;
				function CoSENode(gm, loc, size, vNode) {
					FDLayoutNode.call(this, gm, loc, size, vNode);
				}
				CoSENode.prototype = Object.create(FDLayoutNode.prototype);
				for (var prop in FDLayoutNode) CoSENode[prop] = FDLayoutNode[prop];
				CoSENode.prototype.move = function() {
					var layout = this.graphManager.getLayout();
					this.displacementX = layout.coolingFactor * (this.springForceX + this.repulsionForceX + this.gravitationForceX) / this.noOfChildren;
					this.displacementY = layout.coolingFactor * (this.springForceY + this.repulsionForceY + this.gravitationForceY) / this.noOfChildren;
					if (Math.abs(this.displacementX) > layout.coolingFactor * layout.maxNodeDisplacement) this.displacementX = layout.coolingFactor * layout.maxNodeDisplacement * IMath.sign(this.displacementX);
					if (Math.abs(this.displacementY) > layout.coolingFactor * layout.maxNodeDisplacement) this.displacementY = layout.coolingFactor * layout.maxNodeDisplacement * IMath.sign(this.displacementY);
					if (this.child == null) this.moveBy(this.displacementX, this.displacementY);
					else if (this.child.getNodes().length == 0) this.moveBy(this.displacementX, this.displacementY);
					else this.propogateDisplacementToChildren(this.displacementX, this.displacementY);
					layout.totalDisplacement += Math.abs(this.displacementX) + Math.abs(this.displacementY);
					this.springForceX = 0;
					this.springForceY = 0;
					this.repulsionForceX = 0;
					this.repulsionForceY = 0;
					this.gravitationForceX = 0;
					this.gravitationForceY = 0;
					this.displacementX = 0;
					this.displacementY = 0;
				};
				CoSENode.prototype.propogateDisplacementToChildren = function(dX, dY) {
					var nodes = this.getChild().getNodes();
					var node;
					for (var i = 0; i < nodes.length; i++) {
						node = nodes[i];
						if (node.getChild() == null) {
							node.moveBy(dX, dY);
							node.displacementX += dX;
							node.displacementY += dY;
						} else node.propogateDisplacementToChildren(dX, dY);
					}
				};
				CoSENode.prototype.setPred1 = function(pred1) {
					this.pred1 = pred1;
				};
				CoSENode.prototype.getPred1 = function() {
					return pred1;
				};
				CoSENode.prototype.getPred2 = function() {
					return pred2;
				};
				CoSENode.prototype.setNext = function(next) {
					this.next = next;
				};
				CoSENode.prototype.getNext = function() {
					return next;
				};
				CoSENode.prototype.setProcessed = function(processed) {
					this.processed = processed;
				};
				CoSENode.prototype.isProcessed = function() {
					return processed;
				};
				module$8.exports = CoSENode;
			}),
			(function(module$9, exports$8, __webpack_require__) {
				"use strict";
				var FDLayout = __webpack_require__(0).FDLayout;
				var CoSEGraphManager = __webpack_require__(4);
				var CoSEGraph = __webpack_require__(3);
				var CoSENode = __webpack_require__(5);
				var CoSEEdge = __webpack_require__(2);
				var CoSEConstants = __webpack_require__(1);
				var FDLayoutConstants = __webpack_require__(0).FDLayoutConstants;
				var LayoutConstants = __webpack_require__(0).LayoutConstants;
				var Point = __webpack_require__(0).Point;
				var PointD = __webpack_require__(0).PointD;
				var Layout = __webpack_require__(0).Layout;
				var Integer = __webpack_require__(0).Integer;
				var IGeometry = __webpack_require__(0).IGeometry;
				var LGraph = __webpack_require__(0).LGraph;
				var Transform = __webpack_require__(0).Transform;
				function CoSELayout() {
					FDLayout.call(this);
					this.toBeTiled = {};
				}
				CoSELayout.prototype = Object.create(FDLayout.prototype);
				for (var prop in FDLayout) CoSELayout[prop] = FDLayout[prop];
				CoSELayout.prototype.newGraphManager = function() {
					var gm = new CoSEGraphManager(this);
					this.graphManager = gm;
					return gm;
				};
				CoSELayout.prototype.newGraph = function(vGraph) {
					return new CoSEGraph(null, this.graphManager, vGraph);
				};
				CoSELayout.prototype.newNode = function(vNode) {
					return new CoSENode(this.graphManager, vNode);
				};
				CoSELayout.prototype.newEdge = function(vEdge) {
					return new CoSEEdge(null, null, vEdge);
				};
				CoSELayout.prototype.initParameters = function() {
					FDLayout.prototype.initParameters.call(this, arguments);
					if (!this.isSubLayout) {
						if (CoSEConstants.DEFAULT_EDGE_LENGTH < 10) this.idealEdgeLength = 10;
						else this.idealEdgeLength = CoSEConstants.DEFAULT_EDGE_LENGTH;
						this.useSmartIdealEdgeLengthCalculation = CoSEConstants.DEFAULT_USE_SMART_IDEAL_EDGE_LENGTH_CALCULATION;
						this.springConstant = FDLayoutConstants.DEFAULT_SPRING_STRENGTH;
						this.repulsionConstant = FDLayoutConstants.DEFAULT_REPULSION_STRENGTH;
						this.gravityConstant = FDLayoutConstants.DEFAULT_GRAVITY_STRENGTH;
						this.compoundGravityConstant = FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_STRENGTH;
						this.gravityRangeFactor = FDLayoutConstants.DEFAULT_GRAVITY_RANGE_FACTOR;
						this.compoundGravityRangeFactor = FDLayoutConstants.DEFAULT_COMPOUND_GRAVITY_RANGE_FACTOR;
						this.prunedNodesAll = [];
						this.growTreeIterations = 0;
						this.afterGrowthIterations = 0;
						this.isTreeGrowing = false;
						this.isGrowthFinished = false;
						this.coolingCycle = 0;
						this.maxCoolingCycle = this.maxIterations / FDLayoutConstants.CONVERGENCE_CHECK_PERIOD;
						this.finalTemperature = FDLayoutConstants.CONVERGENCE_CHECK_PERIOD / this.maxIterations;
						this.coolingAdjuster = 1;
					}
				};
				CoSELayout.prototype.layout = function() {
					if (LayoutConstants.DEFAULT_CREATE_BENDS_AS_NEEDED) {
						this.createBendpoints();
						this.graphManager.resetAllEdges();
					}
					this.level = 0;
					return this.classicLayout();
				};
				CoSELayout.prototype.classicLayout = function() {
					this.nodesWithGravity = this.calculateNodesToApplyGravitationTo();
					this.graphManager.setAllNodesToApplyGravitation(this.nodesWithGravity);
					this.calcNoOfChildrenForAllNodes();
					this.graphManager.calcLowestCommonAncestors();
					this.graphManager.calcInclusionTreeDepths();
					this.graphManager.getRoot().calcEstimatedSize();
					this.calcIdealEdgeLengths();
					if (!this.incremental) {
						var forest = this.getFlatForest();
						if (forest.length > 0) this.positionNodesRadially(forest);
						else {
							this.reduceTrees();
							this.graphManager.resetAllNodesToApplyGravitation();
							var allNodes = new Set(this.getAllNodes());
							var intersection = this.nodesWithGravity.filter(function(x) {
								return allNodes.has(x);
							});
							this.graphManager.setAllNodesToApplyGravitation(intersection);
							this.positionNodesRandomly();
						}
					} else if (CoSEConstants.TREE_REDUCTION_ON_INCREMENTAL) {
						this.reduceTrees();
						this.graphManager.resetAllNodesToApplyGravitation();
						var allNodes = new Set(this.getAllNodes());
						var intersection = this.nodesWithGravity.filter(function(x) {
							return allNodes.has(x);
						});
						this.graphManager.setAllNodesToApplyGravitation(intersection);
					}
					this.initSpringEmbedder();
					this.runSpringEmbedder();
					return true;
				};
				CoSELayout.prototype.tick = function() {
					this.totalIterations++;
					if (this.totalIterations === this.maxIterations && !this.isTreeGrowing && !this.isGrowthFinished) if (this.prunedNodesAll.length > 0) this.isTreeGrowing = true;
					else return true;
					if (this.totalIterations % FDLayoutConstants.CONVERGENCE_CHECK_PERIOD == 0 && !this.isTreeGrowing && !this.isGrowthFinished) {
						if (this.isConverged()) if (this.prunedNodesAll.length > 0) this.isTreeGrowing = true;
						else return true;
						this.coolingCycle++;
						if (this.layoutQuality == 0) this.coolingAdjuster = this.coolingCycle;
						else if (this.layoutQuality == 1) this.coolingAdjuster = this.coolingCycle / 3;
						this.coolingFactor = Math.max(this.initialCoolingFactor - Math.pow(this.coolingCycle, Math.log(100 * (this.initialCoolingFactor - this.finalTemperature)) / Math.log(this.maxCoolingCycle)) / 100 * this.coolingAdjuster, this.finalTemperature);
						this.animationPeriod = Math.ceil(this.initialAnimationPeriod * Math.sqrt(this.coolingFactor));
					}
					if (this.isTreeGrowing) {
						if (this.growTreeIterations % 10 == 0) if (this.prunedNodesAll.length > 0) {
							this.graphManager.updateBounds();
							this.updateGrid();
							this.growTree(this.prunedNodesAll);
							this.graphManager.resetAllNodesToApplyGravitation();
							var allNodes = new Set(this.getAllNodes());
							var intersection = this.nodesWithGravity.filter(function(x) {
								return allNodes.has(x);
							});
							this.graphManager.setAllNodesToApplyGravitation(intersection);
							this.graphManager.updateBounds();
							this.updateGrid();
							this.coolingFactor = FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL;
						} else {
							this.isTreeGrowing = false;
							this.isGrowthFinished = true;
						}
						this.growTreeIterations++;
					}
					if (this.isGrowthFinished) {
						if (this.isConverged()) return true;
						if (this.afterGrowthIterations % 10 == 0) {
							this.graphManager.updateBounds();
							this.updateGrid();
						}
						this.coolingFactor = FDLayoutConstants.DEFAULT_COOLING_FACTOR_INCREMENTAL * ((100 - this.afterGrowthIterations) / 100);
						this.afterGrowthIterations++;
					}
					var gridUpdateAllowed = !this.isTreeGrowing && !this.isGrowthFinished;
					var forceToNodeSurroundingUpdate = this.growTreeIterations % 10 == 1 && this.isTreeGrowing || this.afterGrowthIterations % 10 == 1 && this.isGrowthFinished;
					this.totalDisplacement = 0;
					this.graphManager.updateBounds();
					this.calcSpringForces();
					this.calcRepulsionForces(gridUpdateAllowed, forceToNodeSurroundingUpdate);
					this.calcGravitationalForces();
					this.moveNodes();
					this.animate();
					return false;
				};
				CoSELayout.prototype.getPositionsData = function() {
					var allNodes = this.graphManager.getAllNodes();
					var pData = {};
					for (var i = 0; i < allNodes.length; i++) {
						var rect = allNodes[i].rect;
						var id = allNodes[i].id;
						pData[id] = {
							id,
							x: rect.getCenterX(),
							y: rect.getCenterY(),
							w: rect.width,
							h: rect.height
						};
					}
					return pData;
				};
				CoSELayout.prototype.runSpringEmbedder = function() {
					this.initialAnimationPeriod = 25;
					this.animationPeriod = this.initialAnimationPeriod;
					var layoutEnded = false;
					if (FDLayoutConstants.ANIMATE === "during") this.emit("layoutstarted");
					else {
						while (!layoutEnded) layoutEnded = this.tick();
						this.graphManager.updateBounds();
					}
				};
				CoSELayout.prototype.calculateNodesToApplyGravitationTo = function() {
					var nodeList = [];
					var graph;
					var graphs = this.graphManager.getGraphs();
					var size = graphs.length;
					var i;
					for (i = 0; i < size; i++) {
						graph = graphs[i];
						graph.updateConnected();
						if (!graph.isConnected) nodeList = nodeList.concat(graph.getNodes());
					}
					return nodeList;
				};
				CoSELayout.prototype.createBendpoints = function() {
					var edges = [];
					edges = edges.concat(this.graphManager.getAllEdges());
					var visited = /* @__PURE__ */ new Set();
					var i;
					for (i = 0; i < edges.length; i++) {
						var edge = edges[i];
						if (!visited.has(edge)) {
							var source = edge.getSource();
							var target = edge.getTarget();
							if (source == target) {
								edge.getBendpoints().push(new PointD());
								edge.getBendpoints().push(new PointD());
								this.createDummyNodesForBendpoints(edge);
								visited.add(edge);
							} else {
								var edgeList = [];
								edgeList = edgeList.concat(source.getEdgeListToNode(target));
								edgeList = edgeList.concat(target.getEdgeListToNode(source));
								if (!visited.has(edgeList[0])) {
									if (edgeList.length > 1) {
										var k;
										for (k = 0; k < edgeList.length; k++) {
											var multiEdge = edgeList[k];
											multiEdge.getBendpoints().push(new PointD());
											this.createDummyNodesForBendpoints(multiEdge);
										}
									}
									edgeList.forEach(function(edge) {
										visited.add(edge);
									});
								}
							}
						}
						if (visited.size == edges.length) break;
					}
				};
				CoSELayout.prototype.positionNodesRadially = function(forest) {
					var currentStartingPoint = new Point(0, 0);
					var numberOfColumns = Math.ceil(Math.sqrt(forest.length));
					var height = 0;
					var currentY = 0;
					var currentX = 0;
					var point = new PointD(0, 0);
					for (var i = 0; i < forest.length; i++) {
						if (i % numberOfColumns == 0) {
							currentX = 0;
							currentY = height;
							if (i != 0) currentY += CoSEConstants.DEFAULT_COMPONENT_SEPERATION;
							height = 0;
						}
						var tree = forest[i];
						var centerNode = Layout.findCenterOfTree(tree);
						currentStartingPoint.x = currentX;
						currentStartingPoint.y = currentY;
						point = CoSELayout.radialLayout(tree, centerNode, currentStartingPoint);
						if (point.y > height) height = Math.floor(point.y);
						currentX = Math.floor(point.x + CoSEConstants.DEFAULT_COMPONENT_SEPERATION);
					}
					this.transform(new PointD(LayoutConstants.WORLD_CENTER_X - point.x / 2, LayoutConstants.WORLD_CENTER_Y - point.y / 2));
				};
				CoSELayout.radialLayout = function(tree, centerNode, startingPoint) {
					var radialSep = Math.max(this.maxDiagonalInTree(tree), CoSEConstants.DEFAULT_RADIAL_SEPARATION);
					CoSELayout.branchRadialLayout(centerNode, null, 0, 359, 0, radialSep);
					var bounds = LGraph.calculateBounds(tree);
					var transform = new Transform();
					transform.setDeviceOrgX(bounds.getMinX());
					transform.setDeviceOrgY(bounds.getMinY());
					transform.setWorldOrgX(startingPoint.x);
					transform.setWorldOrgY(startingPoint.y);
					for (var i = 0; i < tree.length; i++) tree[i].transform(transform);
					var bottomRight = new PointD(bounds.getMaxX(), bounds.getMaxY());
					return transform.inverseTransformPoint(bottomRight);
				};
				CoSELayout.branchRadialLayout = function(node, parentOfNode, startAngle, endAngle, distance, radialSeparation) {
					var halfInterval = (endAngle - startAngle + 1) / 2;
					if (halfInterval < 0) halfInterval += 180;
					var teta = (halfInterval + startAngle) % 360 * IGeometry.TWO_PI / 360;
					var x_ = distance * Math.cos(teta);
					var y_ = distance * Math.sin(teta);
					node.setCenter(x_, y_);
					var neighborEdges = [];
					neighborEdges = neighborEdges.concat(node.getEdges());
					var childCount = neighborEdges.length;
					if (parentOfNode != null) childCount--;
					var branchCount = 0;
					var incEdgesCount = neighborEdges.length;
					var startIndex;
					var edges = node.getEdgesBetween(parentOfNode);
					while (edges.length > 1) {
						var temp = edges[0];
						edges.splice(0, 1);
						var index = neighborEdges.indexOf(temp);
						if (index >= 0) neighborEdges.splice(index, 1);
						incEdgesCount--;
						childCount--;
					}
					if (parentOfNode != null) startIndex = (neighborEdges.indexOf(edges[0]) + 1) % incEdgesCount;
					else startIndex = 0;
					var stepAngle = Math.abs(endAngle - startAngle) / childCount;
					for (var i = startIndex; branchCount != childCount; i = ++i % incEdgesCount) {
						var currentNeighbor = neighborEdges[i].getOtherEnd(node);
						if (currentNeighbor == parentOfNode) continue;
						var childStartAngle = (startAngle + branchCount * stepAngle) % 360;
						var childEndAngle = (childStartAngle + stepAngle) % 360;
						CoSELayout.branchRadialLayout(currentNeighbor, node, childStartAngle, childEndAngle, distance + radialSeparation, radialSeparation);
						branchCount++;
					}
				};
				CoSELayout.maxDiagonalInTree = function(tree) {
					var maxDiagonal = Integer.MIN_VALUE;
					for (var i = 0; i < tree.length; i++) {
						var diagonal = tree[i].getDiagonal();
						if (diagonal > maxDiagonal) maxDiagonal = diagonal;
					}
					return maxDiagonal;
				};
				CoSELayout.prototype.calcRepulsionRange = function() {
					return 2 * (this.level + 1) * this.idealEdgeLength;
				};
				CoSELayout.prototype.groupZeroDegreeMembers = function() {
					var self = this;
					var tempMemberGroups = {};
					this.memberGroups = {};
					this.idToDummyNode = {};
					var zeroDegree = [];
					var allNodes = this.graphManager.getAllNodes();
					for (var i = 0; i < allNodes.length; i++) {
						var node = allNodes[i];
						var parent = node.getParent();
						if (this.getNodeDegreeWithChildren(node) === 0 && (parent.id == void 0 || !this.getToBeTiled(parent))) zeroDegree.push(node);
					}
					for (var i = 0; i < zeroDegree.length; i++) {
						var node = zeroDegree[i];
						var p_id = node.getParent().id;
						if (typeof tempMemberGroups[p_id] === "undefined") tempMemberGroups[p_id] = [];
						tempMemberGroups[p_id] = tempMemberGroups[p_id].concat(node);
					}
					Object.keys(tempMemberGroups).forEach(function(p_id) {
						if (tempMemberGroups[p_id].length > 1) {
							var dummyCompoundId = "DummyCompound_" + p_id;
							self.memberGroups[dummyCompoundId] = tempMemberGroups[p_id];
							var parent = tempMemberGroups[p_id][0].getParent();
							var dummyCompound = new CoSENode(self.graphManager);
							dummyCompound.id = dummyCompoundId;
							dummyCompound.paddingLeft = parent.paddingLeft || 0;
							dummyCompound.paddingRight = parent.paddingRight || 0;
							dummyCompound.paddingBottom = parent.paddingBottom || 0;
							dummyCompound.paddingTop = parent.paddingTop || 0;
							self.idToDummyNode[dummyCompoundId] = dummyCompound;
							var dummyParentGraph = self.getGraphManager().add(self.newGraph(), dummyCompound);
							var parentGraph = parent.getChild();
							parentGraph.add(dummyCompound);
							for (var i = 0; i < tempMemberGroups[p_id].length; i++) {
								var node = tempMemberGroups[p_id][i];
								parentGraph.remove(node);
								dummyParentGraph.add(node);
							}
						}
					});
				};
				CoSELayout.prototype.clearCompounds = function() {
					var childGraphMap = {};
					var idToNode = {};
					this.performDFSOnCompounds();
					for (var i = 0; i < this.compoundOrder.length; i++) {
						idToNode[this.compoundOrder[i].id] = this.compoundOrder[i];
						childGraphMap[this.compoundOrder[i].id] = [].concat(this.compoundOrder[i].getChild().getNodes());
						this.graphManager.remove(this.compoundOrder[i].getChild());
						this.compoundOrder[i].child = null;
					}
					this.graphManager.resetAllNodes();
					this.tileCompoundMembers(childGraphMap, idToNode);
				};
				CoSELayout.prototype.clearZeroDegreeMembers = function() {
					var self = this;
					var tiledZeroDegreePack = this.tiledZeroDegreePack = [];
					Object.keys(this.memberGroups).forEach(function(id) {
						var compoundNode = self.idToDummyNode[id];
						tiledZeroDegreePack[id] = self.tileNodes(self.memberGroups[id], compoundNode.paddingLeft + compoundNode.paddingRight);
						compoundNode.rect.width = tiledZeroDegreePack[id].width;
						compoundNode.rect.height = tiledZeroDegreePack[id].height;
					});
				};
				CoSELayout.prototype.repopulateCompounds = function() {
					for (var i = this.compoundOrder.length - 1; i >= 0; i--) {
						var lCompoundNode = this.compoundOrder[i];
						var id = lCompoundNode.id;
						var horizontalMargin = lCompoundNode.paddingLeft;
						var verticalMargin = lCompoundNode.paddingTop;
						this.adjustLocations(this.tiledMemberPack[id], lCompoundNode.rect.x, lCompoundNode.rect.y, horizontalMargin, verticalMargin);
					}
				};
				CoSELayout.prototype.repopulateZeroDegreeMembers = function() {
					var self = this;
					var tiledPack = this.tiledZeroDegreePack;
					Object.keys(tiledPack).forEach(function(id) {
						var compoundNode = self.idToDummyNode[id];
						var horizontalMargin = compoundNode.paddingLeft;
						var verticalMargin = compoundNode.paddingTop;
						self.adjustLocations(tiledPack[id], compoundNode.rect.x, compoundNode.rect.y, horizontalMargin, verticalMargin);
					});
				};
				CoSELayout.prototype.getToBeTiled = function(node) {
					var id = node.id;
					if (this.toBeTiled[id] != null) return this.toBeTiled[id];
					var childGraph = node.getChild();
					if (childGraph == null) {
						this.toBeTiled[id] = false;
						return false;
					}
					var children = childGraph.getNodes();
					for (var i = 0; i < children.length; i++) {
						var theChild = children[i];
						if (this.getNodeDegree(theChild) > 0) {
							this.toBeTiled[id] = false;
							return false;
						}
						if (theChild.getChild() == null) {
							this.toBeTiled[theChild.id] = false;
							continue;
						}
						if (!this.getToBeTiled(theChild)) {
							this.toBeTiled[id] = false;
							return false;
						}
					}
					this.toBeTiled[id] = true;
					return true;
				};
				CoSELayout.prototype.getNodeDegree = function(node) {
					node.id;
					var edges = node.getEdges();
					var degree = 0;
					for (var i = 0; i < edges.length; i++) {
						var edge = edges[i];
						if (edge.getSource().id !== edge.getTarget().id) degree = degree + 1;
					}
					return degree;
				};
				CoSELayout.prototype.getNodeDegreeWithChildren = function(node) {
					var degree = this.getNodeDegree(node);
					if (node.getChild() == null) return degree;
					var children = node.getChild().getNodes();
					for (var i = 0; i < children.length; i++) {
						var child = children[i];
						degree += this.getNodeDegreeWithChildren(child);
					}
					return degree;
				};
				CoSELayout.prototype.performDFSOnCompounds = function() {
					this.compoundOrder = [];
					this.fillCompexOrderByDFS(this.graphManager.getRoot().getNodes());
				};
				CoSELayout.prototype.fillCompexOrderByDFS = function(children) {
					for (var i = 0; i < children.length; i++) {
						var child = children[i];
						if (child.getChild() != null) this.fillCompexOrderByDFS(child.getChild().getNodes());
						if (this.getToBeTiled(child)) this.compoundOrder.push(child);
					}
				};
				/**
				* This method places each zero degree member wrt given (x,y) coordinates (top left).
				*/
				CoSELayout.prototype.adjustLocations = function(organization, x, y, compoundHorizontalMargin, compoundVerticalMargin) {
					x += compoundHorizontalMargin;
					y += compoundVerticalMargin;
					var left = x;
					for (var i = 0; i < organization.rows.length; i++) {
						var row = organization.rows[i];
						x = left;
						var maxHeight = 0;
						for (var j = 0; j < row.length; j++) {
							var lnode = row[j];
							lnode.rect.x = x;
							lnode.rect.y = y;
							x += lnode.rect.width + organization.horizontalPadding;
							if (lnode.rect.height > maxHeight) maxHeight = lnode.rect.height;
						}
						y += maxHeight + organization.verticalPadding;
					}
				};
				CoSELayout.prototype.tileCompoundMembers = function(childGraphMap, idToNode) {
					var self = this;
					this.tiledMemberPack = [];
					Object.keys(childGraphMap).forEach(function(id) {
						var compoundNode = idToNode[id];
						self.tiledMemberPack[id] = self.tileNodes(childGraphMap[id], compoundNode.paddingLeft + compoundNode.paddingRight);
						compoundNode.rect.width = self.tiledMemberPack[id].width;
						compoundNode.rect.height = self.tiledMemberPack[id].height;
					});
				};
				CoSELayout.prototype.tileNodes = function(nodes, minWidth) {
					var organization = {
						rows: [],
						rowWidth: [],
						rowHeight: [],
						width: 0,
						height: minWidth,
						verticalPadding: CoSEConstants.TILING_PADDING_VERTICAL,
						horizontalPadding: CoSEConstants.TILING_PADDING_HORIZONTAL
					};
					nodes.sort(function(n1, n2) {
						if (n1.rect.width * n1.rect.height > n2.rect.width * n2.rect.height) return -1;
						if (n1.rect.width * n1.rect.height < n2.rect.width * n2.rect.height) return 1;
						return 0;
					});
					for (var i = 0; i < nodes.length; i++) {
						var lNode = nodes[i];
						if (organization.rows.length == 0) this.insertNodeToRow(organization, lNode, 0, minWidth);
						else if (this.canAddHorizontal(organization, lNode.rect.width, lNode.rect.height)) this.insertNodeToRow(organization, lNode, this.getShortestRowIndex(organization), minWidth);
						else this.insertNodeToRow(organization, lNode, organization.rows.length, minWidth);
						this.shiftToLastRow(organization);
					}
					return organization;
				};
				CoSELayout.prototype.insertNodeToRow = function(organization, node, rowIndex, minWidth) {
					var minCompoundSize = minWidth;
					if (rowIndex == organization.rows.length) {
						organization.rows.push([]);
						organization.rowWidth.push(minCompoundSize);
						organization.rowHeight.push(0);
					}
					var w = organization.rowWidth[rowIndex] + node.rect.width;
					if (organization.rows[rowIndex].length > 0) w += organization.horizontalPadding;
					organization.rowWidth[rowIndex] = w;
					if (organization.width < w) organization.width = w;
					var h = node.rect.height;
					if (rowIndex > 0) h += organization.verticalPadding;
					var extraHeight = 0;
					if (h > organization.rowHeight[rowIndex]) {
						extraHeight = organization.rowHeight[rowIndex];
						organization.rowHeight[rowIndex] = h;
						extraHeight = organization.rowHeight[rowIndex] - extraHeight;
					}
					organization.height += extraHeight;
					organization.rows[rowIndex].push(node);
				};
				CoSELayout.prototype.getShortestRowIndex = function(organization) {
					var r = -1;
					var min = Number.MAX_VALUE;
					for (var i = 0; i < organization.rows.length; i++) if (organization.rowWidth[i] < min) {
						r = i;
						min = organization.rowWidth[i];
					}
					return r;
				};
				CoSELayout.prototype.getLongestRowIndex = function(organization) {
					var r = -1;
					var max = Number.MIN_VALUE;
					for (var i = 0; i < organization.rows.length; i++) if (organization.rowWidth[i] > max) {
						r = i;
						max = organization.rowWidth[i];
					}
					return r;
				};
				/**
				* This method checks whether adding extra width to the organization violates
				* the aspect ratio(1) or not.
				*/
				CoSELayout.prototype.canAddHorizontal = function(organization, extraWidth, extraHeight) {
					var sri = this.getShortestRowIndex(organization);
					if (sri < 0) return true;
					var min = organization.rowWidth[sri];
					if (min + organization.horizontalPadding + extraWidth <= organization.width) return true;
					var hDiff = 0;
					if (organization.rowHeight[sri] < extraHeight) {
						if (sri > 0) hDiff = extraHeight + organization.verticalPadding - organization.rowHeight[sri];
					}
					var add_to_row_ratio;
					if (organization.width - min >= extraWidth + organization.horizontalPadding) add_to_row_ratio = (organization.height + hDiff) / (min + extraWidth + organization.horizontalPadding);
					else add_to_row_ratio = (organization.height + hDiff) / organization.width;
					hDiff = extraHeight + organization.verticalPadding;
					var add_new_row_ratio;
					if (organization.width < extraWidth) add_new_row_ratio = (organization.height + hDiff) / extraWidth;
					else add_new_row_ratio = (organization.height + hDiff) / organization.width;
					if (add_new_row_ratio < 1) add_new_row_ratio = 1 / add_new_row_ratio;
					if (add_to_row_ratio < 1) add_to_row_ratio = 1 / add_to_row_ratio;
					return add_to_row_ratio < add_new_row_ratio;
				};
				CoSELayout.prototype.shiftToLastRow = function(organization) {
					var longest = this.getLongestRowIndex(organization);
					var last = organization.rowWidth.length - 1;
					var row = organization.rows[longest];
					var node = row[row.length - 1];
					var diff = node.width + organization.horizontalPadding;
					if (organization.width - organization.rowWidth[last] > diff && longest != last) {
						row.splice(-1, 1);
						organization.rows[last].push(node);
						organization.rowWidth[longest] = organization.rowWidth[longest] - diff;
						organization.rowWidth[last] = organization.rowWidth[last] + diff;
						organization.width = organization.rowWidth[instance.getLongestRowIndex(organization)];
						var maxHeight = Number.MIN_VALUE;
						for (var i = 0; i < row.length; i++) if (row[i].height > maxHeight) maxHeight = row[i].height;
						if (longest > 0) maxHeight += organization.verticalPadding;
						var prevTotal = organization.rowHeight[longest] + organization.rowHeight[last];
						organization.rowHeight[longest] = maxHeight;
						if (organization.rowHeight[last] < node.height + organization.verticalPadding) organization.rowHeight[last] = node.height + organization.verticalPadding;
						var finalTotal = organization.rowHeight[longest] + organization.rowHeight[last];
						organization.height += finalTotal - prevTotal;
						this.shiftToLastRow(organization);
					}
				};
				CoSELayout.prototype.tilingPreLayout = function() {
					if (CoSEConstants.TILE) {
						this.groupZeroDegreeMembers();
						this.clearCompounds();
						this.clearZeroDegreeMembers();
					}
				};
				CoSELayout.prototype.tilingPostLayout = function() {
					if (CoSEConstants.TILE) {
						this.repopulateZeroDegreeMembers();
						this.repopulateCompounds();
					}
				};
				CoSELayout.prototype.reduceTrees = function() {
					var prunedNodesAll = [];
					var containsLeaf = true;
					var node;
					while (containsLeaf) {
						var allNodes = this.graphManager.getAllNodes();
						var prunedNodesInStepTemp = [];
						containsLeaf = false;
						for (var i = 0; i < allNodes.length; i++) {
							node = allNodes[i];
							if (node.getEdges().length == 1 && !node.getEdges()[0].isInterGraph && node.getChild() == null) {
								prunedNodesInStepTemp.push([
									node,
									node.getEdges()[0],
									node.getOwner()
								]);
								containsLeaf = true;
							}
						}
						if (containsLeaf == true) {
							var prunedNodesInStep = [];
							for (var j = 0; j < prunedNodesInStepTemp.length; j++) if (prunedNodesInStepTemp[j][0].getEdges().length == 1) {
								prunedNodesInStep.push(prunedNodesInStepTemp[j]);
								prunedNodesInStepTemp[j][0].getOwner().remove(prunedNodesInStepTemp[j][0]);
							}
							prunedNodesAll.push(prunedNodesInStep);
							this.graphManager.resetAllNodes();
							this.graphManager.resetAllEdges();
						}
					}
					this.prunedNodesAll = prunedNodesAll;
				};
				CoSELayout.prototype.growTree = function(prunedNodesAll) {
					var prunedNodesInStep = prunedNodesAll[prunedNodesAll.length - 1];
					var nodeData;
					for (var i = 0; i < prunedNodesInStep.length; i++) {
						nodeData = prunedNodesInStep[i];
						this.findPlaceforPrunedNode(nodeData);
						nodeData[2].add(nodeData[0]);
						nodeData[2].add(nodeData[1], nodeData[1].source, nodeData[1].target);
					}
					prunedNodesAll.splice(prunedNodesAll.length - 1, 1);
					this.graphManager.resetAllNodes();
					this.graphManager.resetAllEdges();
				};
				CoSELayout.prototype.findPlaceforPrunedNode = function(nodeData) {
					var gridForPrunedNode;
					var nodeToConnect;
					var prunedNode = nodeData[0];
					if (prunedNode == nodeData[1].source) nodeToConnect = nodeData[1].target;
					else nodeToConnect = nodeData[1].source;
					var startGridX = nodeToConnect.startX;
					var finishGridX = nodeToConnect.finishX;
					var startGridY = nodeToConnect.startY;
					var finishGridY = nodeToConnect.finishY;
					var controlRegions = [
						0,
						0,
						0,
						0
					];
					if (startGridY > 0) for (var i = startGridX; i <= finishGridX; i++) controlRegions[0] += this.grid[i][startGridY - 1].length + this.grid[i][startGridY].length - 1;
					if (finishGridX < this.grid.length - 1) for (var i = startGridY; i <= finishGridY; i++) controlRegions[1] += this.grid[finishGridX + 1][i].length + this.grid[finishGridX][i].length - 1;
					if (finishGridY < this.grid[0].length - 1) for (var i = startGridX; i <= finishGridX; i++) controlRegions[2] += this.grid[i][finishGridY + 1].length + this.grid[i][finishGridY].length - 1;
					if (startGridX > 0) for (var i = startGridY; i <= finishGridY; i++) controlRegions[3] += this.grid[startGridX - 1][i].length + this.grid[startGridX][i].length - 1;
					var min = Integer.MAX_VALUE;
					var minCount;
					var minIndex;
					for (var j = 0; j < controlRegions.length; j++) if (controlRegions[j] < min) {
						min = controlRegions[j];
						minCount = 1;
						minIndex = j;
					} else if (controlRegions[j] == min) minCount++;
					if (minCount == 3 && min == 0) {
						if (controlRegions[0] == 0 && controlRegions[1] == 0 && controlRegions[2] == 0) gridForPrunedNode = 1;
						else if (controlRegions[0] == 0 && controlRegions[1] == 0 && controlRegions[3] == 0) gridForPrunedNode = 0;
						else if (controlRegions[0] == 0 && controlRegions[2] == 0 && controlRegions[3] == 0) gridForPrunedNode = 3;
						else if (controlRegions[1] == 0 && controlRegions[2] == 0 && controlRegions[3] == 0) gridForPrunedNode = 2;
					} else if (minCount == 2 && min == 0) {
						var random = Math.floor(Math.random() * 2);
						if (controlRegions[0] == 0 && controlRegions[1] == 0) if (random == 0) gridForPrunedNode = 0;
						else gridForPrunedNode = 1;
						else if (controlRegions[0] == 0 && controlRegions[2] == 0) if (random == 0) gridForPrunedNode = 0;
						else gridForPrunedNode = 2;
						else if (controlRegions[0] == 0 && controlRegions[3] == 0) if (random == 0) gridForPrunedNode = 0;
						else gridForPrunedNode = 3;
						else if (controlRegions[1] == 0 && controlRegions[2] == 0) if (random == 0) gridForPrunedNode = 1;
						else gridForPrunedNode = 2;
						else if (controlRegions[1] == 0 && controlRegions[3] == 0) if (random == 0) gridForPrunedNode = 1;
						else gridForPrunedNode = 3;
						else if (random == 0) gridForPrunedNode = 2;
						else gridForPrunedNode = 3;
					} else if (minCount == 4 && min == 0) {
						var random = Math.floor(Math.random() * 4);
						gridForPrunedNode = random;
					} else gridForPrunedNode = minIndex;
					if (gridForPrunedNode == 0) prunedNode.setCenter(nodeToConnect.getCenterX(), nodeToConnect.getCenterY() - nodeToConnect.getHeight() / 2 - FDLayoutConstants.DEFAULT_EDGE_LENGTH - prunedNode.getHeight() / 2);
					else if (gridForPrunedNode == 1) prunedNode.setCenter(nodeToConnect.getCenterX() + nodeToConnect.getWidth() / 2 + FDLayoutConstants.DEFAULT_EDGE_LENGTH + prunedNode.getWidth() / 2, nodeToConnect.getCenterY());
					else if (gridForPrunedNode == 2) prunedNode.setCenter(nodeToConnect.getCenterX(), nodeToConnect.getCenterY() + nodeToConnect.getHeight() / 2 + FDLayoutConstants.DEFAULT_EDGE_LENGTH + prunedNode.getHeight() / 2);
					else prunedNode.setCenter(nodeToConnect.getCenterX() - nodeToConnect.getWidth() / 2 - FDLayoutConstants.DEFAULT_EDGE_LENGTH - prunedNode.getWidth() / 2, nodeToConnect.getCenterY());
				};
				module$9.exports = CoSELayout;
			}),
			(function(module$10, exports$9, __webpack_require__) {
				"use strict";
				var coseBase = {};
				coseBase.layoutBase = __webpack_require__(0);
				coseBase.CoSEConstants = __webpack_require__(1);
				coseBase.CoSEEdge = __webpack_require__(2);
				coseBase.CoSEGraph = __webpack_require__(3);
				coseBase.CoSEGraphManager = __webpack_require__(4);
				coseBase.CoSELayout = __webpack_require__(6);
				coseBase.CoSENode = __webpack_require__(5);
				module$10.exports = coseBase;
			})
		]);
	});
}));
//#endregion
export { require_cose_base$1 as n, require_cose_base as t };
