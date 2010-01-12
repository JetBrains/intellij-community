
// example program for interpreter testing 
// contains division and modulo functins 

input a,b
functions div(x,y) = if x < y
                     then 0
                     else div(x-y,y)+1
                     fi,
          mod(x,y) = if x < y
                     then x
                     else mod(x-y,y)
                     fi
output div(a,b), mod(a,b)
arguments 324, 17
end
