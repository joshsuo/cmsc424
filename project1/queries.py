queries = ["" for i in range(0, 12)]
### EXAMPLE
### 0. List all airport codes and their cities. Order by the city name in the increasing order.
### Output column order: airportid, city

queries[0] = """
select airportid, city
from airports
order by city;
"""

### 1. Write a query to find the names of customers who have flights on a Monday and 
###    first name that has a second letter is not a vowel [a, e, i, o, u].
###    If a customer who satisfies the condition flies on multiple Mondays, output their name only once.
###    Do not include the oldest customer among those that satisfies the above conditions in the results.
### Hint:  - See postgresql date operators that are linked to from the README, and the "like" operator (see Chapter 3.4.2). 
###        - Alternately, you can use a regex to match the condition imposed on the name.
###        - See postgresql date operators and string functions
###        - You may want to use a self-join to avoid including the oldest customer.
###        - When testing, write a query that includes all customers, then modify that to exclude the oldest.
### Order: by name
### Output columns: name
queries[1] = """
select distinct name
from customers natural join flewon
inner join (select customerid 
from customers natural join flewon 
where date_part('dow', flightdate) = 1
and regexp_match(substring(name, 2, 1),'(a|e|i|o|u)') is  null
order by birthdate limit 1) as oldest on oldest.customerid <> customers.customerid
where date_part('dow', flightdate) = 1
and regexp_match(substring(name, 2, 1),'(a|e|i|o|u)') is  null
order by name;
"""


### 2. Write a query to find customers who are frequent fliers on Delta Airlines (DL) 
###    and have their birthday are either before 02/15 or after 11/15 (mm/dd). 
### Hint: See postgresql date functions.
### Order: by birthdate
### Output columns: customer id, name, birthdate
queries[2] = """
select customerid, name, birthdate
from customers
where
(
date_part('doy', birthdate) < 46
or
date_part('doy', birthdate) > 319
)
and frequentflieron = 'DL'
order by birthdate;
"""

### 3. Write a query to rank the customers who have taken most number of flights with their
###    frequentflieron airline, along with their name, airlineid, and number of times they 
###    have flown with the airlines. If any ties make the top 10 rankings exceed 10 results 
###    (ex. the number of most flights is shared by 20 people), list all such customers.
### Output: (rank, name, airlineid count)
### Order: rank, name
### HINT: You can use self join to rank customers based on the number of flights.
queries[3] = """
select ranknum, customers.name, customers.frequentflieron, ranker.cnt from 
(select * , rank() over ( order by cnt desc) as rankNum
from (
select count(*) as cnt, customerid
from (
select flightid, flewon.customerid, customers.frequentflieron
from flewon join customers
on customers.customerid = flewon.customerid
where substring(flightid for 2) = customers.frequentflieron)
as ff
group by ff.customerid)
as rankf)
as ranker
join customers
on customers.customerid = ranker.customerid
where ranknum <= 10
order by ranknum, name;
"""

### 4. Write a query to find the airlines with the least number of customers that 
###    choose it as their frequent flier airline. For example, if 10 customers have Delta
###    listed as their frequent flier airline, and no other airlines have fewer than 10
###    frequent flier customers, then the query should return  "DELTA, 10" as the
###    only result. In the case of a tie, return all tied airlines.
### Hint: use `with clause` and nested queries (Chapter 3.8.6). 
### Output: name, count
### Order: name
queries[4] = """
with ff as 
(select count(*) as cnt, airlines.name
from customers join airlines
on customers.frequentflieron = airlines.airlineid
group by airlines.name)
select name, min(cnt)
from ff
where cnt = (select min(cnt) from ff)
group by name
order by name;
"""


### 5. Write a query to find the most-frequent flyers (customers who have flown on most number of flights).
###    In this dataset and in general, always assume that there could be multiple flyers who satisfy this condition.
###    Assuming multiple customers exist, list the customer names along with the count of other frequent flyers
###    they have flown with.
###    Two customers are said to have flown together when they have a flewon entry with a matching flightid and flightdate.
###    For example if Alice, Bob and Charlie flew on the most number of flighs (3 each). Assuming Alice and Bob never flew together,
###    while Charlie flew with both of them, the expected output would be: [('Alice', 1), ('Bob', 1), ('Charlie', 2)].
### NOTE: A frequent flyer here is purely based on number of occurances in flewon, (not the frequentflieron field).
### Output: name, count
### Order: order by count desc, name.
queries[5] = """
with a1 as (
select count(*) as cnt, customerid from flewon group by customerid order by count(*) desc
)
, a2 as (
select customerid, flightid, flightdate from a1  natural join flewon f where a1.cnt=(select max(cnt) from a1)
)
, a3 as(
select a21.customerid as myid, a22.customerid as otherid, a21.flightid,a21.flightdate from a2 as a21 left outer join a2 a22 on a21.flightid=a22.flightid and a21.flightdate=a22.flightdate and a21.customerid!=a22.customerid where a22.customerid is not null
)
select c.name, aaaa.ccnt from (
select myid, count(*) as ccnt from
( select distinct myid, otherid from a3
order by a3.myid
) as aaa group by myid
) aaaa join customers as c on c.customerid=aaaa.myid
order by aaaa.ccnt desc, name
;
"""

### 6. Write a query to find the percentage participation of American Airlines in each airport, relative to the other airlines.
### One instance of participation in an airport is defined as a flight (EX. AA150) having a source or dest of that airport.
### If AA101 leaves OAK and arrives in DFW, that adds 1 to American's count for both OAK and DFW airports.
### This means that if AA has 1 in DFW, UA has 1 in DFW, DL has 2 in DFW, and SW has 3 in DFW, the query returns:
###     airport 		                              | participation
###     Dallas Fort Worth International           |  .14
### Output: (airport_name, participation).
### Order: Participation in descending order, airport name
### Note: - The airport column must be the full name of the airport
###       - The participation percentage is rounded to 2 decimals, as shown above
###       - You do not need to confirm that the flights actually occur by referencing the flewon table. This query is only concerned with
###         flights that exist in the flights table.
###       - You must not leave out airports that have no AA flights (participation of 0)
queries[6] = """
select airports.name, ratio 
from
(
    select airports.airportid, airports.name, cnttotal, cntaa,
    coalesce(cast(cast(aa1.cntaa as double precision)/cast(total1.cnttotal as double precision) as decimal(2,2)), 0.00) as ratio
    from 
    (
        select count(*) as cnttotal, airport from
        (select dest as airport from flights
        union all
        select source as airport from flights
        ) as total
        group by airport
    ) as total1
    natural join
    (
        select count(*) as cntaa, airport from
        (select dest as airport from flights
        where airlineid = 'AA'
        union all
        select source as airport from flights
        where airlineid = 'AA') as aa
        group by airport
    ) as aa1
    right outer join airports
    on airports.airportid = total1.airport
) as percenttable
join airports
on airports.airportid = percenttable.airportid
order by ratio desc, airports.name
;
"""

### 7. Write a query to find the customer/customers that taken the highest number of flights but have never flown on their frequentflier airline.
###    If there is a tie, return the names of all such customers. 
### Output: Customer name
### Order: name
queries[7] = """
select ranktable.name from (
select customers.name, ff.cnt,
rank() over ( order by cnt desc) as ranknum
from (
select count(*) as cnt, flewon.customerid
from flewon join customers
on customers.customerid = flewon.customerid
where substring(flightid for 2) != customers.frequentflieron
group by flewon.customerid)
as ff
join customers
on customers.customerid = ff.customerid) 
as ranktable
where ranknum <=1
order by ranktable.name
;
"""

### 8. Write a query to find customers that took the same flight (identified by flightid) on consecutive days.
###    Return the name, flightid start date and end date of the customers flights.
###    The start date should be the first date of the pair and the end date should be the second date of the pair.
###    If a customer took the same flight on multiple pairs of consecutive days, return all the pair.
###    For instance if 'John Doe' flew on UA101 on 08/01/2024, 08/02/2024, 08/03/2024, 08/06/2024, and 08/07/2024,
###    the output should be: 
###    [(John Doe ', 'UA101 ', datetime.date(2016, 8, 1), datetime.date(2016, 8, 2)),
###     (John Doe ', 'UA101 ', datetime.date(2016, 8, 2), datetime.date(2016, 8, 3)),
###     (John Doe ', 'UA101 ', datetime.date(2016, 8, 6), datetime.date(2016, 8, 7))]
### Output: customer_name, flightid, start_date, end_date
### Order: by customer_name, flightid, start_date
queries[8] = """
with a1 as (
select flightid, customerid, flightdate, customers.name
from flewon natural join customers 
)
select a2.name, a2.flightid, a2.flightdate-1, a2.flightdate from a1 as a2
where exists 
(
    select * from a1 as a3
    where a2.flightid = a3.flightid
    and a2.customerid = a3.customerid
    and a2.flightdate = a3.flightdate + 1
)
order by a2.name, a2.flightid, a2.flightdate
;
"""

### 9. A layover consists of set of two flights where the destination of the first flight is the same 
###    as the source of the second flight. Additionally, the arrival of the first flight must be before the
###    departure of the second flight. 
###    Write a query to find all pairs of flights belonging to the same airline that had a layover in IAD
###    between 1 and 4 hours in length (inclusive).
### Output columns: 1st flight id, 2nd flight id, source city, destination city, layover duration
### Order by: layover duration
queries[9] = """
with a1 as (
    select * from flights
    where dest = 'IAD' or source = 'IAD'
)
select firstflightid, secondflightid,
source, dest,
layovertime
from (
    select a2.flightid as firstflightid,
    a3.flightid as secondflightid,
    a2.airlineid, a2.source, a3.dest,
    a2.local_arrival_time,
    a3.local_departing_time,
    (a3.local_departing_time - a2.local_arrival_time) 
    as layovertime
    from a1 as a2
    join a1 as a3
    on a2.dest = a3.source
    and a2.airlineid = a3.airlineid
) as layover
where layovertime >= '1:00:00'
and layovertime <= '4:00:00'
order by layovertime
;
"""



### 10. Provide a ranking of the airlines that are most loyal to their hub. 
###     The loyalty of an airline to its hub is defined by the ratio of the number
###     of flights that fly in or out of the hub versus the total number of flights
###     operated by airline. 
###     Output: (name, rank)
###     Order: rank, name
### Note: a) If two airlines tie, then they should both get the same rank, and the next rank should be skipped. 
### For example, if the top two airlines have the same ratio, then there should be no rank 2, e.g., 1, 1, 3 ...
queries[10] = """
select airlines.name, ranknum
from airlines join 
(
select airlineid,
rank() over ( order by ratio desc) as ranknum
from (
select matched, totalf, airlineid,
cast(matched as double precision)/cast(totalf as double precision) as ratio 
from (
select count(*) as matched, airlines.airlineid
from flights
natural join airlines
where flights.source = airlines.hub
or flights.dest = airlines.hub
group by airlines.airlineid)
as loyal
natural join
(select count(*) as totalf, flights.airlineid
from flights
group by flights.airlineid
) as total
) as loyalratio
) as ranked
on airlines.airlineid = ranked.airlineid
order by ranknum;
"""


### 11. OPTIONAL Query (0 Points): A (fun) challenge for you to try out. 
###    This query is a modification of query 8.
###    Write a query to find customers that took the same flight (identified by flightid) on consecutive days.
###    Return the name, flightid start and end date of the customers flights.
###    The start date should be the first date of the sequence and the end date should be the last date of the sequence.
###    If a customer took the same flight on multiple sequences of consecutive days, return all the sequences.
###    For instance if 'John Doe' flew on UA101 on 08/01/2024, 08/02/2024, 08/03/2024, 08/06/2024, and 08/07/2024,
###    the output should be: 
###    [(John Doe ', 'UA101 ', datetime.date(2016, 8, 1), datetime.date(2016, 8, 3)),
###     (John Doe ', 'UA101 ', datetime.date(2016, 8, 6), datetime.date(2016, 8, 7))]
### Output: customer_name, flightid, start_date, end_date
### Order: by customer_name, flightid, start_date
queries[11] = """
select 0;
"""
