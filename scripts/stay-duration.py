
cnt = {}

for arrival in range(0,4+1):
    for departure in range(arrival+1, 5+1):
        time = departure - arrival
        if time not in cnt:
            cnt[time] = 0
        cnt[time] += 1
        
total = 0
for time in cnt.values():
    total += time
    
    
print "Day, Cnt, Possibility, Average Guests"
for time, num in cnt.items():
    print time, num, (num*100.0/total), num*8.0/total
    
print "Total:", total
        
