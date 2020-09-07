f'{<error descr="Expression expected"><error descr="Expression fragments inside f-strings cannot include line comments">#'</error></error><EOLError descr="Type conversion, ':' or '}' expected"></EOLError><EOLError descr="' expected"></EOLError>
f'{<error descr="Expression expected"><error descr="Expression fragments inside f-strings cannot include line comments">#</error></error><EOLError descr="Type conversion, ':' or '}' expected"></EOLError><EOLError descr="' expected"></EOLError>
f'{<error descr="Expression expected"><error descr="Expression fragments inside f-strings cannot include line comments">#foo#}'</error></error><EOLError descr="Type conversion, ':' or '}' expected"></EOLError><EOLError descr="' expected"></EOLError>
f'{42:#}'
f'{42:{<error descr="Expression expected"><error descr="Expression fragments inside f-strings cannot include line comments">#}}'</error></error><EOLError descr="Type conversion, ':' or '}' expected"></EOLError><EOLError descr="' expected"></EOLError>
f'{x<error descr="Type conversion, ':' or '}' expected"> </error><error descr="Expression fragments inside f-strings cannot include line comments">### foo}'</error><EOLError descr="' expected"></EOLError>
f'{"###"}'
f'''{[
    42 <error descr="Expression fragments inside f-strings cannot include line comments"># foo</error>
]}'''
