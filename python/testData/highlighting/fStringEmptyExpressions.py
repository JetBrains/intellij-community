f'{<error descr="Empty expressions are not allowed inside f-strings"></error>}'
f'{<error descr="Empty expressions are not allowed inside f-strings"></error><error descr="'}' is expected"></error>'
<error descr="Missing closing quote [']">f'{<error descr="Empty expressions are not allowed inside f-strings"></error><error descr="'}' is expected"></error></error>
f'{<error descr="Empty expressions are not allowed inside f-strings"></error>!r}'
f'{<error descr="Empty expressions are not allowed inside f-strings"></error>:2.3}'
f'{42:2.{<error descr="Empty expressions are not allowed inside f-strings"></error>}}'
f'{<error descr="Empty expressions are not allowed inside f-strings">  </error>}'
f'{42:{<error descr="Empty expressions are not allowed inside f-strings"> </error>}}'