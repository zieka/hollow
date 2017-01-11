package com.netflix.vms.transformer.hollowinput;

import com.netflix.hollow.api.objects.provider.HollowFactory;
import com.netflix.hollow.core.read.dataaccess.HollowTypeDataAccess;
import com.netflix.hollow.api.custom.HollowTypeAPI;
import com.netflix.hollow.api.objects.delegate.HollowListCachedDelegate;

@SuppressWarnings("all")
public class VideoRatingRatingReasonArrayOfIdsHollowFactory<T extends VideoRatingRatingReasonArrayOfIdsHollow> extends HollowFactory<T> {

    @Override
    public T newHollowObject(HollowTypeDataAccess dataAccess, HollowTypeAPI typeAPI, int ordinal) {
        return (T)new VideoRatingRatingReasonArrayOfIdsHollow(((VideoRatingRatingReasonArrayOfIdsTypeAPI)typeAPI).getDelegateLookupImpl(), ordinal);
    }

    @Override
    public T newCachedHollowObject(HollowTypeDataAccess dataAccess, HollowTypeAPI typeAPI, int ordinal) {
        return (T)new VideoRatingRatingReasonArrayOfIdsHollow(new HollowListCachedDelegate((VideoRatingRatingReasonArrayOfIdsTypeAPI)typeAPI, ordinal), ordinal);
    }

}