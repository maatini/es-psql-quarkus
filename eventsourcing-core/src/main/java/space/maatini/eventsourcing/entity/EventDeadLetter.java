package space.maatini.eventsourcing.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "events_dead_letter")
public class EventDeadLetter extends PanacheEntityBase {

    @Id
    @Column(name = "event_id")
    public String eventId;

    public String type;
    
    public String subject;
    
    public String reason;
    
    @Column(name = "error_message", length = 4096)
    public String errorMessage;
    
    @Column(name = "retry_count")
    public Integer retryCount;
}
