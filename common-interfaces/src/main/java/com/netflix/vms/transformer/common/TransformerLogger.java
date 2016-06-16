package com.netflix.vms.transformer.common;

import java.util.Arrays;
import java.util.Collection;

public interface TransformerLogger {

    public static enum LogTag {
        WaitForNextCycle,
        TransformCycleBegin,
        TransformCycleSuccess,
        TransformCycleFailed,
        TransformProgress,
        InputDataVersionIds,
        FollowVip,
        InputDataConverterVersionId,
        ProcessNowMillis,
        PropertyValue,
        WroteBlob,
        WritingBlobsFailed,
        NonVideoSpecificTransformDuration,
        ConfigurationFailure,
        UnexpectedError,

        //// TRANSFORMATION ERRORS ////

        IndividualTransformFailed,
        UnknownArtworkImageType,
        MissingLocaleForArtwork,
        InvalidImagesTerritoryCode,
        LanguageRightsError,

        //// TRANSFORMATION MISC ////

        L10NProcessing,


        //// PUBLISH WORKFLOW ////

        CircuitBreaker,
        PublishedBlob,
        PlaybackMonkey,
        PlaybackMonkeyTestVideo,
        DataCanary,
        MarkedPoisonState,
        ObservedPoisonState,
        AnnouncementSuccess,
        AnnouncementFailure,
        BlobChecksum
    }

    public static enum Severity {
        INFO,
        WARN,
        ERROR
    }


    default void info(LogTag messageTag, Object message) {
        log(Severity.INFO, Arrays.asList(messageTag), message, null);
    }

    default void info(Collection<LogTag> messageTags, Object message) {
        log(Severity.INFO, messageTags, message, null);
    }

    default void warn(LogTag messageTag, Object message) {
        log(Severity.WARN, Arrays.asList(messageTag), message, null);
    }

    default void warn(Collection<LogTag> messageTags, Object message) {
        log(Severity.WARN, messageTags, message, null);
    }

    default void error(LogTag messageTag, Object message) {
        log(Severity.ERROR, Arrays.asList(messageTag), message, null);
    }

    default void error(Collection<LogTag> messageTags, Object message) {
        log(Severity.ERROR, messageTags, message, null);
    }

    default void error(LogTag messageTag, Object message, Throwable th) {
        log(Severity.ERROR, Arrays.asList(messageTag), message, th);
    }

    default void error(Collection<LogTag> messageTags, Object message, Throwable th) {
        log(Severity.ERROR, messageTags, message, th);
    }

    default void log(Severity severity, Collection<LogTag> tags, Object message, Throwable th) {
        log(severity, tags, message.toString(), th);
    }

    void log(Severity severity, Collection<LogTag> tags, String message, Throwable th);


}
