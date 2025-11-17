package io.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "booking-slot")
public class BookingSlotEntity extends EventSourcedEntity<Timeslot, BookingEvent> {

    private final String entityId;
    private static final Logger logger = LoggerFactory.getLogger(BookingSlotEntity.class);

    public BookingSlotEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    public Effect<Done> markSlotAvailable(Command.MarkSlotAvailable cmd) {
        if (currentState() == null)
            return effects().error("not yet implemented");

        var reserved = new BookingEvent.ParticipantMarkedAvailable(entityId, cmd.participant.id(), cmd.participant.participantType());

        return effects()
                .persist(reserved)
                .thenReply(__-> Done.getInstance());
    }

    public Effect<Done> unmarkSlotAvailable(Command.UnmarkSlotAvailable cmd) {
        if (currentState() == null)
            return effects().error("not yet implemented");

        var unreserved = new BookingEvent.ParticipantUnmarkedAvailable(entityId, cmd.participant.id(), cmd.participant.participantType());

        return effects()
                .persist(unreserved)
                .thenReply(__-> Done.getInstance());
    }

    // NOTE: booking a slot should produce 3
    // `ParticipantBooked` events
    public Effect<Done> bookSlot(Command.BookReservation cmd) {

        boolean isBookable = currentState().isBookable(cmd.studentId,  cmd.aircraftId, cmd.instructorId);
        var studentBooking = new BookingEvent.ParticipantBooked(
                entityId, cmd.studentId, Participant.ParticipantType.STUDENT, cmd.bookingId);
        var aircraftBooking = new BookingEvent.ParticipantBooked(
                entityId, cmd.aircraftId, Participant.ParticipantType.AIRCRAFT, cmd.bookingId);
        var instructorBooking = new BookingEvent.ParticipantBooked(
                entityId, cmd.instructorId, Participant.ParticipantType.INSTRUCTOR, cmd.bookingId);

        if (isBookable)
            return effects()
                    .persistAll(List.of(studentBooking, aircraftBooking, instructorBooking))
                    .thenReply(__-> Done.getInstance());
        else return effects().error("Timeslot is not bookable");

    }

    // NOTE: canceling a booking should produce 3
    // `ParticipantCanceled` events
    public Effect<Done> cancelBooking(String bookingId) {
        List<Timeslot.Booking> bookings = currentState().findBooking(bookingId);
        List<BookingEvent> events = new ArrayList<>();

        bookings.forEach(booking -> {
            BookingEvent.ParticipantCanceled canceled = new BookingEvent.ParticipantCanceled(
                    entityId, booking.participant().id(), booking.participant().participantType(), bookingId);
            events.add(canceled);
        });
        return effects().persistAll(events).thenReply(__-> Done.done());
    }

    public ReadOnlyEffect<Timeslot> getSlot() {
        return effects().reply(currentState());
    }

    @Override
    public Timeslot emptyState() {
        return new Timeslot(
                // NOTE: these are just estimates for capacity based on it being a sample
                HashSet.newHashSet(10), HashSet.newHashSet(10));
    }

    @Override
    public Timeslot applyEvent(BookingEvent event) {
        // Supply your own implementation to update state based
        // on the event
        switch (event) {
            case BookingEvent.ParticipantMarkedAvailable e -> currentState().reserve(e);
            case BookingEvent.ParticipantUnmarkedAvailable e -> currentState().unreserve(e);
            case BookingEvent.ParticipantBooked e -> currentState()
                    .book(e)
                    .unreserve(new BookingEvent.ParticipantUnmarkedAvailable(
                            e.slotId(), e.participantId(), e.participantType()));
            case BookingEvent.ParticipantCanceled e -> currentState().cancelBooking(e.bookingId());
        }
        return currentState();
    }

    public sealed interface Command {
        record MarkSlotAvailable(Participant participant) implements Command {
        }

        record UnmarkSlotAvailable(Participant participant) implements Command {
        }

        record BookReservation(
                String studentId, String aircraftId, String instructorId, String bookingId)
                implements Command {
        }
    }
}
