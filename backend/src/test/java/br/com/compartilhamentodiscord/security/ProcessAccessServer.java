package br.com.compartilhamentodiscord.security;

import br.com.compartilhamentodiscord.service.access.AccessStore;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.UUID;
import testsupport.AllowedAccessVerification;

/** Child JVM fixture, launched only by AccessProcessRestartHttpTest; no test endpoint. */
public final class ProcessAccessServer {
    public static void main(String[] args) throws Exception {
        var privatePipe = new DataOutputStream(System.out);
        // Never let application startup or errors contaminate the private IPC or test logs.
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        try (var server = new HttpSecurityServer(new Class<?>[]{AllowedAccessVerification.class})) {
            var grant = new AccessStore.Grant(UUID.randomUUID(), UUID.randomUUID(), "123456", "234567",
                    "Participante", AccessStore.Role.OWNER);
            String first = server.bean(AccessStore.class).issue(grant);
            String unused = server.bean(AccessStore.class).issue(grant);
            privatePipe.writeInt(server.port);
            privatePipe.writeUTF(first);
            privatePipe.writeUTF(unused);
            privatePipe.flush();
            System.in.read();
        } catch (Throwable failure) {
            privatePipe.writeInt(0);
            privatePipe.flush();
            System.exit(1);
        }
    }
}
