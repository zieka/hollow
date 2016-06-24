package com.netflix.vms.transformer.hollowinput;

import com.netflix.hollow.objects.HollowObject;
import com.netflix.hollow.HollowObjectSchema;

@SuppressWarnings("all")
public class RolloutHollow extends HollowObject {

    public RolloutHollow(RolloutDelegate delegate, int ordinal) {
        super(delegate, ordinal);
    }

    public long _getRolloutId() {
        return delegate().getRolloutId(ordinal);
    }

    public Long _getRolloutIdBoxed() {
        return delegate().getRolloutIdBoxed(ordinal);
    }

    public long _getMovieId() {
        return delegate().getMovieId(ordinal);
    }

    public Long _getMovieIdBoxed() {
        return delegate().getMovieIdBoxed(ordinal);
    }

    public StringHollow _getRolloutName() {
        int refOrdinal = delegate().getRolloutNameOrdinal(ordinal);
        if(refOrdinal == -1)
            return null;
        return  api().getStringHollow(refOrdinal);
    }

    public StringHollow _getRolloutType() {
        int refOrdinal = delegate().getRolloutTypeOrdinal(ordinal);
        if(refOrdinal == -1)
            return null;
        return  api().getStringHollow(refOrdinal);
    }

    public RolloutPhaseListHollow _getPhases() {
        int refOrdinal = delegate().getPhasesOrdinal(ordinal);
        if(refOrdinal == -1)
            return null;
        return  api().getRolloutPhaseListHollow(refOrdinal);
    }

    public VMSHollowInputAPI api() {
        return typeApi().getAPI();
    }

    public RolloutTypeAPI typeApi() {
        return delegate().getTypeAPI();
    }

    protected RolloutDelegate delegate() {
        return (RolloutDelegate)delegate;
    }

}