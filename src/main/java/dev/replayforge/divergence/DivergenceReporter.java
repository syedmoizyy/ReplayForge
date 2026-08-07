package dev.replayforge.divergence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.replayforge.divergence.DivergenceReport.FieldDifference;
import dev.replayforge.divergence.DivergenceReport.TransitionDifference;
import dev.replayforge.replay.ReplayState;
import dev.replayforge.invariants.InvariantViolation;
import java.util.*;

public final class DivergenceReporter {
    private final ObjectMapper mapper;
    public DivergenceReporter(ObjectMapper mapper) {
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public DivergenceReport compare(List<TraceTransition> baseline, List<TraceTransition> replay,
            ReplayState baselineFinal, ReplayState replayFinal) {
        return compare(baseline, replay, baselineFinal, replayFinal, List.of());
    }

    public DivergenceReport compare(List<TraceTransition> baseline, List<TraceTransition> replay,
            ReplayState baselineFinal, ReplayState replayFinal, List<InvariantViolation> violations) {
        List<TransitionDifference> differences = new ArrayList<>();
        int size = Math.max(baseline.size(), replay.size());
        for (int index = 0; index < size; index++) {
            long order = index + 1L;
            if (index >= baseline.size()) { differences.add(new TransitionDifference(order, "event-order", "missingBaseline", null, replay.get(index).eventId())); continue; }
            if (index >= replay.size()) { differences.add(new TransitionDifference(order, "event-order", "missingReplay", baseline.get(index).eventId(), null)); continue; }
            TraceTransition left = baseline.get(index), right = replay.get(index);
            diff(differences, order, "event-order", "eventId", left.eventId(), right.eventId());
            diff(differences, order, "event-type", "eventType", left.eventType(), right.eventType());
            payloadDiff(differences, order, "", left.payload(), right.payload());
            diff(differences, order, "side-effects", "sideEffects", left.sideEffects(), right.sideEffects());
        }
        Map<String, FieldDifference> state = stateDiff(baselineFinal, replayFinal);
        Integer first = differences.isEmpty() ? null : Math.toIntExact(differences.getFirst().order());
        return new DivergenceReport("1", first, differences, state, violations);
    }

    public String json(DivergenceReport report) {
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Cannot serialize divergence report", error); }
    }

    public String markdown(DivergenceReport report) {
        StringBuilder text = new StringBuilder("# Replay divergence report\n\n");
        text.append(report.firstDivergentOrder() == null ? "No transition divergence detected.\n" :
                "First divergent event order: **" + report.firstDivergentOrder() + "**.\n");
        if (!report.transitionDifferences().isEmpty()) {
            text.append("\n## Trace differences\n\n| Order | Category | Field | Baseline | Replay |\n|---:|---|---|---|---|\n");
            report.transitionDifferences().forEach(d -> text.append('|').append(d.order()).append('|').append(d.category())
                    .append('|').append(d.field()).append('|').append(cell(d.baseline())).append('|').append(cell(d.replay())).append("|\n"));
        }
        text.append("\n## Final state\n\n");
        if (report.finalStateDifferences().isEmpty()) text.append("No final aggregate state differences.\n");
        else report.finalStateDifferences().forEach((field, value) -> text.append("- `").append(field).append("`: `")
                .append(value.baseline()).append("` → `").append(value.replay()).append("`\n"));
        text.append("\n## Invariant violations\n\n");
        if (report.invariantViolations().isEmpty()) text.append("No invariant violations.\n");
        else report.invariantViolations().forEach(v -> text.append("- **").append(v.severity()).append("** `")
                .append(v.ruleId()).append("` at event ").append(v.eventPosition()).append(": expected ")
                .append(v.expectedCondition()).append("; actual ").append(v.actualCondition()).append(".\n"));
        return text.toString();
    }

    private void payloadDiff(List<TransitionDifference> out, long order, String path,
            com.fasterxml.jackson.databind.JsonNode left, com.fasterxml.jackson.databind.JsonNode right) {
        if (Objects.equals(left, right)) return;
        if (left != null && right != null && left.isObject() && right.isObject()) {
            SortedSet<String> fields = new TreeSet<>(); left.fieldNames().forEachRemaining(fields::add); right.fieldNames().forEachRemaining(fields::add);
            fields.forEach(field -> payloadDiff(out, order, path + "/" + field, left.get(field), right.get(field)));
        } else out.add(new TransitionDifference(order, "payload", path.isEmpty() ? "/" : path, left, right));
    }
    private void diff(List<TransitionDifference> out, long order, String category, String field, Object left, Object right) {
        if (!Objects.equals(left, right)) out.add(new TransitionDifference(order, category, field, left, right));
    }
    private Map<String, FieldDifference> stateDiff(ReplayState left, ReplayState right) {
        Map<String, FieldDifference> result = new TreeMap<>();
        diffState(result, "reservationStatus", left.reservationStatus(), right.reservationStatus());
        diffState(result, "depositAmount", left.depositAmount(), right.depositAmount());
        diffState(result, "currency", left.currency(), right.currency());
        diffState(result, "depositAuthorized", left.depositAuthorized(), right.depositAuthorized());
        diffState(result, "refundStatus", left.refundStatus(), right.refundStatus());
        diffState(result, "payoutStatus", left.payoutStatus(), right.payoutStatus());
        diffState(result, "lastSequenceNumber", left.lastSequenceNumber(), right.lastSequenceNumber()); return result;
    }
    private void diffState(Map<String, FieldDifference> out, String field, Object left, Object right) {
        if (!Objects.equals(left, right)) out.put(field, new FieldDifference(left, right));
    }
    private String cell(Object value) { return String.valueOf(value).replace("|", "\\|").replace("\n", " "); }
}
