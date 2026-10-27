package br.com.compartilhamentodiscord.service;

import static org.junit.jupiter.api.Assertions.*;

import br.com.compartilhamentodiscord.integration.livekit.*;
import br.com.compartilhamentodiscord.security.CaptureProofAccessPolicy;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CaptureProofServiceTest {
    private static final String PRESENTER = "test-presenter-capability-with-32-characters";
    private static final String VIEWER = "test-viewer-capability-with-32-characters";
    private static final String NONCE = "29a0b79a-74aa-4f21-973a-cb97ae71e051";

    @Test
    void validationIdentifiesRoleWithoutCreatingRoomOrContactingMedia() {
        FakeMedia media = new FakeMedia();
        CaptureProofService service = service(media);
        assertEquals(MediaRole.PRESENTER, service.validateAccess(PRESENTER).role());
        assertEquals(MediaRole.VIEWER, service.validateAccess(VIEWER).role());
        assertThrows(CaptureProofException.class, () -> service.validateAccess("invalid"));
        assertEquals(0, media.creates);
        assertNull(media.room);
    }

    @Test
    void rolesAreEnforcedInServiceAndViewerIdentityIsStable() {
        FakeMedia media = new FakeMedia();
        CaptureProofService service = service(media);
        assertThrows(CaptureProofException.class, () -> service.create(VIEWER));
        assertThrows(CaptureProofException.class, () -> service.viewer(PRESENTER, NONCE));
        assertThrows(CaptureProofException.class, () -> service.status("invalid"));
        assertNull(media.room);
        var presenter = service.create(PRESENTER);
        assertEquals(MediaRole.PRESENTER, presenter.role());
        var first = service.viewer(VIEWER, NONCE);
        var second = service.viewer(VIEWER, NONCE);
        assertEquals(MediaRole.VIEWER, first.role());
        assertEquals(first.identity(), second.identity());
        assertEquals(presenter.roomName(), first.roomName());
        assertNotEquals(presenter.identity(), first.identity());
        assertFalse(presenter.toString().contains(presenter.token()));
        assertThrows(CaptureProofException.class, () -> service.delete(VIEWER));
        service.delete(PRESENTER);
        service.delete(PRESENTER);
        assertFalse(service.status(VIEWER).exists());
        assertThrows(CaptureProofException.class, () -> service.viewer(VIEWER, NONCE));
    }

    @Test
    void liveRequiresBothCorrectSourcesFromExpectedPresenterAndUnmutedTracks() {
        FakeMedia media = new FakeMedia();
        CaptureProofService service = service(media);
        var presenter = service.create(PRESENTER);
        assertFalse(service.status(PRESENTER).live());
        media.participants = List.of(participant("unexpected", screen(false), audio(false)));
        assertFalse(service.status(PRESENTER).live());
        media.participants = List.of(participant(presenter.identity(), screen(false)));
        assertFalse(service.status(PRESENTER).live());
        media.participants = List.of(participant(presenter.identity(), screen(false), audio(true)));
        assertFalse(service.status(PRESENTER).live());
        media.participants = List.of(participant(presenter.identity(), screen(false),
                new LiveKitTrack("mic", "AUDIO", "MICROPHONE", false)));
        assertFalse(service.status(PRESENTER).live());
        // No audio amplitude/volume is inspected: a silent but present track is valid.
        media.participants = List.of(participant(presenter.identity(), screen(false), audio(false)));
        assertTrue(service.status(VIEWER).live());
        media.participants = List.of(participant(presenter.identity(), audio(false)));
        assertFalse(service.status(VIEWER).live());
    }

    @Test
    void uncertainCreateAndDeleteRemainAvailableForExplicitCleanup() {
        FakeMedia media = new FakeMedia();
        CaptureProofService service = service(media);
        media.failCreate = true;
        assertThrows(IllegalStateException.class, () -> service.create(PRESENTER));
        assertNotNull(media.room);
        assertFalse(service.status(PRESENTER).live());
        assertThrows(CaptureProofException.class, () -> service.create(PRESENTER));
        media.failDelete = true;
        assertThrows(IllegalStateException.class, () -> service.delete(PRESENTER));
        assertThrows(CaptureProofException.class, () -> service.viewer(VIEWER, NONCE));
        media.failDelete = false;
        service.delete(PRESENTER);
        assertNull(media.room);
        assertFalse(service.status(PRESENTER).exists());
        media.failCreate = false;
        service.create(PRESENTER);
        service.delete(PRESENTER);
    }

    @Test
    void roomNamesNeverRepeatAndExecutionHasAHardBound() {
        FakeMedia media = new FakeMedia();
        CaptureProofService service = service(media);
        var names = new java.util.HashSet<String>();
        for (int i = 0; i < 32; i++) {
            assertTrue(names.add(service.create(PRESENTER).roomName()));
            assertThrows(CaptureProofException.class, () -> service.create(PRESENTER));
            service.delete(PRESENTER);
        }
        assertThrows(CaptureProofException.class, () -> service.create(PRESENTER));
        assertEquals(32, names.size());
        assertNull(media.room);
    }

    @Test
    void concurrentCreateCannotAllocateTwoRoomsAndNoStateLockIsHeldDuringNetwork() throws Exception {
        FakeMedia media = new FakeMedia();
        media.createEntered = new CountDownLatch(1);
        media.releaseCreate = new CountDownLatch(1);
        CaptureProofService service = service(media);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var creating = executor.submit(() -> service.create(PRESENTER));
            assertTrue(media.createEntered.await(2, TimeUnit.SECONDS));
            var rejected = executor.submit(() -> assertThrows(CaptureProofException.class,
                    () -> service.create(PRESENTER)));
            rejected.get(2, TimeUnit.SECONDS);
            var rejectedCleanup = executor.submit(() -> assertThrows(CaptureProofException.class,
                    () -> service.delete(PRESENTER)));
            rejectedCleanup.get(2, TimeUnit.SECONDS);
            media.releaseCreate.countDown();
            creating.get(2, TimeUnit.SECONDS);
            assertEquals(1, media.creates);
            service.delete(PRESENTER);
        } finally { media.releaseCreate.countDown(); }
    }

    @Test
    void nineDistinctBackendIdentitiesIncludePresenterInTheTenParticipantLimit() {
        FakeMedia media = new FakeMedia();
        CaptureProofService service = service(media);
        var presenter = service.create(PRESENTER);
        var identities = new java.util.HashSet<String>();
        identities.add(presenter.identity());
        for (int i = 0; i < 9; i++) {
            String nonce = UUID.randomUUID().toString();
            var viewer = service.viewer(VIEWER, nonce);
            assertTrue(identities.add(viewer.identity()));
            assertNotEquals(nonce, viewer.identity());
            assertEquals(viewer.identity(), service.viewer(VIEWER, nonce).identity());
            assertEquals(presenter.roomName(), viewer.roomName());
        }
        assertEquals(10, identities.size());
        var full = assertThrows(CaptureProofException.class, () -> service.viewer(VIEWER, NONCE));
        assertEquals(409, full.status().value());
        assertEquals("CAPTURE_PROOF_CAPACITY", full.code());
        assertEquals(9, service.status(PRESENTER).viewerSlotsUsed());
        assertEquals(0, service.status(PRESENTER).viewerCount());
        assertEquals(9, service.status(PRESENTER).maxViewers());
    }

    @Test
    void concurrentLastSlotHasExactlyOneWinnerAndRepeatedNonceNeverCreatesMoreIdentities() throws Exception {
        FakeMedia media = new FakeMedia();
        CaptureProofService service = service(media);
        service.create(PRESENTER);
        for (int i = 0; i < 8; i++) service.viewer(VIEWER, UUID.randomUUID().toString());
        var start = new CountDownLatch(1);
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        var rejected = new java.util.concurrent.atomic.AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 12).mapToObj(i -> executor.submit(() -> {
                start.await();
                try { service.viewer(VIEWER, UUID.randomUUID().toString()); successes.incrementAndGet(); }
                catch (CaptureProofException exception) {
                    assertEquals("CAPTURE_PROOF_CAPACITY", exception.code()); rejected.incrementAndGet();
                }
                return null;
            })).toList();
            start.countDown();
            for (var task : tasks) task.get(3, TimeUnit.SECONDS);
        }
        assertEquals(1, successes.get());
        assertEquals(11, rejected.get());
        service.delete(PRESENTER);
        service.create(PRESENTER);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 12)
                    .mapToObj(i -> executor.submit(() -> service.viewer(VIEWER, NONCE).identity())).toList();
            var identities = new java.util.HashSet<String>();
            for (var task : tasks) identities.add(task.get(3, TimeUnit.SECONDS));
            assertEquals(1, identities.size());
        }
        assertEquals(1, service.status(PRESENTER).viewerSlotsUsed());
    }

    @Test
    void leavingDisconnectsOnlyOwnIdentityAndNeverReassignsAStillValidJwtSlot() {
        FakeMedia media = new FakeMedia();
        CaptureProofService service = service(media);
        var presenter = service.create(PRESENTER);
        var viewer = service.viewer(VIEWER, NONCE);
        for (int i = 0; i < 8; i++) service.viewer(VIEWER, UUID.randomUUID().toString());
        media.participants = List.of(participant(presenter.identity(), screen(false), audio(false)),
                participant(viewer.identity()), participant(viewer.identity()), participant("unissued-viewer"));
        assertEquals(1, service.status(PRESENTER).viewerCount());
        assertThrows(CaptureProofException.class, () -> service.leave(PRESENTER, NONCE));
        service.leave(VIEWER, UUID.randomUUID().toString());
        assertNull(media.removedIdentity);
        service.leave(VIEWER, NONCE);
        assertEquals(viewer.identity(), media.removedIdentity);
        assertEquals(0, service.status(VIEWER).viewerCount());
        service.leave(VIEWER, NONCE);
        assertEquals(9, service.status(VIEWER).viewerSlotsUsed());
        assertEquals(viewer.identity(), service.viewer(VIEWER, NONCE).identity());
        assertEquals("CAPTURE_PROOF_CAPACITY", assertThrows(CaptureProofException.class,
                () -> service.viewer(VIEWER, UUID.randomUUID().toString())).code());
        service.delete(PRESENTER);
        service.leave(VIEWER, NONCE);
        var nextPresenter = service.create(PRESENTER);
        var nextViewer = service.viewer(VIEWER, NONCE);
        assertNotEquals(presenter.roomName(), nextPresenter.roomName());
        assertNotEquals(viewer.identity(), nextViewer.identity());
    }

    @Test
    void invalidNonceAndFailedIssuanceCannotConsumeUnboundedSlotsAndFailuresAreRetryable() {
        FakeMedia media = new FakeMedia();
        CaptureProofService service = service(media);
        service.create(PRESENTER);
        for (String invalid : new String[]{"", "arbitrary-identity", "29a0b79a-74aa-1f21-973a-cb97ae71e051"}) {
            assertEquals(400, assertThrows(CaptureProofException.class,
                    () -> service.viewer(VIEWER, invalid)).status().value());
        }
        assertThrows(CaptureProofException.class, () -> service.viewer(VIEWER, null));
        assertEquals(0, service.status(VIEWER).viewerSlotsUsed());
        media.failToken = true;
        assertThrows(IllegalStateException.class, () -> service.viewer(VIEWER, NONCE));
        String attemptedIdentity = media.tokenIdentity;
        assertEquals(1, service.status(VIEWER).viewerSlotsUsed());
        media.failToken = false;
        assertEquals(attemptedIdentity, service.viewer(VIEWER, NONCE).identity());
        media.failRemove = true;
        assertThrows(IllegalStateException.class, () -> service.leave(VIEWER, NONCE));
        media.failRemove = false;
        service.leave(VIEWER, NONCE);
        assertEquals(attemptedIdentity, media.removedIdentity);
    }

    @Test
    void deletionDuringViewerIssuanceRejectsLateCredentialWithoutHoldingStateLock() throws Exception {
        FakeMedia media = new FakeMedia();
        CaptureProofService service = service(media);
        service.create(PRESENTER);
        media.tokenEntered = new CountDownLatch(1);
        media.releaseToken = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var issuing = executor.submit(() -> assertThrows(CaptureProofException.class,
                    () -> service.viewer(VIEWER, NONCE)));
            assertTrue(media.tokenEntered.await(2, TimeUnit.SECONDS));
            executor.submit(() -> service.delete(PRESENTER)).get(2, TimeUnit.SECONDS);
            media.releaseToken.countDown();
            issuing.get(2, TimeUnit.SECONDS);
        } finally { media.releaseToken.countDown(); }
        assertFalse(service.status(PRESENTER).exists());
    }

    @Test
    void generationExpiryClosesIssuanceAndRetriesUncertainDeletionBeforeReleasingState() {
        FakeMedia media = new FakeMedia();
        MutableClock clock = new MutableClock();
        CaptureProofService service = new CaptureProofService(media, new CaptureProofAccessPolicy(PRESENTER, VIEWER),
                URI.create("wss://192.0.2.1:4443"), clock);
        var first = service.create(PRESENTER);
        service.viewer(VIEWER, NONCE);
        service.expire();
        assertNotNull(media.room);
        clock.now = clock.now.plus(CaptureProofService.GENERATION_LIFETIME);
        assertThrows(CaptureProofException.class, () -> service.viewer(VIEWER, NONCE));
        media.failDelete = true;
        service.expire();
        assertNotNull(media.room);
        assertFalse(service.status(PRESENTER).live());
        assertThrows(CaptureProofException.class, () -> service.create(PRESENTER));
        media.failDelete = false;
        service.expire();
        assertNull(media.room);
        assertEquals(0, service.status(PRESENTER).viewerSlotsUsed());
        assertNotEquals(first.roomName(), service.create(PRESENTER).roomName());
        service.shutdown();
        assertNull(media.room);
    }

    private static CaptureProofService service(FakeMedia media) {
        return new CaptureProofService(media, new CaptureProofAccessPolicy(PRESENTER, VIEWER),
                URI.create("wss://192.0.2.1:4443"));
    }
    private static LiveKitTrack screen(boolean muted) { return new LiveKitTrack("video", "VIDEO", "SCREEN_SHARE", muted); }
    private static LiveKitTrack audio(boolean muted) { return new LiveKitTrack("audio", "AUDIO", "SCREEN_SHARE_AUDIO", muted); }
    private static LiveKitParticipant participant(String identity, LiveKitTrack... tracks) {
        return new LiveKitParticipant(identity, "sid", List.of(tracks));
    }

    private static final class FakeMedia implements CaptureProofMedia {
        volatile String room;
        volatile List<LiveKitParticipant> participants = List.of();
        boolean failCreate;
        boolean failDelete;
        boolean failToken;
        boolean failRemove;
        String tokenIdentity;
        String removedIdentity;
        int creates;
        CountDownLatch createEntered;
        CountDownLatch releaseCreate;
        CountDownLatch tokenEntered;
        CountDownLatch releaseToken;
        public void create(String name) {
            creates++;
            room = name;
            if (createEntered != null) {
                createEntered.countDown();
                try { if (!releaseCreate.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(); }
            }
            if (failCreate) throw new IllegalStateException("simulated uncertain creation");
        }
        public Optional<LiveKitRoom> find(String name) {
            return name.equals(room) ? Optional.of(new LiveKitRoom(name, "sid", 10, participants.size())) : Optional.empty();
        }
        public List<LiveKitParticipant> participants(String name) { return participants; }
        public MediaAccessToken token(String name, String identity, MediaRole role) {
            tokenIdentity = identity;
            if (tokenEntered != null) {
                tokenEntered.countDown();
                try { if (!releaseToken.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(); }
            }
            if (failToken) throw new IllegalStateException("simulated token failure");
            return new MediaAccessToken("fake-jwt", 120);
        }
        public void removeViewer(String name, String identity) {
            if (failRemove) throw new IllegalStateException("simulated remove failure");
            removedIdentity = identity;
            participants = participants.stream().filter(participant -> !identity.equals(participant.identity())).toList();
        }
        public void delete(String name) {
            if (failDelete) throw new IllegalStateException("simulated delete failure");
            if (name.equals(room)) room = null;
        }
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-27T12:00:00Z");
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
