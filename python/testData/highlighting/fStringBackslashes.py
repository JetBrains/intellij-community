f'{<error descr="expression expected"><error descr="Expression fragments inside f-strings cannot include backslashes">\</error>t</error>}'
f'{<error descr="expression expected"><error descr="Expression fragments inside f-strings cannot include backslashes">\</error>t</error><error descr="} expected">'</error>
f'{<error descr="expression expected"><error descr="Expression fragments inside f-strings cannot include backslashes">\</error>N{GREEK SMALL LETTER ALPHA}</error>}'
f'{Formatable():\n\t}'
f'{42:{<error descr="expression expected"><error descr="Expression fragments inside f-strings cannot include backslashes">\</error>t</error>}}'
f'{f"""{"<error descr="Expression fragments inside f-strings cannot include backslashes">\</error>n"}"""}'