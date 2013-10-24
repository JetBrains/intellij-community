list(<error descr="Generator expression must be parenthesized if not sole argument">int(i) for i in '1'</error>, '2')
list(int(i) for i in ['1', '2'])
list((int(i) for i in '1'), '2')
