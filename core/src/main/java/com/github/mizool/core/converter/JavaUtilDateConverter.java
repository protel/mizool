package com.github.mizool.core.converter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

@Deprecated()
public class JavaUtilDateConverter
{
    public Date fromZonedDateTime(ZonedDateTime pojo)
    {
        Date result = null;

        if (pojo != null)
        {
            result = Date.from(Instant.from(pojo));
        }
        return result;
    }

    public ZonedDateTime toZonedDateTime(Date value)
    {
        ZonedDateTime pojo = null;

        if (value != null)
        {
            pojo = ZonedDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
        }
        return pojo;
    }

    public Date fromLocalDate(LocalDate pojo)
    {
        Date result = null;

        if (pojo != null)
        {
            result = Date.from(Instant.from(pojo.atStartOfDay(ZoneOffset.UTC)));
        }
        return result;
    }

    public LocalDate toLocalDate(Date record)
    {
        LocalDate pojo = null;

        if (record != null)
        {
            pojo = LocalDate.from(record.toInstant().atZone(ZoneOffset.UTC));
        }
        return pojo;
    }
}
