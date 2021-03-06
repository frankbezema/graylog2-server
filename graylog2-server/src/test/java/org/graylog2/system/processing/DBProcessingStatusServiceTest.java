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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.joschi.jadconfig.util.Duration;
import com.lordofthejars.nosqlunit.annotation.UsingDataSet;
import com.lordofthejars.nosqlunit.core.LoadStrategyEnum;
import com.lordofthejars.nosqlunit.mongodb.InMemoryMongoDb;
import org.bson.types.ObjectId;
import org.graylog.events.JobSchedulerTestClock;
import org.graylog2.bindings.providers.MongoJackObjectMapperProvider;
import org.graylog2.database.MongoConnectionRule;
import org.graylog2.plugin.lifecycles.Lifecycle;
import org.graylog2.plugin.system.NodeId;
import org.graylog2.shared.bindings.providers.ObjectMapperProvider;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mongojack.DBQuery;
import org.mongojack.JacksonDBCollection;

import java.util.stream.Collectors;

import static com.lordofthejars.nosqlunit.mongodb.InMemoryMongoDb.InMemoryMongoRuleBuilder.newInMemoryMongoDbRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class DBProcessingStatusServiceTest {
    private static final String NODE_ID = "abc-123";

    @ClassRule
    public static final InMemoryMongoDb IN_MEMORY_MONGO_DB = newInMemoryMongoDbRule().build();

    @Rule
    public MongoConnectionRule mongoRule = MongoConnectionRule.build("test");
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private NodeId nodeId;

    private DBProcessingStatusService dbService;
    private JobSchedulerTestClock clock;
    private Duration updateThreshold;
    private JacksonDBCollection<ProcessingStatusDto, ObjectId> db;

    @Before
    public void setUp() throws Exception {
        when(nodeId.toString()).thenReturn(NODE_ID);

        final ObjectMapper objectMapper = new ObjectMapperProvider().get();
        final MongoJackObjectMapperProvider mapperProvider = new MongoJackObjectMapperProvider(objectMapper);

        clock = spy(new JobSchedulerTestClock(DateTime.parse("2019-01-01T00:00:00.000Z")));
        updateThreshold = spy(Duration.minutes(1));
        dbService = new DBProcessingStatusService(mongoRule.getMongoConnection(), nodeId, clock, updateThreshold, 1, mapperProvider);
        db = JacksonDBCollection.wrap(mongoRule.getMongoConnection().getDatabase().getCollection(DBProcessingStatusService.COLLECTION_NAME),
                ProcessingStatusDto.class,
                ObjectId.class,
                mapperProvider.get());
    }

    @Test
    @UsingDataSet(locations = "processing-status.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void loadPersisted() {
        assertThat(dbService.all()).hasSize(3);

        assertThat(dbService.all().get(0)).satisfies(dto -> {
            assertThat(dto.id()).isEqualTo("54e3deadbeefdeadbeef0000");
            assertThat(dto.nodeId()).isEqualTo("abc-123");
            assertThat(dto.updatedAt()).isEqualByComparingTo(DateTime.parse("2019-01-01T00:01:00.000Z"));
            assertThat(dto.nodeLifecycleStatus()).isEqualTo(Lifecycle.RUNNING);

            assertThat(dto.receiveTimes()).satisfies(receiveTimes -> {
                assertThat(receiveTimes.ingest()).isEqualByComparingTo(DateTime.parse("2019-01-01T00:03:00.000Z"));
                assertThat(receiveTimes.postProcessing()).isEqualByComparingTo(DateTime.parse("2019-01-01T00:02:00.000Z"));
                assertThat(receiveTimes.postIndexing()).isEqualByComparingTo(DateTime.parse("2019-01-01T00:01:00.000Z"));
            });

            assertThat(dto.inputJournal()).satisfies(inputJournal -> {
                assertThat(inputJournal.uncommittedEntries()).isEqualTo(0);
                assertThat(inputJournal.readMessages1mRate()).isEqualTo(12.0d);
                assertThat(inputJournal.writtenMessages1mRate()).isEqualTo(12.0d);
            });
        });

        assertThat(dbService.all().get(1)).satisfies(dto -> {
            assertThat(dto.id()).isEqualTo("54e3deadbeefdeadbeef0001");
            assertThat(dto.nodeId()).isEqualTo("abc-456");
            assertThat(dto.updatedAt()).isEqualByComparingTo(DateTime.parse("2019-01-01T02:01:00.000Z"));
            assertThat(dto.nodeLifecycleStatus()).isEqualTo(Lifecycle.RUNNING);

            assertThat(dto.receiveTimes()).satisfies(receiveTimes -> {
                assertThat(receiveTimes.ingest()).isEqualByComparingTo(DateTime.parse("2019-01-01T01:03:00.000Z"));
                assertThat(receiveTimes.postProcessing()).isEqualByComparingTo(DateTime.parse("2019-01-01T01:02:00.000Z"));
                assertThat(receiveTimes.postIndexing()).isEqualByComparingTo(DateTime.parse("2019-01-01T02:01:00.000Z"));
            });

            assertThat(dto.inputJournal()).satisfies(inputJournal -> {
                assertThat(inputJournal.uncommittedEntries()).isEqualTo(0);
                assertThat(inputJournal.readMessages1mRate()).isEqualTo(0);
                assertThat(inputJournal.writtenMessages1mRate()).isEqualTo(0);
            });
        });

        assertThat(dbService.all().get(2)).satisfies(dto -> {
            assertThat(dto.id()).isEqualTo("54e3deadbeefdeadbeef0002");
            assertThat(dto.nodeId()).isEqualTo("abc-789");
            assertThat(dto.updatedAt()).isEqualByComparingTo(DateTime.parse("2019-01-01T01:01:00.000Z"));
            assertThat(dto.nodeLifecycleStatus()).isEqualTo(Lifecycle.STARTING);

            assertThat(dto.receiveTimes()).satisfies(receiveTimes -> {
                assertThat(receiveTimes.ingest()).isEqualByComparingTo(DateTime.parse("2019-01-01T02:03:00.000Z"));
                assertThat(receiveTimes.postProcessing()).isEqualByComparingTo(DateTime.parse("2019-01-01T02:02:00.000Z"));
                assertThat(receiveTimes.postIndexing()).isEqualByComparingTo(DateTime.parse("2019-01-01T01:01:00.000Z"));
            });

            assertThat(dto.inputJournal()).satisfies(inputJournal -> {
                assertThat(inputJournal.uncommittedEntries()).isEqualTo(42);
                assertThat(inputJournal.readMessages1mRate()).isEqualTo(2.0d);
                assertThat(inputJournal.writtenMessages1mRate()).isEqualTo(4.0d);
            });
        });
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.DELETE_ALL)
    public void persistAndUpdate() {
        final InMemoryProcessingStatusRecorder statusRecorder = new InMemoryProcessingStatusRecorder();
        final DateTime now = DateTime.now(DateTimeZone.UTC);

        statusRecorder.updateIngestReceiveTime(now);
        statusRecorder.updatePostProcessingReceiveTime(now.minusSeconds(1));
        statusRecorder.updatePostIndexingReceiveTime(now.minusSeconds(2));

        statusRecorder.uncommittedMessages.set(123);
        statusRecorder.readMessages1m.set(1);
        statusRecorder.writtenMessages1m.set(2);

        assertThat(dbService.save(statusRecorder, now)).satisfies(dto -> {
            assertThat(dto.id()).isNotBlank();
            assertThat(dto.nodeId()).isEqualTo(NODE_ID);
            assertThat(dto.updatedAt()).isEqualByComparingTo(now);
            assertThat(dto.nodeLifecycleStatus()).isEqualTo(Lifecycle.RUNNING);

            assertThat(dto.receiveTimes()).satisfies(receiveTimes -> {
                assertThat(receiveTimes.ingest()).isEqualByComparingTo(now);
                assertThat(receiveTimes.postProcessing()).isEqualByComparingTo(now.minusSeconds(1));
                assertThat(receiveTimes.postIndexing()).isEqualByComparingTo(now.minusSeconds(2));
            });

            assertThat(dto.inputJournal()).satisfies(inputJournal -> {
                assertThat(inputJournal.uncommittedEntries()).isEqualTo(123);
                assertThat(inputJournal.readMessages1mRate()).isEqualTo(1.0d);
                assertThat(inputJournal.writtenMessages1mRate()).isEqualTo(2.0d);
            });
        });

        assertThat(dbService.all()).hasSize(1);

        // Advance time and update the status recorder
        final DateTime tomorrow = now.plusDays(1);

        statusRecorder.updateIngestReceiveTime(tomorrow);
        statusRecorder.updatePostProcessingReceiveTime(tomorrow.minusSeconds(1));
        statusRecorder.updatePostIndexingReceiveTime(tomorrow.minusSeconds(2));

        // Save the updated recorder
        assertThat(dbService.save(statusRecorder, tomorrow)).satisfies(dto -> {
            assertThat(dto.id()).isNotBlank();
            assertThat(dto.nodeId()).isEqualTo(NODE_ID);
            assertThat(dto.updatedAt()).isEqualByComparingTo(tomorrow);
            assertThat(dto.nodeLifecycleStatus()).isEqualTo(Lifecycle.RUNNING);

            assertThat(dto.receiveTimes()).satisfies(receiveTimes -> {
                assertThat(receiveTimes.ingest()).isEqualByComparingTo(tomorrow);
                assertThat(receiveTimes.postProcessing()).isEqualByComparingTo(tomorrow.minusSeconds(1));
                assertThat(receiveTimes.postIndexing()).isEqualByComparingTo(tomorrow.minusSeconds(2));
            });

            assertThat(dto.inputJournal()).satisfies(inputJournal -> {
                assertThat(inputJournal.uncommittedEntries()).isEqualTo(123);
                assertThat(inputJournal.readMessages1mRate()).isEqualTo(1.0d);
                assertThat(inputJournal.writtenMessages1mRate()).isEqualTo(2.0d);
            });
        });

        // The save() should be an upsert so we should only have one document
        assertThat(dbService.all()).hasSize(1);
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.DELETE_ALL)
    public void get() {
        assertThat(dbService.get()).isNotPresent();

        dbService.save(new InMemoryProcessingStatusRecorder());

        assertThat(dbService.get()).isPresent();
    }

    @Test
    @UsingDataSet(locations = "processing-status.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void earliestPostIndexingTimestamp() {
        when(clock.nowUTC()).thenReturn(DateTime.parse("2019-01-01T02:01:00.000Z"));

        // The exclude threshold is big enough to include all three nodes. So abc-123 has the earliest indexed timestamp
        when(updateThreshold.toMilliseconds()).thenReturn(Duration.hours(4).toMilliseconds());
        assertThat(dbService.earliestPostIndexingTimestamp()).isPresent().get().isEqualTo(DateTime.parse("2019-01-01T00:01:00.000Z"));

        // The exclude threshold is only including nodes abc-456 and abc-789. So abc-789 has the earliest indexed timestamp
        when(updateThreshold.toMilliseconds()).thenReturn(Duration.hours(2).toMilliseconds());
        assertThat(dbService.earliestPostIndexingTimestamp()).isPresent().get().isEqualTo(DateTime.parse("2019-01-01T01:01:00.000Z"));
    }

    @Test
    @UsingDataSet(locations = "processing-status.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void earliestPostIndexingTimestampWithoutAnyRecentUpdates() {
        // The exclude threshold is not including any nodes
        when(clock.nowUTC()).thenReturn(DateTime.parse("2019-01-02T00:00:00.000Z"));
        assertThat(dbService.earliestPostIndexingTimestamp()).isNotPresent();
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.DELETE_ALL)
    public void earliestPostIndexingTimestampWithoutData() {
        assertThat(dbService.earliestPostIndexingTimestamp()).isNotPresent();
    }

    @Test
    @UsingDataSet(locations = "processing-status-node-selection-test.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void selectionQuery() {
        when(clock.nowUTC()).thenReturn(DateTime.parse("2019-01-01T00:03:00.000Z"));

        final DBQuery.Query query1 = DBProcessingStatusService.getDataSelectionQuery(clock, Duration.hours(1), 1.0);

        assertThat(db.find(query1).toArray().stream().map(ProcessingStatusDto::nodeId).collect(Collectors.toSet()))
                .containsOnly("abc-123", "abc-456", "abc-678");

        // With a higher journal write threshold there should only be two node IDs
        final DBQuery.Query query2 = DBProcessingStatusService.getDataSelectionQuery(clock, Duration.hours(1), 2.0);

        assertThat(db.find(query2).toArray().stream().map(ProcessingStatusDto::nodeId).collect(Collectors.toSet()))
                .containsOnly("abc-123", "abc-456");
    }
}
