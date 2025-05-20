-- part 1
create or replace function update_new_ff_func()
returns trigger as $update_newcustomer_ffairlines$
declare
    points integer;
    ff character(2);
begin
    points := (select coalesce ((select
                round(sum(extract(epoch from (local_arrival_time - local_departing_time)) / 60))
                from flights natural join flewon
                where customerid = new.customerid
                and airlineid = new.frequentflieron), 0));
    
    ff := (select airlineid
            from flights
            natural join flewon
            where customerid = new.customerid
            and starts_with(flightid, airlineid)
            order by flightdate desc, airlineid
            limit 1);

    if (TG_OP = 'INSERT') then
        insert into newcustomers (customerid, name, birthdate)
        values (new.customerid, new.name, new.birthdate);

        if (new.frequentflieron is not null) then
            insert into ffairlines (customerid, airlineid, points)
            values (new.customerid, new.frequentflieron, points);
        end if;
        
    elsif (TG_OP = 'UPDATE') then
        update newcustomers
        set name = new.name, birthdate = new.birthdate
        where customerid = new.customerid;

        if (new.frequentflieron is null) then
            delete from ffairlines
            where customerid = new.customerid;
        end if;

        if (old.frequentflieron is distinct from new.frequentflieron) then
            if (new.frequentflieron is not null) then
                if (ff is not null and ff = old.frequentflieron) then
                    update customers
                    set frequentflieron = ff
                    where customerid = new.customerid;
                end if;

                insert into ffairlines (customerid, airlineid, points)
                values (new.customerid, new.frequentflieron, points);
            end if;
        end if;

    elsif (TG_OP = 'DELETE') then
        delete from newcustomers
        where customerid = old.customerid;

        delete from ffairlines
        where customerid = old.customerid;

    end if;
    
    return null;
end;
$update_newcustomer_ffairlines$ language plpgsql;

create or replace trigger update_new_ff
after INSERT or UPDATE or DELETE on customers
for each row
when (pg_trigger_depth() < 1)
execute procedure update_new_ff_func();



-- part 2

create or replace function update_cust_func()
returns trigger as $update_customers$
declare
    ff character(2);
begin
    ff := (select airlineid
            from flights natural join flewon
            where customerid = new.customerid
            order by flightdate desc, airlineid
            limit 1);

    if (TG_OP = 'INSERT') then
        insert into customers (customerid, name, birthdate, frequentflieron)
        values (new.customerid, new.name, new.birthdate, ff);

    elsif (TG_OP = 'UPDATE') then
        update customers
        set name = new.name, birthdate = new.birthdate
        where customerid = new.customerid;

    elsif (TG_OP = 'DELETE') then
        delete from customers
        where customerid = old.customerid;

    end if;

    return null;
end;
$update_customers$ language plpgsql;

create or replace trigger update_cust
after INSERT or UPDATE or DELETE on newcustomers
for each row
when (pg_trigger_depth() < 1)
execute procedure update_cust_func();


-- part 3

create or replace function update_ff_func()
returns trigger as $update_ffairlines$
declare
    ff character(2);
begin
    ff := (select airlineid
            from ffairlines
            natural join flewon
            where customerid = new.customerid
            and starts_with(flightid, airlineid)
            order by flightdate desc
            limit 1);

    if (TG_OP = 'INSERT' or TG_OP = 'UPDATE') then
        update customers
        set frequentflieron = ff
        where customerid = new.customerid;

    elsif (TG_OP = 'DELETE') then
        ff := (select airlineid
            from ffairlines
            natural join flewon
            where customerid = old.customerid
            and starts_with(flightid, airlineid)
            order by flightdate desc
            limit 1);

        update customers
        set frequentflieron = ff
        where customerid = old.customerid;

    end if;

    return null;
end;
$update_ffairlines$ language plpgsql;

create or replace trigger update_ff
after INSERT or UPDATE or DELETE on ffairlines
for each row
when (pg_trigger_depth() < 1)
execute procedure update_ff_func();


-- part 4

create or replace function update_ffo_func()
returns trigger as $update_flieron$
declare
    ff character(2);
    new_points integer;
    old_points integer;
begin
    ff := (select airlineid
            from flights
            natural join flewon
            natural join ffairlines
            where customerid = new.customerid
            and starts_with(flightid, airlineid)
            order by flightdate desc, airlineid
            limit 1);

    new_points := (select coalesce ((select
            round(sum(extract(epoch from (local_arrival_time - local_departing_time)) / 60))
            from flights natural join flewon
            where customerid = new.customerid
            and airlineid = substring(new.flightid for 2)), 0));

    
    if(TG_OP = 'INSERT') then

        update customers
        set frequentflieron = ff
        where customerid = new.customerid;

        update ffairlines
        set points = new_points
        where customerid = new.customerid
        and airlineid = substring(new.flightid for 2);

    elsif (TG_OP = 'UPDATE') then
        old_points := (select coalesce ((select
            round(sum(extract(epoch from (local_arrival_time - local_departing_time)) / 60))
            from flights natural join flewon
            where customerid = old.customerid
            and airlineid = substring(old.flightid for 2)), 0));

        update customers
        set frequentflieron = ff
        where customerid = new.customerid;

        update ffairlines
        set points = new_points
        where customerid = new.customerid
        and airlineid = substring(new.flightid for 2);

        update ffairlines
        set points = old_points
        where customerid = old.customerid
        and airlineid = substring(old.flightid for 2);

    elsif (TG_OP = 'DELETE') then
        new_points := (select coalesce ((select
            round(sum(extract(epoch from (local_arrival_time - local_departing_time)) / 60))
            from flights natural join flewon
            where customerid = old.customerid
            and airlineid = substring(old.flightid for 2)), 0));

        update ffairlines
        set points = new_points
        where customerid = old.customerid
        and airlineid = substring(old.flightid for 2);

        -- if delete all flewon entries for cust, then set ffairlines to 0        
        if ((select count(*) from flewon where customerid = old.customerid) = 0) then
            update ffairlines
            set points = 0
            where customerid = old.customerid;
        end if;
        
    end if;

    return null;
end;
$update_flieron$ language plpgsql;

create or replace trigger update_ffo
after INSERT or UPDATE or DELETE on flewon
for each row
when (pg_trigger_depth() < 1)
execute procedure update_ffo_func();