package io.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.example.application.ParticipantSlotEntity.Event.Booked;
import io.example.application.ParticipantSlotEntity.Event.Canceled;
import io.example.application.ParticipantSlotEntity.Event.MarkedAvailable;
import io.example.application.ParticipantSlotEntity.Event.UnmarkedAvailable;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "view-participant-slots")
public class ParticipantSlotsView extends View {

    private static Logger logger = LoggerFactory.getLogger(ParticipantSlotsView.class);

    @Consume.FromEventSourcedEntity(ParticipantSlotEntity.class)
    public static class ParticipantSlotsViewUpdater extends TableUpdater<SlotRow> {

        public Effect<SlotRow> onEvent(ParticipantSlotEntity.Event event) {
            // Supply your own implementation
            return switch (event) {
                case MarkedAvailable e -> {
                    var newRow = new SlotRow(
                            e.slotId(), e.participantId(), e.participantType().toString(), "not booked", "available", Instant.now());
                    yield effects().updateRow(newRow);
                }
                case UnmarkedAvailable e -> {
                    var newRow = new SlotRow(
                            e.slotId(), e.participantId(), e.participantType().toString(), "not booked", "unavailable", Instant.now());
                    yield effects().updateRow(newRow);
                }
                case Booked e -> {
                    var newRow = new SlotRow(
                            e.slotId(), e.participantId(), e.participantType().toString(), e.bookingId(), "booked", Instant.now());
                    yield effects().updateRow(newRow);
                }
                case Canceled e -> {
                    var newRow = new SlotRow(
                            e.slotId(), e.participantId(), e.participantType().toString(), e.bookingId() + " canceled", "available", Instant.now());
                    yield effects().updateRow(newRow);
                }
            };
        }
    }

    public record SlotRow(
            String slotId,
            String participantId,
            String participantType,
            String bookingId,
            String status,
            Instant lastUpdated) {
    }

    public record ParticipantStatusInput(String participantId, String status) {
    }

    public record SlotList(List<SlotRow> slots) {
    }

     @Query("SELECT * AS slots FROM view_participant_slots WHERE participantId = :participantId")
    public QueryEffect<SlotList> getSlotsByParticipant(String participantId) {
        return queryResult();
    }

     @Query("SELECT * AS slots FROM view_participant_slots WHERE participantId = :participantId AND status = :status ORDER BY lastUpdated DESC")
    public QueryEffect<SlotList> getSlotsByParticipantAndStatus(ParticipantStatusInput input) {
        return queryResult();
    }
}
