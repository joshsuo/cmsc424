queries = ["" for i in range(0, 4)]

queries[0] = """
select 0;
"""

### 1.
queries[1] = ["", ""]
### <answer1>
queries[1][0] = " count(flewon_cust7.*) "
### <answer2>
queries[1][1] = " flewon_cust7 natural right outer join flights "


### 2.
queries[2] = """
SELECT name, 
    round(
        cast(cast(coalesce(
        ((SELECT count(*) FROM flights where airlineid = 'AA' and source = airportidunion.airportid) + 
        (SELECT count(*) FROM flights where airlineid = 'AA' and dest = airportidunion.airportid)), 0) 
        as float)
        /
        cast(count(*) as float) as numeric), 2) as ratio
FROM (SELECT source as airportid FROM flights union all SELECT dest as airportid FROM flights) as airportidunion
	natural join airports
GROUP BY airportidunion.airportid, name
order by ratio desc
;
"""

### 3.
### Explaination - 
'''
SELECT airlineid 
FROM flights_airports a LEFT JOIN flights_jfk j 
	ON a.flightid = j.flightid
WHERE j.flightid IS NULL
GROUP BY airlineid
HAVING count(*) >= 15;

this query doesn't work because it doesn't take out the airlines that have gone to JFK airport
it only takes out the instances the airlines went to JFK airport
so it ends up resulting in all of the airlines, not the ones that never went to JFK
'''
###
queries[3] = """
SELECT airlineid
FROM flights_airports a LEFT JOIN flights_jfk j 
	ON a.flightid = j.flightid
WHERE airlineid NOT IN
(select airlineid from flights_airports where airportid = 'JFK')
group by airlineid
having count(*) >= 15
order by airlineid
;
"""