Here is a timelapse:

--- function foo is defined

function foo {
}

--- Attempting to define function bar above function foo
function bar {
function foo {
}

--- Function bar is incorrectly expanded
function bar {


}function foo {
}