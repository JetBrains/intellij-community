echo "foo
bar"
#foo
#bar

# Fix for IDEA-262819
"$(md5sum <( echo "$(hostname)-$(whoami)" ) | awk '{print $1}')"

echo

echo 'foo
bar'    # No difference yet.
#foo
#bar

echo

echo foo\
bar     # Newline escaped.
#foobar

echo

echo "foo\
bar"     # Same here, as \ still interpreted as escape within weak quotes.
#foobar

echo

echo 'foo\
bar'     # Escape character \ taken literally because of strong quoting.
#foo\
#bar

# Examples suggested by Stéphane Chazelas.
# https://tldp.org/LDP/abs/html/escapingsection.html

\
test                        #lineContinuation

myVar=42
echo $((\
my\
Var))

echo ${\
my\
Var}

eval \
t\
e\
st