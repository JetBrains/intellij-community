function foo() <fold text='{...}' expand='true'>{
    echo "Empty line"
    bar() <fold text='{...}' expand='true'>{
      echo "test"
    }</fold>
}</fold>

function foo() <fold text='{...}' expand='true'>{ echo "Test" }</fold>

foo()
 <fold text='{...}' expand='true'>{
}</fold>