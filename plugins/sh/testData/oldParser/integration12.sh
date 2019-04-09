valid (in bash 4, at least): function a for f in 1; do echo; done;
function a { for f in 1; do echo; done; }
f() { export a=1 b=2; }
