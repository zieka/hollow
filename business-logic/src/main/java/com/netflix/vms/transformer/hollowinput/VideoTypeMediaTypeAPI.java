package com.netflix.vms.transformer.hollowinput;

import com.netflix.hollow.api.custom.HollowObjectTypeAPI;
import com.netflix.hollow.core.read.dataaccess.HollowObjectTypeDataAccess;

@SuppressWarnings("all")
public class VideoTypeMediaTypeAPI extends HollowObjectTypeAPI {

    private final VideoTypeMediaDelegateLookupImpl delegateLookupImpl;

    VideoTypeMediaTypeAPI(VMSHollowInputAPI api, HollowObjectTypeDataAccess typeDataAccess) {
        super(api, typeDataAccess, new String[] {
            "value"
        });
        this.delegateLookupImpl = new VideoTypeMediaDelegateLookupImpl(this);
    }

    public int getValueOrdinal(int ordinal) {
        if(fieldIndex[0] == -1)
            return missingDataHandler().handleReferencedOrdinal("VideoTypeMedia", ordinal, "value");
        return getTypeDataAccess().readOrdinal(ordinal, fieldIndex[0]);
    }

    public StringTypeAPI getValueTypeAPI() {
        return getAPI().getStringTypeAPI();
    }

    public VideoTypeMediaDelegateLookupImpl getDelegateLookupImpl() {
        return delegateLookupImpl;
    }

    @Override
    public VMSHollowInputAPI getAPI() {
        return (VMSHollowInputAPI) api;
    }

}