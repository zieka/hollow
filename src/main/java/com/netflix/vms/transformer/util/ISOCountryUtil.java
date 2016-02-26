package com.netflix.vms.transformer.util;

import com.netflix.type.NFCountry;
import com.netflix.vms.transformer.hollowinput.ISOCountryHollow;
import com.netflix.vms.transformer.hollowinput.ISOCountryListHollow;
import com.netflix.vms.transformer.hollowoutput.ISOCountry;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ISOCountryUtil {

    public static Set<ISOCountry> createList(ISOCountryListHollow countries) {
        if (countries==null || countries.isEmpty()) return Collections.emptySet();

        Set<ISOCountry> result = new HashSet<>();
        Iterator<ISOCountryHollow> iter = countries.iterator();
        while(iter.hasNext()) {
            ISOCountryHollow item = iter.next();

            NFCountry country = NFCountry.findInstance(item._getValue());
            result.add(new ISOCountry(country.getId()));
        }

        return result;
    }
}
