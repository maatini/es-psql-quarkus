package space.maatini.eventsourcing.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import space.maatini.eventsourcing.entity.AggregateRoot;
import space.maatini.eventsourcing.entity.JsonAggregate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class EventHandlerRegistry {
    private final Map<String, List<EventHandler>> handlerRegistry = new HashMap<>();
    private final Set<Class<? extends AggregateRoot>> aggregateClasses = new HashSet<>();

    @Inject
    public EventHandlerRegistry(
            Instance<AggregateEventHandler<?>> handlerInstances,
            Instance<JsonAggregateHandler> jsonHandlerInstances) {

        // Standard Aggregate Event Handlers
        handlerInstances.handles().forEach(handle -> {
            AggregateEventHandler<?> handler = handle.get();
            Class<?> beanClass = handle.getBean().getBeanClass();
            HandlesEvents annotation = beanClass.getAnnotation(HandlesEvents.class);
            if (annotation != null) {
                String prefix = annotation.value();
                handlerRegistry.computeIfAbsent(prefix, k -> new ArrayList<>()).add(handler);
                aggregateClasses.add(annotation.aggregate());
            }
        });

        // Generic JSON Aggregate Handlers (Stage 2)
        jsonHandlerInstances.handles().forEach(handle -> {
            JsonAggregateHandler handler = handle.get();
            Class<?> beanClass = handle.getBean().getBeanClass();
            HandlesEvents annotation = beanClass.getAnnotation(HandlesEvents.class);
            if (annotation != null) {
                String type = annotation.aggregateType();
                if (type.isEmpty()) type = handler.getAggregateType();

                String prefix = annotation.value();
                if (prefix.isEmpty()) prefix = "space.maatini." + type + ".";

                handlerRegistry.computeIfAbsent(prefix, k -> new ArrayList<>()).add(handler);
                aggregateClasses.add(JsonAggregate.class);
            }
        });
    }

    public Map<String, List<EventHandler>> getHandlers() {
        return handlerRegistry;
    }

    public Set<Class<? extends AggregateRoot>> getAggregateClasses() {
        return aggregateClasses;
    }

    public int size() {
        return handlerRegistry.size();
    }
}
