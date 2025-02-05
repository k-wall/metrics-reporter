/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.client.Collector;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Measurable;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaMetricsCollectorTest {

    private final MetricConfig metricConfig = new MetricConfig();
    private final Time time = Time.SYSTEM;
    private Map<String, String> labels;

    @BeforeEach
    public void setup() {
        labels = new HashMap<>();
        labels.put("key", "value");
    }

    @Test
    public void testMetricLifecycle() {
        Map<String, String> props = new HashMap<>();
        props.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props);
        KafkaMetricsCollector collector = new KafkaMetricsCollector(config);
        collector.setPrefix("kafka.server");

        List<Collector.MetricFamilySamples> metrics = collector.collect();
        assertTrue(metrics.isEmpty());

        // Adding a metric not matching the allowlist does nothing
        collector.addMetric(buildMetric("name", "other", 2.0));
        metrics = collector.collect();
        assertTrue(metrics.isEmpty());

        // Adding a metric that matches the allowlist
        collector.addMetric(buildMetric("name", "group", 1.0));
        metrics = collector.collect();
        assertEquals(1, metrics.size());

        Collector.MetricFamilySamples metricFamilySamples = metrics.get(0);
        assertMetricFamilySample(metricFamilySamples, 1.0, labels);

        // Adding the same metric updates its value
        collector.addMetric(buildMetric("name", "group", 3.0));
        metrics = collector.collect();
        assertEquals(1, metrics.size());

        Collector.MetricFamilySamples updatedMetrics = metrics.get(0);
        assertMetricFamilySample(updatedMetrics, 3.0, labels);

        // Removing the metric
        collector.removeMetric(buildMetric("name", "group", 4.0));
        metrics = collector.collect();
        assertTrue(metrics.isEmpty());
    }

    @Test
    public void testCollectNonNumericMetric() {
        Map<String, String> props = new HashMap<>();
        props.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props);
        KafkaMetricsCollector collector = new KafkaMetricsCollector(config);
        collector.setPrefix("kafka.server");

        List<Collector.MetricFamilySamples> metrics = collector.collect();
        assertTrue(metrics.isEmpty());

        // Adding a non-numeric metric converted
        String nonNumericValue = "myValue";
        KafkaMetric nonNumericMetric = buildNonNumericMetric("name", "group", nonNumericValue);
        collector.addMetric(nonNumericMetric);
        metrics = collector.collect();

        Map<String, String> expectedLabels = new LinkedHashMap<>(labels);
        expectedLabels.put("name", nonNumericValue);
        assertEquals(1, metrics.size());

        Collector.MetricFamilySamples metricFamilySamples = metrics.get(0);

        assertEquals("kafka_server_group_name", metricFamilySamples.name);
        assertMetricFamilySample(metricFamilySamples, 1.0, expectedLabels);
    }

    private void assertMetricFamilySample(Collector.MetricFamilySamples actual, double expectedValue, Map<String, String> expectedLabels) {

        Collector.MetricFamilySamples.Sample actualSample = actual.samples.get(0);

        assertEquals(1, actual.samples.size());
        assertEquals(actualSample.value, expectedValue, 0.1);
        assertEquals(new ArrayList<>(expectedLabels.keySet()), actualSample.labelNames);
        assertEquals(new ArrayList<>(expectedLabels.values()), actualSample.labelValues);
    }

    private KafkaMetric buildMetric(String name, String group, double value) {
        Measurable measurable = (config, now) -> value;
        return new KafkaMetric(
                new Object(),
                new MetricName(name, group, "", labels),
                measurable,
                metricConfig,
                time);
    }

    private KafkaMetric buildNonNumericMetric(String name, String group, String value) {
        Gauge<String> measurable = (config, now) -> value;
        return new KafkaMetric(
                new Object(),
                new MetricName(name, group, "", labels),
                measurable,
                metricConfig,
                time);
    }

}
