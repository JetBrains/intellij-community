if (window.__IntelliJTools === undefined) {
  window.__IntelliJTools = {}
}

window.__IntelliJTools.scrollToOffset = (function () {
  var offsetToScroll;
  var attributeName;
  var closestParent;
  var closestLeftSibling;

  var getSrcFromTo = function(node) {
    if (!node || !('getAttribute' in node)) {
      return null
    }
    var attrValue = node.getAttribute(attributeName);

    if (attrValue) {
      return attrValue.split('..')
    }
    return null
  }

  var dfs = function (node) {
    closestParent = node;
    closestLeftSibling = null;
    for (var child = node.firstChild; child !== null; child = child.nextSibling) {
      var fromTo = getSrcFromTo(child);

      if (!fromTo) {
        continue
      }
      if (fromTo[1] <= offsetToScroll) {
        closestLeftSibling = child;
        continue
      }

      if (fromTo[0] <= offsetToScroll) {
        dfs(child)
      }
      break
    }
  }

  var scrollToSrcOffset = function (newOffsetToScroll, newAttributeName) {
    attributeName = newAttributeName;
    offsetToScroll = newOffsetToScroll;

    var body = document.body;
    if (!body || !body.firstChild) {
      return
    }

    dfs(body)

    var rightSibling = closestLeftSibling;
    while (closestLeftSibling !== null && rightSibling !== null) {
      rightSibling = rightSibling.nextSibling;
      if (getSrcFromTo(rightSibling) !== null) {
        break
      }
    }

    var leftSrcPos = getSrcFromTo(closestLeftSibling)
    var parentSrcPos = getSrcFromTo(closestParent)
    var rightSrcPos = getSrcFromTo(rightSibling)

    var leftBound;
    if (leftSrcPos !== null) {
      leftBound = [leftSrcPos[1], closestLeftSibling.offsetTop + closestLeftSibling.offsetHeight]
    } else {
      leftBound = [parentSrcPos[0], closestParent.offsetTop]
    }

    var rightBound;
    if (rightSrcPos !== null) {
      rightBound = [rightSrcPos[0], rightSibling.offsetTop]
    } else {
      rightBound = [parentSrcPos[1], closestParent.offsetTop + closestParent.offsetHeight]
    }

    var resultY;
    if (leftBound[0] === rightBound[0]) {
      resultY = (leftBound[1] + rightBound[1]) / 2;
    } else {
      var srcRatio = (offsetToScroll - leftBound[0]) / (rightBound[0] - leftBound[0]);
      resultY = leftBound[1] + (rightBound[1] - leftBound[1]) * srcRatio;
    }

    var height = window.innerHeight
    var newValue = resultY - height / 2;
    var oldValue = document.documentElement.scrollTop || document.body.scrollTop;

    if (Math.abs(newValue - oldValue) > 50) {
      document.documentElement.scrollTop = document.body.scrollTop = newValue;
    }
  }

  return scrollToSrcOffset
})()

