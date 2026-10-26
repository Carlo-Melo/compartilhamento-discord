package br.com.compartilhamentodiscord.security;

import br.com.compartilhamentodiscord.integration.livekit.MediaRole;
import br.com.compartilhamentodiscord.service.CaptureProofException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;

/** Experimental operator capabilities, unrelated to the future bot ticket flow. */
public final class CaptureProofAccessPolicy {
    private final byte[] presenter;
    private final byte[] viewer;

    public CaptureProofAccessPolicy(String presenter, String viewer) {
        if (presenter == null || viewer == null || !presenter.matches("[A-Za-z0-9_-]{32,128}")
                || !viewer.matches("[A-Za-z0-9_-]{32,128}") || presenter.equals(viewer)) {
            throw new IllegalStateException("Defina duas chaves experimentais distintas, aleatórias e com 32 a 128 caracteres.");
        }
        this.presenter = presenter.getBytes(StandardCharsets.US_ASCII);
        this.viewer = viewer.getBytes(StandardCharsets.US_ASCII);
    }

    public MediaRole authenticate(String key) {
        byte[] candidate = key == null || key.length() > 128 ? new byte[0] : key.getBytes(StandardCharsets.US_ASCII);
        boolean isPresenter = MessageDigest.isEqual(presenter, candidate);
        boolean isViewer = MessageDigest.isEqual(viewer, candidate);
        if (isPresenter) return MediaRole.PRESENTER;
        if (isViewer) return MediaRole.VIEWER;
        throw new CaptureProofException(HttpStatus.FORBIDDEN, "Acesso experimental inválido.");
    }

    public void require(String key, MediaRole required) {
        if (authenticate(key) != required) {
            throw new CaptureProofException(HttpStatus.FORBIDDEN, "Este papel não permite a operação experimental.");
        }
    }
}
