package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphEdge;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphNode;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RoadGraph;
import pl.khamutouskaya.truckdispatch.domain.RoadEdge;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.repository.RoadEdgeRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Spring-owa nakładka na RoadGraph: ładuje graf z bazy do pamięci i udostępnia go algorytmom
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoadGraphService {

    private final RoadNodeRepository roadNodeRepository;
    private final RoadEdgeRepository roadEdgeRepository;

    // volatile: po reload() inne wątki (żądania HTTP) od razu widzą nowy graf.
    // Sam RoadGraph jest niezmienny, więc podmiana jednej referencji jest bezpieczna.
    private volatile RoadGraph graph;

    // Wywoływane raz po starcie aplikacji (przez proxy Springa, więc działa @Transactional).
    // Wyjątek tutaj zatrzymuje start aplikacji: lepiej nie wystartować niż szukać tras w niespójnym grafie.
    @EventListener(ApplicationReadyEvent.class)
    public void reload() {
        List<GraphNode> nodes = roadNodeRepository.findAll().stream()
                .map(RoadGraphService::toGraphNode)
                .toList();
        List<GraphEdge> edges = roadEdgeRepository.findAll().stream()
                .map(RoadGraphService::toGraphEdge)
                .toList();

        RoadGraph loaded = new RoadGraph(nodes, edges);
        if (!loaded.isStronglyConnected()) {
            throw new IllegalStateException("Road graph is not strongly connected; problem nodes: "
                    + problemNodeNames(loaded));
        }
        graph = loaded;
        log.info("Road graph loaded: {} nodes, {} edges, strongly connected", loaded.nodeCount(), loaded.edgeCount());
    }

    public RoadGraph graph() {
        RoadGraph current = graph;
        if (current == null) {
            throw new IllegalStateException("Road graph is not loaded yet");
        }
        return current;
    }

    // Węzły spoza największej silnie spójnej składowej. Punkt odniesienia nie może być „pierwszy z brzegu”:
    // kolejność graph.nodes() jest dowolna, a gdyby trafił się węzeł odizolowany, za problem uznalibyśmy resztę grafu.
    private static String problemNodeNames(RoadGraph graph) {
        Set<Long> ok = Set.of();
        for (GraphNode node : graph.nodes()) {
            Set<Long> component = new HashSet<>(graph.reachableFrom(node.id()));
            component.retainAll(graph.canReach(node.id()));
            if (component.size() > ok.size()) {
                ok = component;
            }
        }
        Set<Long> largestComponent = ok;
        return graph.nodes().stream()
                .filter(node -> !largestComponent.contains(node.id()))
                .map(GraphNode::name)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static GraphNode toGraphNode(RoadNode node) {
        return new GraphNode(node.getId(), node.getName(), node.getLat(), node.getLon());
    }

    private static GraphEdge toGraphEdge(RoadEdge edge) {
        return new GraphEdge(
                edge.getId(),
                edge.getFromNode().getId(),
                edge.getToNode().getId(),
                edge.getDistanceKm(),
                edge.getRoadType(),
                edge.getSurfaceQuality(),
                edge.getTerrain(),
                edge.getMaxWeightT(),
                edge.getMaxHeightM(),
                edge.getDelayFactor()
        );
    }
}
