f'{<error descr="Expressions fragments inside f-strings cannot include '#'">#<error descr="'}' is expected"></error></error>'
<error descr="Missing closing quote [']">f'{<error descr="Expressions fragments inside f-strings cannot include '#'">#<error descr="'}' is expected"></error></error></error>
f'{<error descr="Expressions fragments inside f-strings cannot include '#'">#</error>foo<error descr="Expressions fragments inside f-strings cannot include '#'">#</error>}'
f'{42:#}'
f'{42:{<error descr="Expressions fragments inside f-strings cannot include '#'">#</error>}}'