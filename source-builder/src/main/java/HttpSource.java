/*
 * Copyright (c) 2008-2018, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.datamodel.TimestampedItem;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.SourceBuilder;
import com.hazelcast.jet.pipeline.SourceBuilder.TimestampedSourceBuffer;
import com.hazelcast.jet.pipeline.StreamSource;
import io.undertow.Undertow;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import support.SystemMonitorGui;
import support.SystemMonitorHttpService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Stream;

import static com.hazelcast.jet.Util.entry;
import static com.hazelcast.jet.aggregate.AggregateOperations.linearTrend;
import static com.hazelcast.jet.pipeline.WindowDefinition.sliding;

/**
 * Shows how to use the {@link com.hazelcast.jet.pipeline.SourceBuilder} to
 * build a source connector for the Jet pipeline. It starts a simple
 * {@linkplain support.SystemMonitorHttpService system-monitoring HTTP
 * service}. The connector polls it for events that contain the measurement
 * of the used JVM heap.
 * <p>
 * The pipeline finds the linear trend of used memory over a sliding window;
 * in other words, the current allocation rate. It sends the output to a
 * Hazelcast map.
 * <p>
 * The sample starts a GUI window that shows the allocation rate over time
 * in a graph.
 */
public class HttpSource {

    private static final String MAP_NAME = "system-monitor";

    /**
     * This code is the main point of the sample: use the source builder to
     * create an HTTP source connector, then create a Jet pipeline that
     * performs windowed aggregation over its data.
     */
    private static Pipeline buildPipeline() {
        StreamSource<TimestampedItem<Long>> usedMemorySource = SourceBuilder
                .timestampedStream("used-memory", x -> new PollHttp())
                .fillBufferFn(PollHttp::fillBuffer)
                .destroyFn(PollHttp::close)
                .build();
        Pipeline p = Pipeline.create();
        p.drawFrom(usedMemorySource)
         .window(sliding(100, 20))
         .aggregate(linearTrend(TimestampedItem::timestamp, TimestampedItem::item))
         .map(tsItem -> entry(tsItem.timestamp(), tsItem.item()))
         .drainTo(Sinks.map(MAP_NAME));
        return p;
    }

    /**
     * Contains the logic of the HTTP-polling source.
     */
    private static class PollHttp {
        private final CloseableHttpClient httpc = HttpClients.createDefault();
        private final long pollIntervalMillis = 20;
        private long lastPolled;

        /**
         * Jet repeatedly calls this method whenever it wants more data from our
         * source. The source limits the rate of HTTP requests by first checking
         * that enough time has elapsed since the previous request. Then it
         * makes the request and emits each line of the request as a pair {@code
         * (timestamp, usedMemory)}.
         */
        void fillBuffer(TimestampedSourceBuffer<TimestampedItem<Long>> buf) throws IOException {
            if (!readyToPoll()) {
                return;
            }
            try (Stream<String> lines = new BufferedReader(new InputStreamReader(
                    httpc.execute(new HttpGet("http://localhost:8008"))
                         .getEntity().getContent()))
                    .lines()
            ) {
                lines.forEach(line -> {
                    int splitPoint = line.indexOf(' ');
                    long timestamp = Long.valueOf(line.substring(0, splitPoint));
                    long value = Long.valueOf(line.substring(splitPoint + 1));
                    buf.add(new TimestampedItem<>(timestamp, value), timestamp);
                });
            }
        }

        private boolean readyToPoll() {
            long now = System.currentTimeMillis();
            if (now - lastPolled < pollIntervalMillis) {
                return false;
            }
            lastPolled = now;
            return true;
        }

        void close() throws IOException {
            httpc.close();
        }
    }

    /**
     * Starts the system-monitoring HTTP service, the GUI screen and Hazelcast
     * Jet, and runs the stream job on it.
     */
    public static void main(String[] args) {
        System.setProperty("hazelcast.logging.type", "log4j");
        Undertow server = new SystemMonitorHttpService().httpServer();
        server.start();
        try {
            JetInstance jet = startJet();
            new SystemMonitorGui(jet.getMap(MAP_NAME));
            runPipeline(jet);
        } finally {
            server.stop();
            Jet.shutdownAll();
        }
    }

    private static JetInstance startJet() {
        System.out.println("Creating Jet instance 1");
        Jet.newJetInstance();
        System.out.println("Creating Jet instance 2");
        return Jet.newJetInstance();
    }

    private static void runPipeline(JetInstance jet) {
        System.out.println("\nRunning the pipeline ");
        Pipeline p = buildPipeline();
        jet.newJob(p).join();
    }
}
