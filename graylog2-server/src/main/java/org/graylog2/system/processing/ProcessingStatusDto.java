/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.system.processing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import org.graylog2.plugin.lifecycles.Lifecycle;
import org.joda.time.DateTime;
import org.mongojack.Id;
import org.mongojack.ObjectId;

import javax.annotation.Nullable;

@AutoValue
@JsonDeserialize(builder = ProcessingStatusDto.Builder.class)
public abstract class ProcessingStatusDto {
    private static final String FIELD_ID = "id";
    static final String FIELD_NODE_ID = "node_id";
    static final String FIELD_NODE_LIFECYCLE_STATUS = "node_lifecycle_status";
    static final String FIELD_UPDATED_AT = "updated_at";
    static final String FIELD_RECEIVE_TIMES = "receive_times";
    static final String FIELD_INPUT_JOURNAL = "input_journal";

    @Id
    @ObjectId
    @Nullable
    @JsonProperty(FIELD_ID)
    public abstract String id();

    @JsonProperty(FIELD_NODE_ID)
    public abstract String nodeId();

    @JsonProperty(FIELD_NODE_LIFECYCLE_STATUS)
    public abstract Lifecycle nodeLifecycleStatus();

    @JsonProperty(FIELD_UPDATED_AT)
    public abstract DateTime updatedAt();

    @JsonProperty(FIELD_RECEIVE_TIMES)
    public abstract ReceiveTimes receiveTimes();

    @JsonProperty(FIELD_INPUT_JOURNAL)
    public abstract JournalInfo inputJournal();

    public static ProcessingStatusDto of(String nodeId, ProcessingStatusRecorder processingStatusRecorder, DateTime updatedAt) {
        return builder()
                .nodeId(nodeId)
                .updatedAt(updatedAt)
                .nodeLifecycleStatus(processingStatusRecorder.getNodeLifecycleStatus())
                .receiveTimes(ReceiveTimes.builder()
                        .ingest(processingStatusRecorder.getIngestReceiveTime())
                        .postProcessing(processingStatusRecorder.getPostProcessingReceiveTime())
                        .postIndexing(processingStatusRecorder.getPostIndexingReceiveTime())
                        .build())
                .inputJournal(JournalInfo.builder()
                        .uncommittedEntries(processingStatusRecorder.getJournalInfoUncommittedEntries())
                        .readMessages1mRate(processingStatusRecorder.getJournalInfoReadMessages1mRate())
                        .writtenMessages1mRate(processingStatusRecorder.getJournalInfoWrittenMessages1mRate())
                        .build())
                .build();
    }

    public static Builder builder() {
        return Builder.create();
    }

    public abstract Builder toBuilder();

    @AutoValue.Builder
    public static abstract class Builder {
        @JsonCreator
        public static Builder create() {
            return new AutoValue_ProcessingStatusDto.Builder()
                    // 3.1.0-beta/rc setups didn't have the lifecycle status and journal info so we need to have a default for them.
                    // TODO: The lifecycle status and journal info defaults can be removed at some point after 3.1.0
                    .nodeLifecycleStatus(Lifecycle.RUNNING)
                    .inputJournal(JournalInfo.builder().build());
        }

        @Id
        @ObjectId
        @JsonProperty(FIELD_ID)
        public abstract Builder id(String id);

        @JsonProperty(FIELD_NODE_ID)
        public abstract Builder nodeId(String nodeId);

        @JsonProperty(FIELD_NODE_LIFECYCLE_STATUS)
        public abstract Builder nodeLifecycleStatus(Lifecycle lifecycleStatus);

        @JsonProperty(FIELD_UPDATED_AT)
        public abstract Builder updatedAt(DateTime updatedAt);

        @JsonProperty(FIELD_RECEIVE_TIMES)
        public abstract Builder receiveTimes(ReceiveTimes receiveTimes);

        @JsonProperty(FIELD_INPUT_JOURNAL)
        public abstract Builder inputJournal(JournalInfo inputJournal);

        public abstract ProcessingStatusDto build();
    }

    @AutoValue
    @JsonDeserialize(builder = ReceiveTimes.Builder.class)
    public static abstract class ReceiveTimes {
        private static final String FIELD_INGEST = "ingest";
        private static final String FIELD_POST_PROCESSING = "post_processing";
        static final String FIELD_POST_INDEXING = "post_indexing";

        @JsonProperty(FIELD_INGEST)
        public abstract DateTime ingest();

        @JsonProperty(FIELD_POST_PROCESSING)
        public abstract DateTime postProcessing();

        @JsonProperty(FIELD_POST_INDEXING)
        public abstract DateTime postIndexing();

        public static Builder builder() {
            return Builder.create();
        }

        @AutoValue.Builder
        public static abstract class Builder {
            @JsonCreator
            public static Builder create() {
                return new AutoValue_ProcessingStatusDto_ReceiveTimes.Builder();
            }

            @JsonProperty(FIELD_INGEST)
            public abstract Builder ingest(DateTime timestamp);

            @JsonProperty(FIELD_POST_PROCESSING)
            public abstract Builder postProcessing(DateTime timestamp);

            @JsonProperty(FIELD_POST_INDEXING)
            public abstract Builder postIndexing(DateTime timestamp);

            public abstract ReceiveTimes build();
        }
    }

    @AutoValue
    @JsonDeserialize(builder = JournalInfo.Builder.class)
    public static abstract class JournalInfo {
        static final String FIELD_UNCOMMITTED_ENTRIES = "uncommitted_entries";
        private static final String FIELD_READ_MESSAGES_1M_RATE = "read_messages_1m_rate";
        static final String FIELD_WRITTEN_MESSAGES_1M_RATE = "written_messages_1m_rate";

        @JsonProperty(FIELD_UNCOMMITTED_ENTRIES)
        public abstract long uncommittedEntries();

        @JsonProperty(FIELD_READ_MESSAGES_1M_RATE)
        public abstract double readMessages1mRate();

        @JsonProperty(FIELD_WRITTEN_MESSAGES_1M_RATE)
        public abstract double writtenMessages1mRate();

        public static Builder builder() {
            return Builder.create();
        }

        @AutoValue.Builder
        public abstract static class Builder {
            @JsonCreator
            public static Builder create() {
                return new AutoValue_ProcessingStatusDto_JournalInfo.Builder()
                        .uncommittedEntries(0)
                        .readMessages1mRate(0d)
                        .writtenMessages1mRate(0d);
            }

            @JsonProperty(FIELD_UNCOMMITTED_ENTRIES)
            public abstract Builder uncommittedEntries(long uncommittedEntries);

            @JsonProperty(FIELD_READ_MESSAGES_1M_RATE)
            public abstract Builder readMessages1mRate(double readMessages1mRate);

            @JsonProperty(FIELD_WRITTEN_MESSAGES_1M_RATE)
            public abstract Builder writtenMessages1mRate(double writtenMessages1mRate);

            public abstract JournalInfo build();
        }
    }
}
