package pl.khamutouskaya.truckdispatch.algorithm.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;

/**
 * Graf dróg w pamięci w postaci listy sąsiedztwa: dla każdego węzła lista krawędzi wychodzących.
 * <p>
 * Graf jest rzadki (węzeł ma zwykle 1–6 sąsiadów), dlatego lista sąsiedztwa zajmuje O(V + E) pamięci,
 * a macierz sąsiedztwa zajmowałaby O(V²), w większości zera.
 * <p>
 * Obiekt jest niezmienny: po zmianie danych w bazie (np. delayFactor) tworzy się nowy graf.
 * Klasa nie zależy od Springa ani od JPA — łatwo ją testować na małych danych.
 */
public final class RoadGraph {

    private final Map<Long, GraphNode> nodes;
    private final Map<Long, List<GraphEdge>> outgoing;
    // Krawędzie wchodzące — potrzebne do sprawdzenia silnej spójności (BFS po odwróconym grafie)
    private final Map<Long, List<GraphEdge>> incoming;
    private final int edgeCount;

    public RoadGraph(Collection<GraphNode> nodeList, Collection<GraphEdge> edgeList) {
        Map<Long, GraphNode> nodeMap = new HashMap<>();
        for (GraphNode node : nodeList) {
            if (nodeMap.put(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate node id " + node.id());
            }
        }

        Map<Long, List<GraphEdge>> out = new HashMap<>();
        Map<Long, List<GraphEdge>> in = new HashMap<>();
        for (GraphNode node : nodeList) {
            out.put(node.id(), new ArrayList<>());
            in.put(node.id(), new ArrayList<>());
        }
        for (GraphEdge edge : edgeList) {
            if (!nodeMap.containsKey(edge.fromId()) || !nodeMap.containsKey(edge.toId())) {
                throw new IllegalArgumentException("Edge " + edge.id() + " refers to unknown node");
            }
            out.get(edge.fromId()).add(edge);
            in.get(edge.toId()).add(edge);
        }

        this.nodes = Map.copyOf(nodeMap);
        this.outgoing = freeze(out);
        this.incoming = freeze(in);
        this.edgeCount = edgeList.size();
    }

    public GraphNode node(long id) {
        GraphNode node = nodes.get(id);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node id " + id);
        }
        return node;
    }

    public Collection<GraphNode> nodes() {
        return nodes.values();
    }

    // Krawędzie wychodzące z węzła (pusta lista, jeśli z węzła nie wychodzi żadna droga)
    public List<GraphEdge> outgoingEdges(long nodeId) {
        node(nodeId); // sprawdza, czy węzeł istnieje
        return outgoing.get(nodeId);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edgeCount;
    }

    // Węzły, do których da się dojechać z węzła start (BFS po krawędziach wychodzących)
    public Set<Long> reachableFrom(long startId) {
        return bfs(startId, outgoing, GraphEdge::toId);
    }

    // Węzły, z których da się dojechać do węzła target (BFS po krawędziach odwróconych)
    public Set<Long> canReach(long targetId) {
        return bfs(targetId, incoming, GraphEdge::fromId);
    }

    /**
     * Graf skierowany jest silnie spójny, jeśli z każdego węzła da się dojechać do każdego innego.
     * Wystarczą dwa przejścia BFS z dowolnego węzła s: jeśli s osiąga wszystkie węzły
     * i wszystkie węzły osiągają s, to każde u dojedzie do każdego v przez s (u → s → v).
     * Złożoność O(V + E).
     */
    public boolean isStronglyConnected() {
        if (nodes.isEmpty()) {
            return true;
        }
        long start = nodes.keySet().iterator().next();
        return reachableFrom(start).size() == nodes.size()
                && canReach(start).size() == nodes.size();
    }

    // Przeszukiwanie wszerz (kolejka FIFO); next wskazuje, w którą stronę idziemy po krawędzi
    private Set<Long> bfs(long startId, Map<Long, List<GraphEdge>> adjacency, Function<GraphEdge, Long> next) {
        node(startId);
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new ArrayDeque<>();
        visited.add(startId);
        queue.add(startId);
        while (!queue.isEmpty()) {
            long current = queue.poll();
            for (GraphEdge edge : adjacency.get(current)) {
                long neighbour = next.apply(edge);
                if (visited.add(neighbour)) {   // add zwraca false, jeśli węzeł już był odwiedzony
                    queue.add(neighbour);
                }
            }
        }
        return Collections.unmodifiableSet(visited);
    }

    private static Map<Long, List<GraphEdge>> freeze(Map<Long, List<GraphEdge>> map) {
        Map<Long, List<GraphEdge>> result = new HashMap<>();
        map.forEach((id, edges) -> result.put(id, List.copyOf(edges)));
        return Map.copyOf(result);
    }
}
