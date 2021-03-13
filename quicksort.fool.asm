push 0
push function1
push function2
push function4
push function5
lfp
lfp
lfp
push -6
add
lw
lfp
stm
ltm
ltm
push -5
add
lw
js
lfp
stm
ltm
ltm
push -2
add
lw
js
halt

function0:
cfp
lra
stm
sra
pop
pop
pop
sfp
ltm
lra
js

function1:
cfp
lra
push function0
lfp
push 1
add
lw
beq label2
push 0
b label3
label2:
push 1
label3:
push 1
beq label0
lfp
print
lfp
lfp
lw
stm
ltm
ltm
push -2
add
lw
js
lfp
stm
ltm
ltm
push -2
add
lw
js
b label1
label0:
label1:
stm
pop
sra
pop
pop
sfp
ltm
lra
js

function2:
cfp
lra
lfp
push 1
add
lw
beq label6
push 0
b label7
label6:
push 1
label7:
push 1
beq label4
b label5
label4:
lfp
push 2
add
lw
label5:
stm
sra
pop
pop
pop
sfp
ltm
lra
js

function3:
cfp
lra
lfp
lw
push 3
add
lw
push 1
beq label8
lfp
push 1
add
lw
push -1
mult
push 1
add
b label9
label8:
lfp
push 1
add
lw
label9:
stm
sra
pop
pop
sfp
ltm
lra
js

function4:
cfp
lra
push function3
lfp
push 1
add
lw
beq label12
push 0
b label13
label12:
push 1
label13:
push 1
beq label10
lfp
lfp
push 2
add
lw
bleq label16
push 0
b label17
label16:
push 1
label17:
lfp
stm
ltm
ltm
push -2
add
lw
js
push 1
beq label14
lfp
lfp
push 3
add
lw
lfp
push 2
add
lw
lfp
lw
stm
ltm
ltm
push -4
add
lw
js
b label15
label14:
label15:
b label11
label10:
label11:
stm
pop
sra
pop
pop
pop
pop
sfp
ltm
lra
js

function5:
cfp
lra
lfp
push 1
add
lw
beq label20
push 0
b label21
label20:
push 1
label21:
push 1
beq label18
b label19
label18:
push 0
label19:
lfp
push 1
add
lw
beq label24
push 0
b label25
label24:
push 1
label25:
push 1
beq label22
lfp
lfp
lfp
push 1
lfp
push -2
add
lw
lfp
lw
stm
ltm
ltm
push -4
add
lw
js
lfp
lw
stm
ltm
ltm
push -5
add
lw
js
lfp
lw
stm
ltm
ltm
push -3
add
lw
js
b label23
label22:
label23:
stm
pop
sra
pop
pop
sfp
ltm
lra
js