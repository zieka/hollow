package com.netflix.vms.transformer.modules.artwork;

import static com.netflix.vms.transformer.common.io.TransformerLogTag.ReexploreTags;
import static com.netflix.vms.transformer.index.IndexSpec.ARTWORK_DERIVATIVE_SETS;
import static com.netflix.vms.transformer.index.IndexSpec.ARTWORK_IMAGE_FORMAT;
import static com.netflix.vms.transformer.index.IndexSpec.ARTWORK_RECIPE;
import static com.netflix.vms.transformer.index.IndexSpec.ARTWORK_TERRITORY_COUNTRIES;

import com.netflix.hollow.core.index.HollowHashIndex;
import com.netflix.hollow.core.index.HollowHashIndexResult;
import com.netflix.hollow.core.index.HollowPrimaryKeyIndex;
import com.netflix.hollow.core.read.iterator.HollowOrdinalIterator;
import com.netflix.hollow.core.write.objectmapper.HollowObjectMapper;
import com.netflix.hollow.core.write.objectmapper.NullablePrimitiveBoolean;
import com.netflix.vms.transformer.ConversionUtils;
import com.netflix.vms.transformer.CycleConstants;
import com.netflix.vms.transformer.common.TransformerContext;
import com.netflix.vms.transformer.common.io.TransformerLogTag;
import com.netflix.vms.transformer.hollowinput.ArtWorkImageTypeHollow;
import com.netflix.vms.transformer.hollowinput.ArtworkAttributesHollow;
import com.netflix.vms.transformer.hollowinput.ArtworkLocaleHollow;
import com.netflix.vms.transformer.hollowinput.ArtworkLocaleListHollow;
import com.netflix.vms.transformer.hollowinput.ArtworkRecipeHollow;
import com.netflix.vms.transformer.hollowinput.DerivativeTagHollow;
import com.netflix.vms.transformer.hollowinput.IPLArtworkDerivativeHollow;
import com.netflix.vms.transformer.hollowinput.IPLDerivativeGroupHollow;
import com.netflix.vms.transformer.hollowinput.IPLDerivativeSetHollow;
import com.netflix.vms.transformer.hollowinput.ListOfDerivativeTagHollow;
import com.netflix.vms.transformer.hollowinput.ListOfStringHollow;
import com.netflix.vms.transformer.hollowinput.MapKeyHollow;
import com.netflix.vms.transformer.hollowinput.MultiValuePassthroughMapHollow;
import com.netflix.vms.transformer.hollowinput.PassthroughDataHollow;
import com.netflix.vms.transformer.hollowinput.SingleValuePassthroughMapHollow;
import com.netflix.vms.transformer.hollowinput.StringHollow;
import com.netflix.vms.transformer.hollowinput.VMSHollowInputAPI;
import com.netflix.vms.transformer.hollowoutput.AcquisitionSource;
import com.netflix.vms.transformer.hollowoutput.ArtWorkImageFormatEntry;
import com.netflix.vms.transformer.hollowoutput.ArtWorkImageRecipe;
import com.netflix.vms.transformer.hollowoutput.ArtWorkImageTypeEntry;
import com.netflix.vms.transformer.hollowoutput.Artwork;
import com.netflix.vms.transformer.hollowoutput.ArtworkBasicPassthrough;
import com.netflix.vms.transformer.hollowoutput.ArtworkDerivative;
import com.netflix.vms.transformer.hollowoutput.ArtworkDerivatives;
import com.netflix.vms.transformer.hollowoutput.ArtworkReExploreLongTimestamp;
import com.netflix.vms.transformer.hollowoutput.ArtworkScreensaverPassthrough;
import com.netflix.vms.transformer.hollowoutput.ArtworkSourcePassthrough;
import com.netflix.vms.transformer.hollowoutput.ArtworkSourceString;
import com.netflix.vms.transformer.hollowoutput.Integer;
import com.netflix.vms.transformer.hollowoutput.PassthroughString;
import com.netflix.vms.transformer.hollowoutput.PassthroughVideo;
import com.netflix.vms.transformer.hollowoutput.Strings;
import com.netflix.vms.transformer.hollowoutput.__passthrough_string;
import com.netflix.vms.transformer.index.VMSTransformerIndexer;
import com.netflix.vms.transformer.modules.AbstractTransformModule;
import com.netflix.vms.transformer.util.NFLocaleUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public abstract class ArtWorkModule extends AbstractTransformModule{
    protected final String entityType;
    protected final HollowPrimaryKeyIndex imageTypeIdx;
    protected final HollowPrimaryKeyIndex recipeIdx;
    protected final HollowPrimaryKeyIndex territoryIdx;
    
    protected final HollowHashIndex artworkDerivativeSetIdx;
    
    private final ArtWorkComparator artworkComparator;

    private final Map<String, ArtWorkImageTypeEntry> imageTypeEntryCache;
    private final Map<String, ArtWorkImageFormatEntry> imageFormatEntryCache;
    private final Map<String, ArtWorkImageRecipe> imageRecipeCache;
    
    private final Set<String> unknownArtworkImageTypes = new HashSet<String>();
    
    private final Set<String> variableSizeImageTypes = new HashSet<String>();
    private final List<String> newEpisodeOverlayTypes = new ArrayList<String>();
    
    protected boolean allImagesAreVariableSize = false;

    public ArtWorkModule(String entityType, VMSHollowInputAPI api, TransformerContext ctx, HollowObjectMapper mapper, CycleConstants cycleConstants, VMSTransformerIndexer indexer) {
        super(api, ctx, cycleConstants, mapper);
        this.entityType = entityType;
        this.imageTypeIdx = indexer.getPrimaryKeyIndex(ARTWORK_IMAGE_FORMAT);
        this.recipeIdx = indexer.getPrimaryKeyIndex(ARTWORK_RECIPE);
        this.territoryIdx = indexer.getPrimaryKeyIndex(ARTWORK_TERRITORY_COUNTRIES);
        this.artworkDerivativeSetIdx = indexer.getHashIndex(ARTWORK_DERIVATIVE_SETS);
        this.artworkComparator = new ArtWorkComparator(ctx);
        this.imageFormatEntryCache = new HashMap<String, ArtWorkImageFormatEntry>();
        this.imageTypeEntryCache = new HashMap<String, ArtWorkImageTypeEntry>();
        this.imageRecipeCache = new HashMap<String, ArtWorkImageRecipe>();
        
        for(String type : ctx.getConfig().getVariableImageTypes().split(","))
            variableSizeImageTypes.add(type);
        for(String overlayType : ctx.getConfig().getNewEpisodeOverlayTypes().split(","))
            newEpisodeOverlayTypes.add(overlayType);
    }

    protected void transformArtworks(int entityId, String sourceFileId, int ordinalPriority, int seqNum, ArtworkAttributesHollow attributes, HollowHashIndexResult inputDerivativesMatches, Set<ArtworkLocaleHollow> localeSet, Set<Artwork> artworkSet) {
        unknownArtworkImageTypes.clear();

        Artwork artwork = new Artwork();
        
        // Process list of derivatives
        processCombinedDerivativesAndCdnList(entityId, sourceFileId, inputDerivativesMatches, artwork);

        artwork.sourceFileId = new ArtworkSourceString(sourceFileId);
        artwork.seqNum = seqNum;
        artwork.ordinalPriority = ordinalPriority;
        fillPassThroughData(artwork, attributes);

        for (final ArtworkLocaleHollow localeHollow : localeSet) {
            Artwork localeArtwork = artwork.clone();
            localeArtwork.locale = NFLocaleUtil.createNFLocale(localeHollow._getBcp47Code()._getValue());
            localeArtwork.effectiveDate = localeHollow._getEffectiveDate()._getValue();
            artworkSet.add(localeArtwork);
        }
    }

    protected void processCombinedDerivativesAndCdnList(int entityId, String sourceFileId, HollowHashIndexResult inputDerivativesMatches, Artwork artwork) {
        HollowOrdinalIterator iterator = inputDerivativesMatches.iterator();

        Map<String, IPLDerivativeGroupHollow> groupByType = new HashMap<>();
        
        int ordinal = iterator.next();
        while(ordinal != HollowOrdinalIterator.NO_MORE_ORDINALS) {
            IPLDerivativeGroupHollow inputDerivatives = api.getIPLDerivativeGroupHollow(ordinal);
            String type = inputDerivatives._getImageType()._getValue();
            
            IPLDerivativeGroupHollow iplDerivativeGroupHollow = groupByType.get(type);
            if(iplDerivativeGroupHollow == null || iplDerivativeGroupHollow._getSubmission() < inputDerivatives._getSubmission()) {
                groupByType.put(type, inputDerivatives);
            }
            
            ordinal = iterator.next();
        }
        
        for(Map.Entry<String, IPLDerivativeGroupHollow> entry : groupByType.entrySet()) {
            processDerivativesAndCdnList(entityId, sourceFileId, entry.getValue(), artwork);
        }
    }
    
    // Process Derivatives
    protected void processDerivativesAndCdnList(int entityId, String sourceFileId, IPLDerivativeGroupHollow inputDerivatives, Artwork artwork) {
        IPLDerivativeSetHollow derivativeSet = inputDerivatives._getDerivatives();

        String imageType = inputDerivatives._getImageType()._getValue();
        ArtWorkImageTypeEntry typeEntry = getImageTypeEntry(imageType);
        
        int inputDerivativeSetOrdinal = derivativeSet.getOrdinal();
                
        ArtworkDerivatives outputDerivatives = cycleConstants.artworkDerivativesCache.getResult(inputDerivativeSetOrdinal);
        
        if(outputDerivatives == null) {
            buildAndCacheArtworkDerivatives(derivativeSet, imageType, typeEntry, inputDerivativeSetOrdinal);
            outputDerivatives = cycleConstants.artworkDerivativesCache.getResult(inputDerivativeSetOrdinal);
        }

        /// combine multiple derivative lists where necessary
        if(artwork.derivatives != null) {
            ArtworkDerivativesListMerger merger = new ArtworkDerivativesListMerger(artwork.derivatives.list, outputDerivatives.list);
            
            List<ArtworkDerivative> derivativeList = new ArrayList<>(merger.mergedSize());
            
            while(merger.next()) {
                derivativeList.add(merger.getNextArtworkDerivative());
            }
            
            outputDerivatives = artworkDerivatives(derivativeList);
        }
        
        artwork.derivatives = outputDerivatives;
    }

    private void buildAndCacheArtworkDerivatives(IPLDerivativeSetHollow derivativeSet, String imageType, ArtWorkImageTypeEntry typeEntry,int inputDerivativeSetOrdinal) {
        List<ArtworkDerivative> derivativeList = new ArrayList<>();
                
        for (IPLArtworkDerivativeHollow derivativeHollow : derivativeSet) {
            int inputDerivativeOrdinal = derivativeHollow.getOrdinal();
            
            ArtworkDerivative outputDerivative = cycleConstants.artworkDerivativeCache.getResult(inputDerivativeOrdinal);
            
            if(outputDerivative == null) {
                outputDerivative = new ArtworkDerivative();
                        
                ArtWorkImageTypeEntry derivativeTypeEntry = typeEntry;
                ArtWorkImageFormatEntry formatEntry = getImageFormatEntry(imageType, derivativeHollow);
                ArtWorkImageRecipe recipeEntry = getImageRecipe(derivativeHollow);
                
                derivativeTypeEntry = getModifiedImageTypeEntry(typeEntry, imageType, derivativeHollow);
                
                String recipeDescriptor = derivativeHollow._getRecipeDescriptor()._getValue();
                
                outputDerivative.format = formatEntry;
                outputDerivative.type = derivativeTypeEntry;
                outputDerivative.recipe = recipeEntry;
                outputDerivative.recipeDesc = new Strings(recipeDescriptor);
                outputDerivative.cdnId = java.lang.Integer.parseInt(derivativeHollow._getCdnId()._getValue()); // @TODO: Is it Integer or String
                
                outputDerivative = cycleConstants.artworkDerivativeCache.setResult(inputDerivativeOrdinal, outputDerivative);
            } 
            
            derivativeList.add(outputDerivative);
            
        }

        Collections.sort(derivativeList, ArtworkDerivativesListMerger.DERIVATIVE_COMPARATOR);  /// cannot sort derivatives but not CDNs

        ArtworkDerivatives cacheDerivatives = artworkDerivatives(derivativeList);
        
        cacheDerivatives = cycleConstants.artworkDerivativesCache.setResult(inputDerivativeSetOrdinal, cacheDerivatives);
    }

    private boolean determineIfNewEpisodeOverlayType(ListOfDerivativeTagHollow overlayTypes) {
        if(overlayTypes == null)
            return false;
        
        Boolean isNewEpisodeOverlayType = cycleConstants.isNewEpisodeOverlayTypes.getResult(overlayTypes.getOrdinal());
        if(isNewEpisodeOverlayType == null) {
            isNewEpisodeOverlayType = Boolean.FALSE;
            tagloop:
            for(DerivativeTagHollow tag : overlayTypes) {
                for(String newEpisodeOverlayType : newEpisodeOverlayTypes) {
                    if(tag._isValueEqual(newEpisodeOverlayType)) {
                        isNewEpisodeOverlayType = Boolean.TRUE;
                        break tagloop;
                    }
                }
            }

            cycleConstants.isNewEpisodeOverlayTypes.setResult(overlayTypes.getOrdinal(), isNewEpisodeOverlayType);
        }
        
        return isNewEpisodeOverlayType.booleanValue();
    }

    private ArtworkDerivatives artworkDerivatives(List<ArtworkDerivative> derivatives) {
        ArtworkDerivatives result = new ArtworkDerivatives();

        result.list = derivatives;
        result.formatToDerivativeIndex = new HashMap<>();
        result.typeFormatIndex = new HashMap<>();

        for (int i = 0; i < derivatives.size(); i++) {
            ArtworkDerivative derivative = derivatives.get(i);
            Integer index = new Integer(i);

            { // Map ImageType -> Map<Format, List<index>
                Map<ArtWorkImageFormatEntry, List<Integer>> formatMap = result.typeFormatIndex.get(derivative.type);
                if (formatMap == null) {
                    formatMap = new HashMap<>();
                    result.typeFormatIndex.put(derivative.type, formatMap);
                }

                List<Integer> idxList = formatMap.get(derivative.format);
                if (idxList == null) {
                    idxList = new ArrayList<Integer>();
                    formatMap.put(derivative.format, idxList);
                }
                idxList.add(index);
            }

            { // Legacy : just to be backwards compatible for older client < 59.50
                List<Integer> idxList = result.formatToDerivativeIndex.get(derivative.format);
                if (idxList == null) {
                    idxList = new ArrayList<Integer>();
                    result.formatToDerivativeIndex.put(derivative.format, idxList);
                }
                idxList.add(index);
            }

        }

        return result;
    }

    public Map<Strings, List<Artwork>> createArtworkByTypeMap(Collection<Artwork> allArtwork) {
        Map<Strings, List<Artwork>> artworks = new HashMap<>();

        Set<Strings> imageTypes = new HashSet<>();
        for (Artwork artwork : allArtwork) {
            imageTypes.clear();

            for (Map.Entry<ArtWorkImageTypeEntry, ?> entry : artwork.derivatives.typeFormatIndex.entrySet()) {
                Strings imageType = new Strings(entry.getKey().nameStr);
                List<Artwork> list = artworks.get(imageType);
                if (list == null) {
                    list = new ArrayList<Artwork>();
                    artworks.put(imageType, list);
                }
                list.add(artwork);
            }
        }

        for (Map.Entry<Strings, List<Artwork>> entry : artworks.entrySet()) {
            Collections.sort(entry.getValue(), artworkComparator);
        }

        return artworks;
    }

    public Map<ArtWorkImageTypeEntry, Set<ArtWorkImageFormatEntry>> createFormatByTypeMap(Collection<Artwork> allArtwork) {
        Map<ArtWorkImageTypeEntry, Set<ArtWorkImageFormatEntry>> map = new HashMap<>();

        for (Artwork artwork : allArtwork) {
            for (Map.Entry<ArtWorkImageTypeEntry, Map<ArtWorkImageFormatEntry, List<Integer>>> entry : artwork.derivatives.typeFormatIndex.entrySet()) {
                ArtWorkImageTypeEntry imageType = entry.getKey();

                Set<ArtWorkImageFormatEntry> set = map.get(imageType);
                if (set == null) {
                    set = new HashSet<ArtWorkImageFormatEntry>();
                    map.put(imageType, set);
                }
                set.addAll(entry.getValue().keySet());
            }
        }
        return map;
    }

    protected void fillPassThroughData(Artwork artwork, ArtworkAttributesHollow attributes) {
        Map<String, String> keyValues = getSingleKeyValuesMap(attributes);

        HashMap<String, List<__passthrough_string>> keyListValues = new HashMap<>();
        MultiValuePassthroughMapHollow multiValuePassthrough = attributes._getPassthrough()._getMultiValues();
        for(Entry<MapKeyHollow, ListOfStringHollow> entry : multiValuePassthrough.entrySet()) {
            String key = entry.getKey()._getValue();
            List<__passthrough_string> values = new ArrayList<>();
            ListOfStringHollow listValue = entry.getValue();
            Iterator<StringHollow> iterator = listValue.iterator();
            while(iterator.hasNext()) {
                StringHollow next = iterator.next();
                values.add(new __passthrough_string(next._getValue()));
            }
            keyListValues.put(key, values);
        }

        PassthroughString passThroughString = getPassThroughString("APPROVAL_SOURCE", keyValues);
        if(passThroughString != null) {
            getBasicPassthrough(artwork).approval_source = passThroughString;
        }
        passThroughString = getPassThroughString("designAttribute", keyValues);
        if(passThroughString != null) {
            getBasicPassthrough(artwork).design_attribute = passThroughString;
        }
        
        passThroughString = getPassThroughString("FOCAL_POINT", keyValues);
        if(passThroughString != null) {
            getBasicPassthrough(artwork).focal_point = passThroughString;
        }            // Sort descriptor necessary for client artwork resolver

        passThroughString = getPassThroughString("TONE", keyValues);
        if(passThroughString != null) {
            getBasicPassthrough(artwork).tone = passThroughString;
        }
        passThroughString = getPassThroughString("GROUP_ID", keyValues);
        if(passThroughString != null) {
            getBasicPassthrough(artwork).group_id = passThroughString;
        }
        if (keyListValues.containsKey("AWARD_CAMPAIGNS")) {
            getBasicPassthrough(artwork).awardCampaigns = keyListValues.get("AWARD_CAMPAIGNS");
        }
        if (keyListValues.containsKey("themes")) {
            getBasicPassthrough(artwork).themes = keyListValues.get("themes");
        }
        if (keyListValues.containsKey("IDENTIFIERS")) {
            getBasicPassthrough(artwork).identifiers = keyListValues.get("IDENTIFIERS");
        }
        if (keyListValues.containsKey("PERSON_IDS")) {
            getBasicPassthrough(artwork).personIdStrs = keyListValues.get("PERSON_IDS");
        }
        applyLocaleOverridableAttributes(artwork, keyValues);
        
        String startX = keyValues.get("SCREENSAVER_START_X");
        String endX = keyValues.get("SCREENSAVER_END_X");
        String offsetY = keyValues.get("SCREENSAVER_OFFSET_Y");
        if(startX != null || endX != null || offsetY != null) {
            ArtworkScreensaverPassthrough screensaverPassthrough = new ArtworkScreensaverPassthrough();
            try {
                if(startX != null)  screensaverPassthrough.startX = java.lang.Integer.parseInt(startX);
                if(endX != null)    screensaverPassthrough.endX = java.lang.Integer.parseInt(endX);
                if(offsetY != null) screensaverPassthrough.offsetY = java.lang.Integer.parseInt(offsetY);
                getBasicPassthrough(artwork).screensaverPassthrough = screensaverPassthrough;
            } catch(NumberFormatException unexpected) { 
                ctx.getLogger().error(TransformerLogTag.UnexpectedError, "Failed to parse artwork SCREENSAVER attributes", unexpected);
            }
        }

        ArtworkSourcePassthrough sourcePassThrough = new ArtworkSourcePassthrough();
        sourcePassThrough.source_file_id = getArtworkSourceString("source_file_id", keyValues);
        sourcePassThrough.original_source_file_id = getArtworkSourceString("original_source_file_id", keyValues);
        if (sourcePassThrough.original_source_file_id == null) sourcePassThrough.original_source_file_id = sourcePassThrough.source_file_id;

        artwork.source = sourcePassThrough;
        artwork.source_movie_id = getPassThroughVideo("SOURCE_MOVIE_ID", keyValues);
        artwork.acquisitionSource = getAcquisitionSource("ACQUISITION_SOURCE", keyValues);
    }

    protected Map<String, String> getSingleKeyValuesMap(ArtworkAttributesHollow attributes) {
        PassthroughDataHollow passthrough = attributes._getPassthrough();
        if(passthrough == null)
            return Collections.emptyMap();
        SingleValuePassthroughMapHollow singleValuePassThrough = passthrough._getSingleValues();
        HashMap<String, String> keyValues = new HashMap<>();
        for(Entry<MapKeyHollow, StringHollow> entry : singleValuePassThrough.entrySet()) {
            keyValues.put(entry.getKey()._getValue(), entry.getValue()._getValue());
        }
        return keyValues;
    }

    protected void applyLocaleOverridableAttributes(Artwork artwork, Map<String, String> keyValues) {
        String approvalState = keyValues.get("APPROVAL_STATE");
        if(approvalState != null) {
            // NOTE: Need to manually make approval_state to NullablePrimitiveBoolean (public NullablePrimitiveBoolean approval_state = null)
            getBasicPassthrough(artwork).approval_state = java.lang.Boolean.valueOf(approvalState) ? NullablePrimitiveBoolean.TRUE : NullablePrimitiveBoolean.FALSE;
        }
        if (keyValues.containsKey("REEXPLORE_TIME") && keyValues.get("REEXPLORE_TIME") != null) {
            long timestamp = Long.valueOf(keyValues.get("REEXPLORE_TIME"));
            // Warn on timestamps older than 36 days, since explore (ab testing) of images only spans 35 days at most.
            // upstream team should clean up older timestamps when generating VideoArtwork.json feed.
            // upstream team has a check that may have earliest timestamp which is 36 days old, so we can check for 38 days to compensate for delay in receiving this feed.
            long timestamp38DaysBack = Instant.now().getEpochSecond() - (3600 * 24 * 38);
            if (timestamp < timestamp38DaysBack) {
                // this log statement is only for warning purposes. Ideally this should not warn. If it does with high number, then revisit the window span or contact upstream team.
                ctx.getLogger().warn(ReexploreTags, "Found re-explore timestamp={} that is older than 38 days timestamp={}, This is stale (dead) data", timestamp, timestamp38DaysBack);
            }
            getBasicPassthrough(artwork).reExploreLongTimestamp = new ArtworkReExploreLongTimestamp(timestamp);
        }
        
        if(keyValues.containsKey("file_seq") && keyValues.get("file_seq") != null)
            artwork.file_seq = java.lang.Integer.valueOf(keyValues.get("file_seq"));
    }
    
    private ArtworkBasicPassthrough getBasicPassthrough(Artwork artwork) {
        if(artwork.basic_passthrough == null)
            artwork.basic_passthrough = new ArtworkBasicPassthrough();
        return artwork.basic_passthrough;
    }

    private PassthroughVideo getPassThroughVideo(String key, Map<String, String> keyValues) {
        PassthroughString passThroughString = getPassThroughString(key, keyValues);
        if (passThroughString == null) return null;

        String videoStr = new String(passThroughString.value);
        return new PassthroughVideo(java.lang.Integer.parseInt(videoStr));
    }

    private PassthroughString getPassThroughString(String key, Map<String, String> keyValues) {
        String value = keyValues.get(key);
        if(value != null) {
            return new PassthroughString(value);
        }
        return null;
    }

    private AcquisitionSource getAcquisitionSource(String key, Map<String, String> keyValues) {
        String value = keyValues.get(key);
        if (value != null) {
            return new AcquisitionSource(value);
        }
        return null;
    }

    private ArtworkSourceString getArtworkSourceString(String key, Map<String, String> keyValues) {
        String value = keyValues.get(key);
        if(value != null) {
            return new ArtworkSourceString(value);
        }
        return null;
    }

    protected final ArtWorkImageTypeEntry getImageTypeEntry(String typeName) {
        ArtWorkImageTypeEntry entry = imageTypeEntryCache.get(typeName);

        if(entry == null) {
            int ordinal = imageTypeIdx.getMatchingOrdinal(typeName);
            entry = new ArtWorkImageTypeEntry();
            if(ordinal != -1) {
                ArtWorkImageTypeHollow artWorkImageTypeHollow = api.getArtWorkImageTypeHollow(ordinal);
                entry.recipeNameStr = artWorkImageTypeHollow._getRecipe()._getValue().toCharArray();
                entry.allowMultiples = true;
                entry.unavailableFileNameStr = "unavailable".toCharArray();
                entry.nameStr = typeName.toCharArray();
            }else {
                // RETURN NULL to be backwards compatible
                return null;
                //                entry.recipeNameStr = "jpg".toCharArray();
                //                entry.allowMultiples = true;
                //                entry.unavailableFileNameStr = "unavailable".toCharArray();
                //                entry.nameStr = typeName.toCharArray();
            }

            imageTypeEntryCache.put(typeName, entry);
        }

        return entry;
    }
    
    protected final ArtWorkImageTypeEntry getModifiedImageTypeEntry(ArtWorkImageTypeEntry unmodifiedEntry, String typeName, IPLArtworkDerivativeHollow derivativeInput) {
        String originalTypeName = typeName;
        
        ListOfDerivativeTagHollow modifications = derivativeInput._getModifications();
        ListOfDerivativeTagHollow overlayTypes = derivativeInput._getOverlayTypes();
        
        if(modifications != null && !modifications.isEmpty()) {
            ModificationKey key = new ModificationKey(typeName, modifications.get(0)._getValue());
            String modifiedTypeName  = modifiedImageTypeMap.get(key);
            if(modifiedTypeName != null)
                typeName = modifiedTypeName;
        }
        
        if(determineIfNewEpisodeOverlayType(overlayTypes)) {
            if(isAllLowercase(typeName))
                typeName += "_new_" + derivativeInput._getLanguageCode()._getValue().toLowerCase();
            else
                typeName += "_NEW_" + derivativeInput._getLanguageCode()._getValue().toUpperCase();
        }
        
        if(!typeName.equals(originalTypeName)) {
            ArtWorkImageTypeEntry entry = unmodifiedEntry.clone();
            entry.nameStr = typeName.toCharArray();
            
            return entry;
        }
        
        return unmodifiedEntry;
    }
    
    private boolean isAllLowercase(String str) {
        for(int i=0;i<str.length();i++) {
            if(str.charAt(i) >= 'A' && str.charAt(i) <= 'Z')
                return false;
        }
        return true;
    }

    protected final ArtWorkImageFormatEntry getImageFormatEntry(String imageType, IPLArtworkDerivativeHollow derivative) {
        boolean isVariableSizeImage = allImagesAreVariableSize || variableSizeImageTypes.contains(imageType);
        
        int width = isVariableSizeImage ? derivative._getWidthInPixels() : derivative._getTargetWidthInPixels();
        int height = isVariableSizeImage ? derivative._getHeightInPixels() : derivative._getTargetHeightInPixels();
        String formatName = width + "x" + height;

        ArtWorkImageFormatEntry entry = imageFormatEntryCache.get(formatName);

        if(entry == null) {
            entry = new ArtWorkImageFormatEntry();
            entry.nameStr = formatName.toCharArray();
            entry.height = height;
            entry.width = width;

            imageFormatEntryCache.put(formatName, entry);
        }

        return entry;
    }

    protected final ArtWorkImageRecipe getImageRecipe(IPLArtworkDerivativeHollow derivative) {
        String recipeName = derivative._getRecipeName()._getValue();

        ArtWorkImageRecipe entry = imageRecipeCache.get(recipeName);

        if(entry == null) {
            int ordinal = recipeIdx.getMatchingOrdinal(recipeName);
            entry = new ArtWorkImageRecipe();
            if(ordinal != -1) {
                ArtworkRecipeHollow artworkRecipeHollow = api.getArtworkRecipeHollow(ordinal);
                entry.cdnFolderStr = ConversionUtils.getCharArray(artworkRecipeHollow._getCdnFolder());
                entry.extensionStr = ConversionUtils.getCharArray(artworkRecipeHollow._getExtension());
                entry.recipeNameStr = ConversionUtils.getCharArray(artworkRecipeHollow._getRecipeName());
                StringHollow hostName = artworkRecipeHollow._getHostName();
                if(hostName != null)
                    entry.hostNameStr = ConversionUtils.getCharArray(hostName);
            }else {
                entry.cdnFolderStr = null; //ConversionUtils.getCharArray(derivative._getCdnDirectory());
                entry.extensionStr = recipeName.toCharArray();
                entry.recipeNameStr = recipeName.toCharArray();
            }

            imageRecipeCache.put(recipeName, entry);
        }

        return entry;
    }

    protected Set<ArtworkLocaleHollow> getLocalTerritories(ArtworkLocaleListHollow locales) {
        Set<ArtworkLocaleHollow> artworkLocales = new HashSet<>();
        Iterator<ArtworkLocaleHollow> iterator = locales.iterator();
        while(iterator.hasNext()) {
            ArtworkLocaleHollow locale = iterator.next();
            if(locale != null) {
                artworkLocales.add(locale);
            }
        }
        return artworkLocales;
    }

    protected Set<Artwork> getArtworkSet(int entityId, Map<java.lang.Integer, Set<Artwork>> artMap) {
        Set<Artwork> artworkSet = artMap.get(entityId);
        if (artworkSet == null) {
            artworkSet = new LinkedHashSet<>();
            artMap.put(entityId, artworkSet);
        }
        return artworkSet;
    }

    private static final String IMAGE_TYPE_MODIFICATION_CONFIGS = "TITLE_TREATMENT|autocrop|TITLE_TREATMENT_CROPPED\n"
                                                     + "LOGO_STACKED|autocrop|LOGO_STACKED_CROPPED\n"
                                                     + "LOGO_HORIZONTAL|autocrop|LOGO_HORIZONTAL_CROPPED\n"
                                                     + "NETFLIX_ORIGINAL|autocrop|NETFLIX_ORIGINAL_CROPPED\n"
                                                     + "BB2_OG_LOGO|autocrop|BB2_OG_LOGO_CROPPED\n"
                                                     + "BB2_OG_LOGO_PLUS|autocrop|BB2_OG_LOGO_PLUS_CROPPED\n"
                                                     + "BB2_OG_LOGO_STACKED|autocrop|BB2_OG_LOGO_STACKED_CROPPED\n"
                                                     + "OriginalsPostPlayLogoPostPlay|autocrop|OriginalsPostPlayLogoPostPlay_CROPPED\n"
                                                     + "OriginalsPostPlayLogoPostTrailer|autocrop|OriginalsPostPlayLogoPostTrailer_CROPPED\n"
                                                     + "OriginalsPostPlayLogoPrePlay|autocrop|OriginalsPostPlayLogoPrePlay_CROPPED\n"
                                                     + "StoryArt|left_gradient|STORYART_LEFT_GRADIENT\n"
                                                     + "StoryArt|right_gradient|STORYART_RIGHT_GRADIENT\n"
                                                     + "StoryArt|left_gradient_kids|STORYART_LEFT_GRADIENT_KIDS\n"
                                                     + "StoryArt|right_gradient_kids|STORYART_RIGHT_GRADIENT_KIDS\n"
                                                     + "NSRE_DATE_BADGE|autocrop|NSRE_DATE_BADGE_CROPPED\n"
                                                     + "MERCH_STILL|left_gradient|MERCH_STILL_LEFT_GRADIENT\n"
                                                     + "MERCH_STILL|right_gradient|MERCH_STILL_RIGHT_GRADIENT\n"
                                                     + "MERCH_STILL|left_gradient_kids|MERCH_STILL_LEFT_GRADIENT_KIDS\n"
                                                     + "MERCH_STILL|right_gradient_kids|MERCH_STILL_RIGHT_GRADIENT_KIDS\n"
                                                     + "STORYART_ADDITIONAL|left_gradient|STORYART_ADDITIONAL_LEFT_GRADIENT\n"
                                                     + "STORYART_ADDITIONAL|right_gradient|STORYART_ADDITIONAL_RIGHT_GRADIENT\n"
                                                     + "STORYART_ADDITIONAL|left_gradient_kids|STORYART_ADDITIONAL_LEFT_GRADIENT_KIDS\n"
                                                     + "STORYART_ADDITIONAL|right_gradient_kids|STORYART_ADDITIONAL_RIGHT_GRADIENT_KIDS\n"
                                                     + "CharacterStoryArt|left_gradient|CHARACTERSTORYART_LEFT_GRADIENT\n"
                                                     + "CharacterStoryArt|right_gradient|CHARACTERSTORYART_RIGHT_GRADIENT\n"
                                                     + "CharacterStoryArt|left_gradient_kids|CHARACTERSTORYART_LEFT_GRADIENT_KIDS\n"
                                                     + "CharacterStoryArt|right_gradient_kids|CHARACTERSTORYART_RIGHT_GRADIENT_KIDS\n"
                                                     + "BILLBOARD|bottom_gradient|BILLBOARD_BOTTOM_GRADIENT\n"
                                                     + "NEW_CONTENT_BADGE|autocrop|NEW_CONTENT_BADGE_CROPPED";
    
    private static class ModificationKey {
        private final String imageType;
        private final String modification;
        
        public ModificationKey(String imageType, String modification) {
            this.imageType = imageType;
            this.modification = modification;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((imageType == null) ? 0 : imageType.hashCode());
            result = prime * result
                    + ((modification == null) ? 0 : modification.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ModificationKey other = (ModificationKey) obj;
            if (imageType == null) {
                if (other.imageType != null)
                    return false;
            } else if (!imageType.equals(other.imageType))
                return false;
            if (modification == null) {
                if (other.modification != null)
                    return false;
            } else if (!modification.equals(other.modification))
                return false;
            return true;
        }
    }
    
    private static Map<ModificationKey, String> modifiedImageTypeMap = new HashMap<>();
    
    static {
        for(String modifiedImageTypeRecord : IMAGE_TYPE_MODIFICATION_CONFIGS.split("\n")) {
            String fields[] = modifiedImageTypeRecord.split("\\|");
            
            modifiedImageTypeMap.put(new ModificationKey(fields[0], fields[1]), fields[2]);
        }
    }
    
}