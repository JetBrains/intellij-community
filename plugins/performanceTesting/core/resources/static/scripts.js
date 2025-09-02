// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
function show(x, y, width, height) {
    const xmlHttp = new XMLHttpRequest();
    xmlHttp.open("GET", `robot/highlight?x=${x}&y=${y}&width=${width}&height=${height}`, true);
    xmlHttp.send(null);
}