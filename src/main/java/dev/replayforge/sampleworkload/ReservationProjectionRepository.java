package dev.replayforge.sampleworkload;

import dev.replayforge.domain.workflow.ReservationProjection;
import dev.replayforge.domain.workflow.ReservationProjection.PayoutStatus;
import dev.replayforge.domain.workflow.ReservationProjection.RefundStatus;
import dev.replayforge.domain.workflow.ReservationProjection.Status;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationProjectionRepository {
    private final JdbcTemplate jdbc;
    public ReservationProjectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<ReservationProjection> find(UUID id) {
        return jdbc.query("select * from reservation_projection where reservation_id = ?", this::map, id).stream().findFirst();
    }

    public void create(UUID id, UUID correlationId, long amount, String currency, boolean autoPayout, Instant at) {
        jdbc.update("""
            insert into reservation_projection(reservation_id,correlation_id,status,deposit_amount,currency,auto_payout,last_sequence_number,updated_at)
            values (?,?,'CREATED',?,?,?,1,?)
            """, id, correlationId, amount, currency, autoPayout, Timestamp.from(at));
    }

    public boolean apply(UUID id, long expectedPreviousSequence, long newSequence, String assignments, Object... values) {
        Object[] args = new Object[values.length + 3];
        System.arraycopy(values, 0, args, 0, values.length);
        args[values.length] = newSequence;
        args[values.length + 1] = id;
        args[values.length + 2] = expectedPreviousSequence;
        return jdbc.update("update reservation_projection set " + assignments
                + ", last_sequence_number = ?, updated_at = now() where reservation_id = ? and last_sequence_number = ?", args) == 1;
    }

    public boolean claim(String consumer, UUID eventId) {
        return jdbc.update("insert into consumer_receipts(consumer_name,event_id) values (?,?) on conflict do nothing",
                consumer, eventId) == 1;
    }

    private ReservationProjection map(ResultSet rs, int row) throws SQLException {
        return new ReservationProjection(rs.getObject("reservation_id", UUID.class), rs.getObject("correlation_id", UUID.class),
                Status.valueOf(rs.getString("status")), rs.getLong("deposit_amount"), rs.getString("currency"), rs.getBoolean("auto_payout"),
                rs.getBoolean("deposit_authorized"), RefundStatus.valueOf(rs.getString("refund_status")),
                PayoutStatus.valueOf(rs.getString("payout_status")), rs.getLong("last_sequence_number"),
                rs.getTimestamp("updated_at").toInstant());
    }
}
