package dev.mosiur.payledger.notification.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.mosiur.payledger.notification.notification.domain.Notification;
import dev.mosiur.payledger.notification.notification.domain.NotificationKind;
import dev.mosiur.payledger.notification.notification.model.TransactionPostedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationComposerTest {

    private static final Instant NOW = Instant.parse("2026-11-12T08:00:00Z");
    private static final UUID EVENT = UUID.fromString("01a1cb6a-0000-7000-8000-00000000e001");
    private static final UUID TX = UUID.fromString("01a1cb6a-0000-7000-8000-00000000a001");
    private static final UUID FROM = UUID.fromString("01a1cb6a-945d-762f-8534-2dd33110ff95");
    private static final UUID TO = UUID.fromString("01a1cb6a-94b3-789d-8eac-9ac7e4137bd3");

    @Test
    void aTransferNotifiesTheSenderAndTheReceiver() {
        var notifications = NotificationComposer.compose(EVENT, event("TRANSFER", "ayesha", "rahim", 40_000, "Rent"), NOW);

        assertThat(notifications)
                .extracting(Notification::getRecipientId, Notification::getKind, Notification::getMessage)
                .containsExactly(
                        tuple("ayesha", NotificationKind.MONEY_SENT,
                                "You sent 400.00 BDT from account …ff95 to account …7bd3. Reference: Rent."),
                        tuple("rahim", NotificationKind.MONEY_RECEIVED,
                                "You received 400.00 BDT in account …7bd3. Reference: Rent."));
        assertThat(notifications).allSatisfy(n -> {
            assertThat(n.getEventId()).isEqualTo(EVENT);
            assertThat(n.getTransactionId()).isEqualTo(TX);
            assertThat(n.getCreatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void aDepositNotifiesOnlyTheCustomerNotTheSystemAccount() {
        var notifications = NotificationComposer.compose(EVENT, event("DEPOSIT", "system", "ayesha", 105, null), NOW);

        assertThat(notifications)
                .extracting(Notification::getRecipientId, Notification::getKind, Notification::getMessage)
                .containsExactly(tuple("ayesha", NotificationKind.DEPOSIT_RECEIVED,
                        "1.05 BDT was deposited into account …7bd3."));
    }

    @Test
    void aTransferBetweenOwnAccountsSendsBothMessagesToTheSamePerson() {
        var notifications = NotificationComposer.compose(EVENT, event("TRANSFER", "ayesha", "ayesha", 1, " "), NOW);

        assertThat(notifications).extracting(Notification::getRecipientId, Notification::getKind)
                .containsExactly(
                        tuple("ayesha", NotificationKind.MONEY_SENT),
                        tuple("ayesha", NotificationKind.MONEY_RECEIVED));
        assertThat(notifications.getFirst().getMessage()).isEqualTo("You sent 0.01 BDT from account …ff95 to account …7bd3.");
    }

    private static TransactionPostedEvent event(String type, String from, String to, long amount, String reference) {
        return new TransactionPostedEvent(TX, type, FROM, from, TO, to, amount, "BDT", reference, NOW);
    }
}
