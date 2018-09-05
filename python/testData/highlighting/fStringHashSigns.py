f'{<error descr="' expected"><error descr="expression expected"><error descr="type conversion, : or } expected">#</error></error></error>'
f'{<error descr="' expected"><error descr="expression expected"><error descr="type conversion, : or } expected">#</error></error></error>
f'{<error descr="' expected"><error descr="expression expected"><error descr="type conversion, : or } expected">#</error></error></error>foo#}'
f'{42:<error descr="' expected"><error descr="} expected">#</error></error>}'
f'{42:{<error descr="' expected"><error descr="expression expected"><error descr="type conversion, : or } expected">#</error></error></error>}}'
f'{x<error descr="' expected"><error descr="type conversion, : or } expected"> </error></error>### foo}'
f'{"###"}'
f'''{[
    42 <error descr="Expression fragments inside f-strings cannot include line comments"># foo</error>
]}'''
