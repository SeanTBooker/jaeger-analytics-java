package io.jaegertracing.analytics;

import io.jaegertracing.analytics.gremlin.Keys;
import io.jaegertracing.analytics.gremlin.TraceTraversal;
import io.jaegertracing.analytics.gremlin.TraceTraversalSource;
import io.jaegertracing.analytics.gremlin.Util;
import io.opentracing.tag.Tags;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Child;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;

/**
 * @author Pavol Loffay
 */
public class NetworkLatency {

  /**
   * Returns network latency between client and server spans. Name contains service names
   *
   * @param graph
   * @return
   */
  public static Map<Name, Set<Long>> calculate(Graph graph) {
    Map<Name, Set<Long>> results = new LinkedHashMap<>();

    TraceTraversal<Vertex, Vertex> clientSpans = graph
        .traversal(TraceTraversalSource.class).V()
        .hasTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

    clientSpans.forEachRemaining(client -> {
      String clientService = (String)client.property(Keys.SERVICE_NAME).value();
      for (Vertex child : Util.descendants(client)) {
        if (child.property(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).isPresent()) {
          String serverService = (String)child.property(Keys.SERVICE_NAME).value();
          Long clientStartTime = (Long)client.property(Keys.START_TIME).value();
          Long serverStartTime = (Long)child.property(Keys.START_TIME).value();
          Long latency = serverStartTime - clientStartTime;

          Name name = new Name(clientService, serverService);
          Set<Long> latencies = results.get(name);
          if (latencies == null) {
            latencies = new LinkedHashSet<>();
            results.put(name, latencies);
          }
          latencies.add(latency/1000);
        }
      }
    });

    return results;
  }

  private static final String METRIC_NAME = "network_latency_milliseconds";
  private static final Histogram histogram = Histogram.build()
      .name(METRIC_NAME)
      .help("Network latency between client and server span")
      .labelNames("client", "server")
      .create()
      .register();

  public static void calculateWithMetrics(Graph graph) {
    Map<Name, Set<Long>> latencies = calculate(graph);
    System.out.println(latencies);
    for (Map.Entry<Name, Set<Long>> entry: latencies.entrySet()) {
      Child child = histogram.labels(entry.getKey().client, entry.getKey().server);
      for (Long latency: entry.getValue()) {
        child.observe(latency);
      }
    }
  }

  public static class Name {
    public final String client;
    public final String server;

    public Name(String client, String server) {
      this.client = client;
      this.server = server;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Name name = (Name) o;
      return client.equals(name.client) &&
          server.equals(name.server);
    }

    @Override
    public int hashCode() {
      return Objects.hash(client, server);
    }

    @Override
    public String toString() {
      return "Name{" +
          "client='" + client + '\'' +
          ", server='" + server + '\'' +
          '}';
    }
  }
}
