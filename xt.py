import math


#double xt = 10 + (((double) timeInGame / gameLength) * (upperBound - 10));
#int xti = (int) (xt + 0.5);
#if (xt < 0.0) {
#  price += random.nextInt(-xti + 11) + xti;
#} else if (xt > 0.0) {
#  price += -10 + random.nextInt(xti + 11);
#} else {
#  price += -10 + random.nextInt(11);
#}

# t in (0,1]
# upperBound (-10, 30)
def x(t, upperBound):
    return 10+ t*(upperBound-10)
    
def priceDelta(t, upperBound):
    xt = x(t, upperBound)
    if xt < 0:
        return (math.ceil(xt), 10.0)
    elif xt > 0:
        return (-10.0, math.ceil(xt))
    else:
        return (-10.0, 10.0)
        
ubs = (-10, -5, 0, 5, 10, 15, 20, 25, 30)  
line = "t;"  
for ub in ubs:
    line += "ub="+ str(ub) + ";"
print line

for t in range(0, 1001, 1):
    line = ""+ str(t/1000.0) +";"
    for ub in ubs:
        delta = priceDelta(t/1000.0, ub)
        line += ""+ str(abs(delta[0]-delta[1])) +";"
        #line += ""+ str((delta[0]+delta[1])/2) +";"
    print line
        
    
