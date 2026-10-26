package br.com.compartilhamentodiscord.service;

import static org.junit.jupiter.api.Assertions.*;

import br.com.compartilhamentodiscord.integration.livekit.*;
import br.com.compartilhamentodiscord.security.CaptureProofAccessPolicy;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CaptureProofServiceTest {
    private static final String PRESENTER = "test-presenter-capability-with-32-characters";
    private static final String VIEWER = "test-viewer-capability-with-32-characters";

    @Test
    void rolesAreEnforcedInServiceAndViewerIdentityIsStable() {
        FakeMedia media = new FakeMedia();
        CaptureProofService service = service(media);
        assertThrows(CaptureProofException.class, () -> service.create(VIEWER));
        assertThrows(CaptureProofException.class, () -> service.viewer(PRESENTER));
        assertThrows(CaptureProofException.class, () -> service.status("invalid"));
        assertNull(media.room);
        var presenter = service.create(PRESENTER);
        assertEquals(MediaRole.PRESENTER, presenter.role());
        var first = service.viewer(VIEWER);
        var second = service.viewer(VIEWER);
        assertEquals(MediaRole.VIEWER, first.role());
        assertEquals(first.identity(), second.identity());
        assertEquals(presenter.roomName(), first.roomName());
        assertNotEquals(presenter.identity(), first.identity());
        assertFalse(presenter.toString().contains(presenter.token()));
        assertThrows(CaptureProofException.class, () -> service.delete(VIEWER));
        service.delete(PRESENTER);
        service.delete(PRESENTER);
        assertFalse(service.status(VIEWER).exists());
        assertThrows(CaptureProofException.class, () -> service.viewer(VIEWER));
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
        assertThrows(CaptureProofException.class, () -> service.viewer(VIEWER));
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
        int creates;
        CountDownLatch createEntered;
        CountDownLatch releaseCreate;
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
        public MediaAccessToken token(String name, String identity, MediaRole role) { return new MediaAccessToken("fake-jwt", 120); }
        public void delete(String name) {
            if (failDelete) throw new IllegalStateException("simulated delete failure");
            if (name.equals(room)) room = null;
        }
    }
}
