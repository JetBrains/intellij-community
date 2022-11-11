x<<<${2}
x<<<${2}>/dev/null
foo() {
if ! grep $1 <<< ${2} > /dev/null; then echo Boom; fi
}
