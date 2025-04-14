// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

const root = () => document.getElementById("updateButton")
const body = () => document.getElementsByTagName("body")[0]

window.onload = function () {
    const updateLink = document.createElement("a")
    updateLink.textContent = "Update page in 5 seconds"
    updateLink.setAttribute("href", "#")
    updateLink.setAttribute("onclick", "launchUpdate(5)")
    root().appendChild(updateLink)
}

function launchUpdate(seconds) {
    console.log(`${seconds} left`)
    setTimeout(() => {
            if (seconds === 0) {
                location.reload()
            } else {
                body().outerHTML = ""
                const left = document.createElement("h1")
                left.textContent = `reload in ${seconds} seconds...`
                body().appendChild(left)
            }
            seconds--
            launchUpdate(seconds)
        }
        , 1000);

}