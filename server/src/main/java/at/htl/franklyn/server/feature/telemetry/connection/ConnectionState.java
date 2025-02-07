package at.htl.franklyn.server.feature.telemetry.connection;

import at.htl.franklyn.server.feature.telemetry.participation.Participation;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDateTime;

@Entity
@Table(name = "F_CONNECTION_STATE", indexes = {
        @Index(name = "cs_timestamp_idx", columnList = "cs_ping_timestamp")
})
@SqlResultSetMappings({
        @SqlResultSetMapping(
                name = "timeoutMapping",
                columns=  {
                        @ColumnResult(name = "PAR_ID_RESULT")
                }
        )
})
@NamedNativeQueries({
        @NamedNativeQuery(
            name = "ConnectionState.getTimedoutParticipants",
            query = """
                    with latest_connection as (
                        select distinct on (cs_participation_id)
                            cs_participation_id, cs_is_connected, cs_ping_timestamp
                        from f_connection_state
                        order by cs_participation_id, cs_ping_timestamp desc
                    )
                    select p.p_id::text as par_id_result
                    from f_participation p
                             join latest_connection cs on p.p_id = cs.cs_participation_id
                             join f_exam e on p.p_exam = e.e_id
                    where cs.cs_is_connected = true
                        and e.e_state = 1 -- exam state: ongoing
                      and extract(epoch from (now() - cs.cs_ping_timestamp)) >= ?1 
                    """,
            resultClass = String.class,
            resultSetMapping = "timeoutMapping"
        )
})
public class ConnectionState {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "CS_ID")
    private Long id;

    @NotNull(message = "Ping timestamp can not be null")
    @PastOrPresent(message = "Can not persist ping captured in the future")
    @Column(name = "CS_PING_TIMESTAMP", nullable = false)
    private LocalDateTime pingTimestamp;

    @NotNull(message = "Participation can not be null")
    @ManyToOne(optional = false, fetch = FetchType.LAZY, cascade = {
            CascadeType.DETACH,
            CascadeType.MERGE,
            CascadeType.PERSIST,
            CascadeType.REFRESH
    })
    @JoinColumn(name = "CS_PARTICIPATION_ID", nullable = false)
    private Participation participation;

    @Column(name = "CS_IS_CONNECTED", nullable = false)
    private boolean isConnected;

    public ConnectionState() {
    }

    public ConnectionState(LocalDateTime pingTimestamp, Participation participation, boolean isConnected) {
        this.pingTimestamp = pingTimestamp;
        this.participation = participation;
        this.isConnected = isConnected;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getPingTimestamp() {
        return pingTimestamp;
    }

    public void setPingTimestamp(LocalDateTime pingTimestamp) {
        this.pingTimestamp = pingTimestamp;
    }

    public Participation getParticipation() {
        return participation;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    @Override
    public String toString() {
        return "ConnectionState{" +
                "id=" + id +
                ", pingTimestamp=" + pingTimestamp +
                ", participation=" + participation +
                ", isConnected=" + isConnected +
                '}';
    }
}
